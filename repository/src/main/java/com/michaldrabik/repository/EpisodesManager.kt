package com.michaldrabik.repository

import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.common.extensions.toMillis
import com.michaldrabik.common.extensions.toUtcZone
import com.michaldrabik.data_local.database.model.EpisodesSyncLog
import com.michaldrabik.data_local.sources.EpisodesLocalDataSource
import com.michaldrabik.data_local.sources.EpisodesSyncLogLocalDataSource
import com.michaldrabik.data_local.sources.SeasonsLocalDataSource
import com.michaldrabik.data_local.utilities.TransactionsProvider
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.repository.shows.ShowsRepository
import com.michaldrabik.ui_model.Episode
import com.michaldrabik.ui_model.EpisodeBundle
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Season
import com.michaldrabik.ui_model.SeasonBundle
import com.michaldrabik.ui_model.Show
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import com.michaldrabik.data_local.database.model.Episode as EpisodeDb
import com.michaldrabik.data_local.database.model.Season as SeasonDb

@Singleton
class EpisodesManager @Inject constructor(
  private val showsRepository: ShowsRepository,
  private val episodesLocalSource: EpisodesLocalDataSource,
  private val seasonsLocalSource: SeasonsLocalDataSource,
  private val syncLogLocalSource: EpisodesSyncLogLocalDataSource,
  private val transactions: TransactionsProvider,
  private val mappers: Mappers,
) {

  suspend fun getWatchedSeasonsIds(show: Show) = seasonsLocalSource.getAllWatchedIdsForShows(listOf(show.traktId))

  suspend fun getWatchedEpisodesIds(show: Show) = episodesLocalSource.getAllWatchedIdsForShows(listOf(show.traktId))

  suspend fun setSeasonWatched(
    seasonBundle: SeasonBundle,
    customDate: ZonedDateTime?,
  ): List<Episode> {
    val date = customDate?.toUtcZone() ?: nowUtc()
    val toAdd = mutableListOf<EpisodeDb>()
    transactions.withTransaction {
      val (season, show) = seasonBundle

      val dbSeason = mappers.season.toDatabase(season, show.ids.trakt, true)
      val localSeason = seasonsLocalSource.getById(season.ids.trakt.id)
      if (localSeason == null) {
        seasonsLocalSource.upsert(listOf(dbSeason))
      }

      val watchedEpisodes = episodesLocalSource.getAllForSeason(season.ids.trakt.id).filter { it.isWatched }
      season.episodes.forEach { ep ->
        if (watchedEpisodes.none { it.idTrakt == ep.ids.trakt.id }) {
          val dbEpisode = mappers.episode.toDatabase(ep, season, show.ids.trakt, true, null, date)
          toAdd.add(dbEpisode)
        }
      }

      episodesLocalSource.upsert(toAdd)
      seasonsLocalSource.update(listOf(dbSeason))
      showsRepository.myShows.updateWatchedAt(show.traktId, date.toMillis())
    }
    return toAdd.map { mappers.episode.fromDatabase(it) }
  }

  suspend fun setSeasonUnwatched(seasonBundle: SeasonBundle) {
    transactions.withTransaction {
      val (season, show) = seasonBundle

      val dbSeason = mappers.season.toDatabase(season, show.ids.trakt, false)
      val watchedEpisodes = episodesLocalSource.getAllForSeason(season.ids.trakt.id).filter { it.isWatched }
      val toSet = watchedEpisodes.map { it.copy(isWatched = false, lastExportedAt = null, lastWatchedAt = null) }

      val isShowFollowed = showsRepository.myShows.load(show.ids.trakt) != null

      when {
        isShowFollowed -> {
          episodesLocalSource.upsert(toSet)
          seasonsLocalSource.update(listOf(dbSeason))
        }
        else -> {
          episodesLocalSource.delete(toSet)
          seasonsLocalSource.delete(listOf(dbSeason))
        }
      }
    }
  }

  suspend fun setEpisodeWatched(
    episodeId: Long,
    seasonId: Long,
    showId: IdTrakt,
    customDate: ZonedDateTime?,
  ) {
    val episodeDb = episodesLocalSource.getAllForSeason(seasonId).find { it.idTrakt == episodeId }!!
    val seasonDb = seasonsLocalSource.getById(seasonId)!!
    val show = showsRepository.myShows.load(showId)!!
    setEpisodeWatched(
      episodeBundle = EpisodeBundle(
        episode = mappers.episode.fromDatabase(episodeDb),
        season = mappers.season.fromDatabase(seasonDb),
        show = show,
      ),
      customDate = customDate,
    )
  }

  suspend fun setEpisodeWatched(
    episodeBundle: EpisodeBundle,
    customDate: ZonedDateTime?,
  ) {
    transactions.withTransaction {
      val (episode, season, show) = episodeBundle
      val date = customDate?.toUtcZone() ?: nowUtc()

      val dbEpisode = mappers.episode.toDatabase(episode, season, show.ids.trakt, true, null, date)
      val dbSeason = mappers.season.toDatabase(season, show.ids.trakt, false)

      val localSeason = seasonsLocalSource.getById(season.ids.trakt.id)
      if (localSeason == null) {
        seasonsLocalSource.upsert(listOf(dbSeason))
      }
      episodesLocalSource.upsert(listOf(dbEpisode))
      showsRepository.myShows.updateWatchedAt(show.traktId, date.toMillis())
      onEpisodeSet(season, show)
    }
  }

  suspend fun setEpisodeUnwatched(episodeBundle: EpisodeBundle) {
    transactions.withTransaction {
      val (episode, season, show) = episodeBundle

      val isShowFollowed = showsRepository.myShows.load(show.ids.trakt) != null
      val dbEpisode = mappers.episode.toDatabase(episode, season, show.ids.trakt, true, null, episode.lastWatchedAt)

      when {
        isShowFollowed -> {
          val ep = dbEpisode.copy(
            isWatched = false,
            lastExportedAt = null,
            lastWatchedAt = null,
          )
          episodesLocalSource.upsert(listOf(ep))
        }
        else -> episodesLocalSource.delete(listOf(dbEpisode))
      }

      onEpisodeSet(season, show)
    }
  }

  suspend fun setAllUnwatched(
    showId: IdTrakt,
    skipSpecials: Boolean = false,
  ) {
    transactions.withTransaction {
      val watchedEpisodes = episodesLocalSource.getAllByShowId(showId.id)
      val watchedSeasons = seasonsLocalSource.getAllByShowId(showId.id)

      val updateEpisodes = watchedEpisodes
        .filter { if (skipSpecials) it.seasonNumber > 0 else true }
        .map { it.copy(isWatched = false, lastExportedAt = null, lastWatchedAt = null) }
      val updateSeasons = watchedSeasons
        .filter { if (skipSpecials) it.seasonNumber > 0 else true }
        .map { it.copy(isWatched = false) }

      episodesLocalSource.upsert(updateEpisodes)
      seasonsLocalSource.update(updateSeasons)
    }
  }

  @Suppress("UNCHECKED_CAST")
  suspend fun invalidateSeasons(
    show: Show,
    remoteSeasons: List<Season>,
  ) {
    if (remoteSeasons.isEmpty()) {
      return
    }
    coroutineScope {
      val (localSeasons, localEpisodes) = awaitAll(
        async { seasonsLocalSource.getAllByShowId(show.traktId) },
        async { episodesLocalSource.getAllByShowId(show.traktId) },
      )
      localSeasons as List<SeasonDb>
      localEpisodes as List<EpisodeDb>

      val seasonsToAdd = mutableListOf<SeasonDb>()
      val episodesToAdd = mutableListOf<EpisodeDb>()

      remoteSeasons.forEach { remoteSeason ->
        var isAnyEpisodeUnwatched = false

        remoteSeason.episodes.forEach { remoteEpisode ->
          var localEpisode = localEpisodes.find {
            it.episodeNumber == remoteEpisode.number &&
              it.seasonNumber == remoteEpisode.season
          }
          if (localEpisode == null) {
            // Double check by Trakt ID as season/episode combination might be old.
            localEpisode = localEpisodes.find {
              it.idTrakt == remoteEpisode.ids.trakt.id
            }
          }

          val isWatched = localEpisode?.isWatched ?: false
          if (!isWatched) {
            isAnyEpisodeUnwatched = true
          }

          val episodeDb = mappers.episode.toDatabase(
            episode = remoteEpisode,
            season = remoteSeason,
            showId = show.ids.trakt,
            isWatched = isWatched,
            lastExportedAt = localEpisode?.lastExportedAt,
            lastWatchedAt = localEpisode?.lastWatchedAt,
          )
          episodesToAdd.add(episodeDb)
        }

        val seasonDb = mappers.season.toDatabase(
          season = remoteSeason,
          showId = show.ids.trakt,
          isWatched = !isAnyEpisodeUnwatched,
        )
        seasonsToAdd.add(seasonDb)
      }

      transactions.withTransaction {
        episodesLocalSource.deleteAllForShow(show.traktId)
        seasonsLocalSource.deleteAllForShow(show.traktId)

        seasonsLocalSource.upsert(seasonsToAdd)
        episodesLocalSource.upsertChunked(episodesToAdd)

        syncLogLocalSource.upsert(EpisodesSyncLog(show.traktId, nowUtcMillis()))
      }

      Timber.d("Episodes updated: ${episodesToAdd.size} Seasons updated: ${seasonsToAdd.size}")
    }
  }

  private suspend fun onEpisodeSet(
    season: Season,
    show: Show,
  ) {
    val localEpisodes = episodesLocalSource.getAllForSeason(season.ids.trakt.id)
    val isWatched = localEpisodes.count { it.isWatched } == season.episodeCount
    val dbSeason = mappers.season.toDatabase(season, show.ids.trakt, isWatched)
    seasonsLocalSource.update(listOf(dbSeason))
  }
}
