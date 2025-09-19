package com.michaldrabik.ui_progress.history.usecases

import com.michaldrabik.common.Config.DEFAULT_LANGUAGE
import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.common.extensions.dateFromMillis
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.common.extensions.toLocalZone
import com.michaldrabik.common.extensions.toMillis
import com.michaldrabik.common.extensions.toUtcZone
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.Episode
import com.michaldrabik.data_local.database.model.Season
import com.michaldrabik.repository.TranslationsRepository
import com.michaldrabik.repository.images.ShowImagesProvider
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.repository.shows.ShowsRepository
import com.michaldrabik.ui_base.dates.DateFormatProvider
import com.michaldrabik.ui_base.utilities.extensions.removeDiacritics
import com.michaldrabik.ui_model.HistoryPeriod
import com.michaldrabik.ui_model.HistoryPeriod.ALL_TIME
import com.michaldrabik.ui_model.HistoryPeriod.LAST_30_DAYS
import com.michaldrabik.ui_model.HistoryPeriod.LAST_365_DAYS
import com.michaldrabik.ui_model.HistoryPeriod.LAST_90_DAYS
import com.michaldrabik.ui_model.HistoryPeriod.LAST_MONTH
import com.michaldrabik.ui_model.HistoryPeriod.LAST_WEEK
import com.michaldrabik.ui_model.HistoryPeriod.THIS_MONTH
import com.michaldrabik.ui_model.HistoryPeriod.THIS_WEEK
import com.michaldrabik.ui_model.ImageType
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_progress.helpers.TranslationsBundle
import com.michaldrabik.ui_progress.history.entities.HistoryListItem
import com.michaldrabik.ui_progress.history.utilities.groupers.HistoryItemsGrouper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters.firstDayOfMonth
import java.time.temporal.TemporalAdjusters.lastDayOfMonth
import javax.inject.Inject
import kotlin.time.Duration.Companion.days
import com.michaldrabik.ui_model.Episode as EpisodeUi

