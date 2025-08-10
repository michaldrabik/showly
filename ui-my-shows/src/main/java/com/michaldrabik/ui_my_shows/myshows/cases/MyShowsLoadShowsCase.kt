package com.michaldrabik.ui_my_shows.myshows.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.Season
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.repository.shows.ShowsRepository
import com.michaldrabik.ui_base.utilities.extensions.removeDiacritics
import com.michaldrabik.ui_model.MyShowsSection.FINISHED
import com.michaldrabik.ui_model.MyShowsSection.UPCOMING
import com.michaldrabik.ui_model.MyShowsSection.WATCHING
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_model.ShowStatus.RETURNING
import com.michaldrabik.ui_my_shows.myshows.helpers.MyShowsItemSorter
import com.michaldrabik.ui_my_shows.myshows.recycler.MyShowsItem
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
class MyShowsLoadShowsCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val sorter: MyShowsItemSorter,
  private val showsRepository: ShowsRepository,
  private val settingsRepository: SettingsRepository,
  private val localSource: LocalDataSource,
) {

  suspend fun loadAllShows() =
    withContext(dispatchers.IO) {
      showsRepository.myShows.loadAll()
    }

  suspend fun loadRecentShows(): List<Show> =
    withContext(dispatchers.IO) {
      val amount = settingsRepository.load().myRecentsAmount
      showsRepository.myShows.loadAllRecent(amount)
    }

  suspend fun loadSeasonsForShows(
    traktIds: List<Long>,
    buffer: MutableList<Season> = mutableListOf(),
  ): List<Season> =
    withContext(dispatchers.IO) {
      val batch = traktIds.take(500)
      if (batch.isEmpty()) {
        return@withContext buffer
      }

      val seasons = localSource.seasons
        .getAllByShowsIds(batch)
        .filter { it.seasonNumber != 0 }
      buffer.addAll(seasons)

      loadSeasonsForShows(traktIds.filter { it !in batch }, buffer)
    }

  fun filterSectionShows(
    allShows: List<MyShowsItem>,
    allSeasons: List<Season>,
    searchQuery: String? = null,
    networks: List<String>,
    genres: List<String>,
  ): List<MyShowsItem> {
    val shows = allShows
      .filter { showItem ->
        val seasons = allSeasons.filter { it.idShowTrakt == showItem.show.traktId }
        val airedSeasons = seasons.filter { it.seasonFirstAired?.isBefore(nowUtc()) == true }

        when (val type = settingsRepository.filters.myShowsType) {
          WATCHING -> {
            airedSeasons.any { !it.isWatched }
          }
          FINISHED -> {
            type.allowedStatuses.contains(showItem.show.status) && seasons.all { it.isWatched }
          }
          UPCOMING -> {
            type.allowedStatuses.contains(showItem.show.status) ||
              (showItem.show.status == RETURNING && airedSeasons.all { it.isWatched })
          }
          else -> true
        }
      }

    return shows
      .filterByQuery(searchQuery)
      .filterByNetwork(networks)
      .filterByGenre(genres)
      .sortedWith(
        sorter.sort(
          sortOrder = settingsRepository.sorting.myShowsAllSortOrder,
          sortType = settingsRepository.sorting.myShowsAllSortType,
        ),
      )
  }

  private fun List<MyShowsItem>.filterByQuery(query: String?) =
    when {
      query.isNullOrBlank() -> this
      else -> this.filter {
        it.show.title.removeDiacritics().contains(query, true) ||
          it.translation?.title?.removeDiacritics()?.contains(query, true) == true
      }
    }

  private fun List<MyShowsItem>.filterByNetwork(networks: List<String>) =
    filter { networks.isEmpty() || it.show.network in networks }

  private fun List<MyShowsItem>.filterByGenre(genres: List<String>) =
    filter { genres.isEmpty() || it.show.genres.any { genre -> genre.lowercase() in genres } }
}
