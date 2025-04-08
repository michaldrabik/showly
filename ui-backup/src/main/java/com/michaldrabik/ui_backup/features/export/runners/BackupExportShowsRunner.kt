package com.michaldrabik.ui_backup.features.export.runners

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.common.extensions.dateIsoStringFromMillis
import com.michaldrabik.common.extensions.toMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.repository.OnHoldItemsRepository
import com.michaldrabik.repository.PinnedItemsRepository
import com.michaldrabik.repository.shows.ratings.ShowsRatingsRepository
import com.michaldrabik.ui_backup.model.BackupEpisode
import com.michaldrabik.ui_backup.model.BackupEpisodeRating
import com.michaldrabik.ui_backup.model.BackupSeason
import com.michaldrabik.ui_backup.model.BackupSeasonRating
import com.michaldrabik.ui_backup.model.BackupShow
import com.michaldrabik.ui_backup.model.BackupShowRating
import com.michaldrabik.ui_backup.model.BackupShows
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

internal class BackupExportShowsRunner @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val localSource: LocalDataSource,
  private val pinnedItemsRepository: PinnedItemsRepository,
  private val onHoldItemsRepository: OnHoldItemsRepository,
  private val ratingsRepository: ShowsRatingsRepository,
) : BackupExportRunner<BackupShows>() {

  override suspend fun run(): BackupShows {
    Timber.d("Initialized.")
    return runExport()
      .also {
        Timber.d("Success.")
      }
  }

  private suspend fun runExport(): BackupShows =
    withContext(dispatchers.IO) {
      val backupShowsCollection = exportShowsCollection()
      val backupShowsProgress = exportShowsProgress()
      val backupEpisodesProgress = exportEpisodesProgress()

      val backupShowsRatings = exportShowsRatings()
      val backupSeasonsRatings = exportSeasonsRatings()
      val backupEpisodesRatings = exportEpisodesRatings()

      BackupShows(
        collectionHistory = backupShowsCollection.collectionHistory,
        collectionWatchlist = backupShowsCollection.collectionWatchlist,
        collectionHidden = backupShowsCollection.collectionHidden,
        progressSeasons = backupEpisodesProgress.progressSeasons,
        progressEpisodes = backupEpisodesProgress.progressEpisodes,
        progressPinned = backupShowsProgress.progressPinned,
        progressOnHold = backupShowsProgress.progressOnHold,
        ratingsShows = backupShowsRatings.ratingsShows,
        ratingsSeasons = backupSeasonsRatings.ratingsSeasons,
        ratingsEpisodes = backupEpisodesRatings.ratingsEpisodes,
      )
    }

  private suspend fun exportShowsCollection(): BackupShows =
    withContext(dispatchers.IO) {
      val myShowsAsync = async { localSource.myShows.getAll() }
      val watchlistShowsAsync = async { localSource.watchlistShows.getAll() }
      val hiddenShowsAsync = async { localSource.archiveShows.getAll() }

      val myShows = myShowsAsync.await()
      val watchlistShows = watchlistShowsAsync.await()
      val hiddenShows = hiddenShowsAsync.await()

      val collectionMyShows = myShows.map {
        BackupShow(
          traktId = it.idTrakt,
          tmdbId = it.idTmdb,
          title = it.title,
          addedAt = dateIsoStringFromMillis(it.createdAt),
          updatedAt = dateIsoStringFromMillis(it.updatedAt),
        )
      }
      val collectionWatchlist = watchlistShows.map {
        BackupShow(
          traktId = it.idTrakt,
          tmdbId = it.idTmdb,
          title = it.title,
          addedAt = dateIsoStringFromMillis(it.createdAt),
          updatedAt = dateIsoStringFromMillis(it.updatedAt),
        )
      }
      val collectionHidden = hiddenShows.map {
        BackupShow(
          traktId = it.idTrakt,
          tmdbId = it.idTmdb,
          title = it.title,
          addedAt = dateIsoStringFromMillis(it.createdAt),
          updatedAt = dateIsoStringFromMillis(it.updatedAt),
        )
      }

      BackupShows(
        collectionHistory = collectionMyShows,
        collectionWatchlist = collectionWatchlist,
        collectionHidden = collectionHidden,
      )
    }

  private suspend fun exportEpisodesProgress(): BackupShows =
    withContext(dispatchers.IO) {
      val watchedEpisodesAsync = async { localSource.episodes.getAllWatched() }
      val watchedSeasonsAsync = async { localSource.seasons.getAllWatched() }

      val watchedEpisodes = watchedEpisodesAsync.await()
      val watchedSeasons = watchedSeasonsAsync.await()

      val seasonsIds = watchedSeasons.map { it.idShowTrakt }.distinct()
      val shows = localSource.shows.getAllTmdbIds(traktIds = seasonsIds)

      val progressSeasons = watchedSeasons.map { season ->
        BackupSeason(
          traktId = season.idTrakt,
          showTraktId = season.idShowTrakt,
          showTmdbId = shows.getOrDefault(season.idShowTrakt, -1),
          seasonNumber = season.seasonNumber,
        )
      }

      val progressEpisodes = watchedEpisodes.map { episode ->
        BackupEpisode(
          traktId = episode.idTrakt,
          showTraktId = episode.idShowTrakt,
          showTmdbId = episode.idShowTmdb,
          episodeNumber = episode.episodeNumber,
          seasonNumber = episode.seasonNumber,
          addedAt = episode.lastWatchedAt?.let { dateIsoStringFromMillis(it.toMillis()) },
        )
      }

      BackupShows(
        progressSeasons = progressSeasons,
        progressEpisodes = progressEpisodes,
      )
    }

  private suspend fun exportShowsProgress(): BackupShows =
    withContext(dispatchers.IO) {
      val pinnedIds = pinnedItemsRepository.getAllShows()
      val onHoldIds = onHoldItemsRepository.getAll().map { it.id }
      BackupShows(
        progressPinned = pinnedIds,
        progressOnHold = onHoldIds,
      )
    }

  // Ratings

  private suspend fun exportShowsRatings(): BackupShows =
    withContext(dispatchers.IO) {
      val ratings = ratingsRepository.loadShowsRatings()

      val showsIds = ratings.map { it.idTrakt.id }
      val showsTmdbIds = localSource.shows.getAllTmdbIds(traktIds = showsIds)

      val showsRatings = ratings.map {
        BackupShowRating(
          traktId = it.idTrakt.id,
          tmdbId = showsTmdbIds.getOrDefault(it.idTrakt.id, -1),
          rating = it.rating,
          ratedAt = dateIsoStringFromMillis(it.ratedAt.toMillis()),
        )
      }

      BackupShows(
        ratingsShows = showsRatings,
      )
    }

  private suspend fun exportSeasonsRatings(): BackupShows =
    withContext(dispatchers.IO) {
      val ratings = ratingsRepository.loadSeasonsRatings()
      val seasons = localSource.seasons.getAll(ratings.map { it.idTrakt })

      val showsIds = seasons.map { it.idShowTrakt }.distinct()
      val showsTmdbIds = localSource.shows.getAllTmdbIds(traktIds = showsIds)

      val seasonsRatings = ratings.map { rating ->
        val season = seasons.find { it.idTrakt == rating.idTrakt }
        val showTraktId = season?.idShowTrakt ?: -1
        val showTmdbId = showsTmdbIds.getOrDefault(showTraktId, -1)

        BackupSeasonRating(
          traktId = rating.idTrakt,
          showTraktId = showTraktId,
          showTmdbId = showTmdbId,
          seasonNumber = rating.seasonNumber ?: -1,
          rating = rating.rating,
          ratedAt = dateIsoStringFromMillis(rating.ratedAt.toMillis()),
        )
      }

      BackupShows(
        ratingsSeasons = seasonsRatings,
      )
    }

  private suspend fun exportEpisodesRatings(): BackupShows =
    withContext(dispatchers.IO) {
      val ratings = ratingsRepository.loadEpisodesRatings()
      val episodes = localSource.episodes.getAll(ratings.map { it.idTrakt })

      val episodesRatings = ratings.map { rating ->
        val episode = episodes.find { it.idTrakt == rating.idTrakt }

        BackupEpisodeRating(
          traktId = rating.idTrakt,
          showTraktId = episode?.idShowTrakt ?: -1,
          showTmdbId = episode?.idShowTmdb ?: -1,
          seasonNumber = rating.seasonNumber ?: -1,
          episodeNumber = rating.episodeNumber ?: -1,
          rating = rating.rating,
          ratedAt = dateIsoStringFromMillis(rating.ratedAt.toMillis()),
        )
      }

      BackupShows(
        ratingsEpisodes = episodesRatings,
      )
    }
}