@Suppress("UNCHECKED_CAST")
internal class GetHistoryItemsCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val localSource: LocalDataSource,
  private val showsRepository: ShowsRepository,
  private val translationsRepository: TranslationsRepository,
  private val settingsRepository: SettingsRepository,
  private val imagesProvider: ShowImagesProvider,
  private val dateFormatProvider: DateFormatProvider,
  private val mappers: Mappers,
  private val grouper: HistoryItemsGrouper,
) {

  suspend fun loadItems(searchQuery: String? = "") =
    withContext(dispatchers.IO) {
      val shows = coroutineScope {
        val async1 = async { showsRepository.myShows.loadAll() }
        val async2 = async { showsRepository.watchlistShows.loadAll() }
        awaitAll(async1, async2).flatten()
      }
      val showsIds = shows.map { it.traktId }.chunked(250)

      val periodFilter = settingsRepository.filters.historyShowsPeriod
      val periodRange = getPeriodRange(periodFilter)

      val (episodes, seasons) = awaitAll(
        async {
          showsIds.fold(listOf<Episode>()) { acc, ids ->
            acc.plus(localSource.episodes.getAllWatchedForShows(ids, periodRange.first, periodRange.last))
          }
        },
        async {
          showsIds.fold(listOf<Season>()) { acc, ids ->
            acc.plus(localSource.seasons.getAllByShowsIds(ids))
          }
        },
      )

      val localEpisodes = episodes as List<Episode>
      val localSeasons = seasons as List<Season>

      val language = translationsRepository.getLanguage()
      val dateFormat = dateFormatProvider.loadFullHourFormat()

      val items = localEpisodes
        .map { episode ->
          async {
            val show = shows.firstOrNull { it.traktId == episode.idShowTrakt }
            val season = localSeasons.firstOrNull {
              it.idShowTrakt == episode.idShowTrakt &&
                it.seasonNumber == episode.seasonNumber
            }

            if (show == null || season == null) {
              return@async null
            }

            val seasonEpisodes = episodes.filter {
              it.idShowTrakt == season.idShowTrakt &&
                it.seasonNumber == season.seasonNumber
            }

            val episodeUi = mappers.episode.fromDatabase(episode)
            val seasonUi = mappers.season.fromDatabase(season, seasonEpisodes)

            HistoryListItem.Episode(
              show = show,
              season = seasonUi,
              episode = episodeUi,
              image = imagesProvider.findCachedImage(show, ImageType.POSTER),
              translations = getTranslation(language, show, episodeUi),
              dateFormat = dateFormat,
            )
          }
        }.awaitAll()
        .filterNotNull()

      val filtersItem = listOf(HistoryListItem.Filters(periodFilter))
      val searchItems = filterByQuery(searchQuery, dateFormat, items)
      val groupedItems = grouper.groupByDay(
        items = searchItems,
        language = language,
      )
      filtersItem + groupedItems
    }

  private fun filterByQuery(
    query: String?,
    dateFormat: DateTimeFormatter,
    items: List<HistoryListItem.Episode>,
  ): List<HistoryListItem.Episode> {
    if (query.isNullOrBlank()) {
      return items
    }
    return items.filter {
      it.show.title
        .removeDiacritics()
        .contains(query, true) ||
        it.episode.title
          .removeDiacritics()
          .contains(query, true) ||
        it.translations
          ?.show
          ?.title
          ?.removeDiacritics()
          ?.contains(query, true) == true ||
        it.translations
          ?.episode
          ?.title
          ?.removeDiacritics()
          ?.contains(query, true) == true ||
        it.episode.lastWatchedAt
          ?.toLocalZone()
          ?.format(dateFormat)
          ?.contains(query, true) == true
    }
  }

  private suspend fun getTranslation(
    language: String,
    show: Show,
    episode: EpisodeUi,
  ): TranslationsBundle? {
    if (language == DEFAULT_LANGUAGE) {
      return null
    }
    return TranslationsBundle(
      episode = translationsRepository.loadTranslation(
        language = language,
        showId = show.ids.trakt,
        episode = episode,
        onlyLocal = true,
      ),
      show = translationsRepository.loadTranslation(
        language = language,
        show = show,
        onlyLocal = true,
      ),
    )
  }

  private fun getPeriodRange(period: HistoryPeriod): LongRange {
    val nowUtcMillis = nowUtcMillis()
    return when (period) {
      THIS_WEEK -> {
        val nowLocal = dateFromMillis(nowUtcMillis).toLocalZone()

        val weekStartLocal = nowLocal
          .minusDays(nowLocal.dayOfWeek.ordinal.toLong())
          .with(LocalTime.MIN)
        val weekStartUtc = weekStartLocal.toUtcZone().toMillis()

        val weekEndLocal = weekStartLocal.plusDays(6).with(LocalTime.MAX)
        val weekEndUtc = weekEndLocal.toUtcZone().toMillis()

        return weekStartUtc..weekEndUtc
      }
      LAST_WEEK -> {
        val now = dateFromMillis(nowUtcMillis).toLocalZone()

        val weekStartLocal = now
          .minusDays(now.dayOfWeek.ordinal.toLong())
          .minusWeeks(1)
          .with(LocalTime.MIN)
        val weekStartUtc = weekStartLocal.toUtcZone().toMillis()

        val weekEndLocal = weekStartLocal.plusDays(6).with(LocalTime.MAX)
        val weekEndUtc = weekEndLocal.toUtcZone().toMillis()

        return weekStartUtc..weekEndUtc
      }
      THIS_MONTH -> {
        val now = dateFromMillis(nowUtcMillis).toLocalZone()

        val monthStartLocal = now.with(firstDayOfMonth()).with(LocalTime.MIN)
        val monthStartUtc = monthStartLocal.toUtcZone().toMillis()

        val monthEndLocal = monthStartLocal.with(lastDayOfMonth()).with(LocalTime.MAX)
        val monthEndUtc = monthEndLocal.toUtcZone().toMillis()

        return monthStartUtc..monthEndUtc
      }
      LAST_MONTH -> {
        val now = dateFromMillis(nowUtcMillis).toLocalZone()

        val monthStartLocal = now
          .with(firstDayOfMonth())
          .minusDays(1)
          .with(firstDayOfMonth())
          .with(LocalTime.MIN)
        val monthStartUtc = monthStartLocal.toUtcZone().toMillis()

        val monthEndLocal = monthStartLocal.with(lastDayOfMonth()).with(LocalTime.MAX)
        val monthEndUtc = monthEndLocal.toUtcZone().toMillis()

        return monthStartUtc..monthEndUtc
      }
      LAST_30_DAYS -> (nowUtcMillis - 30.days.inWholeMilliseconds)..nowUtcMillis
      LAST_90_DAYS -> (nowUtcMillis - 90.days.inWholeMilliseconds)..nowUtcMillis
      LAST_365_DAYS -> (nowUtcMillis - 365.days.inWholeMilliseconds)..nowUtcMillis
      ALL_TIME -> (nowUtcMillis - 36159.days.inWholeMilliseconds)..nowUtcMillis // Limited to 99 years
    }
  }
}
