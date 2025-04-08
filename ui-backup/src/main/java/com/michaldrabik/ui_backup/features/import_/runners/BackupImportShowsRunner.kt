package com.michaldrabik.ui_backup.features.import_.runners

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.common.extensions.toMillis
import com.michaldrabik.common.extensions.toUtcDateTime
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.ArchiveShow
import com.michaldrabik.data_local.database.model.Episode
import com.michaldrabik.data_local.database.model.MyShow
import com.michaldrabik.data_local.database.model.Rating
import com.michaldrabik.data_local.database.model.Season
import com.michaldrabik.data_local.database.model.WatchlistShow
import com.michaldrabik.data_local.utilities.TransactionsProvider
import com.michaldrabik.data_remote.RemoteDataSource
import com.michaldrabik.repository.EpisodesManager
import com.michaldrabik.repository.OnHoldItemsRepository
import com.michaldrabik.repository.PinnedItemsRepository
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.repository.shows.ShowsRepository
import com.michaldrabik.repository.shows.ratings.ShowsRatingsRepository
import com.michaldrabik.ui_backup.features.import_.model.BackupImportStatus.Importing
import com.michaldrabik.ui_backup.model.BackupShow
import com.michaldrabik.ui_backup.model.BackupShows
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import com.michaldrabik.ui_model.IdTrakt
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import timber.log.Timber
import javax.inject.Inject

internal class BackupImportShowsRunner @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val localSource: LocalDataSource,
  private val remoteSource: RemoteDataSource,
  private val showsRepository: ShowsRepository,
  private val pinnedItemsRepository: PinnedItemsRepository,
  private val onHoldItemsRepository: OnHoldItemsRepository,
  private val ratingsRepository: ShowsRatingsRepository,
  private val episodesManager: EpisodesManager,
  private val mappers: Mappers,
  private val transactions: TransactionsProvider,
) : BackupImportRunner<BackupShows>() {

  override suspend fun run(backup: BackupShows) {
    Timber.d("Initialized.")
    runImport(backup)
      .also {
        Timber.d("Success.")
      }
  }

  private suspend fun runImport(backup: BackupShows) {
    withContext(dispatchers.IO) {
      importShowsCollection(backup)

      importShowsPinned(backup)
      importShowsOnHold(backup)

      importShowsRatings(backup)
      importSeasonsRatings(backup)
      importEpisodesRatings(backup)
    }
  }

  private suspend fun importShowsCollection(backup: BackupShows) {
    withContext(dispatchers.IO) {
      val localCollection = showsRepository
        .loadCollection()
        .map { it.traktId }

      importMyShows(backup, localCollection)
      importWatchlistShows(backup, localCollection)
      importHiddenShows(backup, localCollection)
    }
  }

  private suspend fun importMyShows(
    backupShows: BackupShows,
    localCollection: List<Long>,
  ) {
    for (show in backupShows.collectionHistory) {
      Timber.d("Importing show ${show.traktId} ...")
      statusListener?.invoke(Importing(show.title))

      if (localCollection.contains(show.traktId)) {
        if (showsRepository.myShows.exists(IdTrakt(show.traktId))) {
          importExistingMyShowEpisodes(IdTrakt(show.traktId), backupShows)
          continue
        }
        Timber.d("Show already in collection. Skipping.")
        continue
      }

      val showDetails = localSource.shows.getById(show.traktId)
      if (showDetails == null) {
        if (!fetchShowDetails(show)) {
          continue
        }
      }

      val addedAt = show.addedAt.toUtcDateTime()?.toMillis() ?: nowUtcMillis()
      val updatedAt = show.updatedAt.toUtcDateTime()?.toMillis() ?: nowUtcMillis()
      val myShows = MyShow.fromTraktId(
        traktId = show.traktId,
        createdAt = addedAt,
        updatedAt = addedAt,
        watchedAt = updatedAt,
      )

      Timber.d("New show in My Shows. Importing season, episodes ...")
      val (seasons, episodes) = loadSeasonsEpisodes(show.traktId, backupShows)

      transactions.withTransaction {
        localSource.seasons.upsert(seasons)
        localSource.episodes.upsert(episodes)
        localSource.myShows.insert(listOf(myShows))
      }

      Timber.d("Added to My Shows ${show.traktId} ...")
    }
  }

  private suspend fun importWatchlistShows(
    backupShows: BackupShows,
    localCollection: List<Long>,
  ) {
    for (show in backupShows.collectionWatchlist) {
      Timber.d("Importing show ${show.traktId} ...")
      statusListener?.invoke(Importing(show.title))

      if (localCollection.contains(show.traktId)) {
        Timber.d("Show already in collection. Skipping.")
        continue
      }

      val showDetails = localSource.shows.getById(show.traktId)
      if (showDetails == null) {
        if (!fetchShowDetails(show)) {
          continue
        }
      }

      val timestamp = show.addedAt.toUtcDateTime()?.toMillis() ?: nowUtcMillis()
      val watchlistShow = WatchlistShow.fromTraktId(show.traktId, timestamp)
      localSource.watchlistShows.insert(watchlistShow)

      Timber.d("Added to Watchlist ${show.traktId} ...")
    }
  }

  private suspend fun importHiddenShows(
    backupShows: BackupShows,
    localCollection: List<Long>,
  ) {
    for (show in backupShows.collectionHidden) {
      Timber.d("Importing show ${show.traktId} ...")
      statusListener?.invoke(Importing(show.title))

      if (localCollection.contains(show.traktId)) {
        Timber.d("Show already in collection. Skipping.")
        continue
      }

      val showDetails = localSource.shows.getById(show.traktId)
      if (showDetails == null) {
        if (!fetchShowDetails(show)) {
          continue
        }
      }

      val timestamp = show.addedAt.toUtcDateTime()?.toMillis() ?: nowUtcMillis()
      val hiddenShow = ArchiveShow.fromTraktId(show.traktId, timestamp)
      localSource.archiveShows.insert(hiddenShow)

      Timber.d("Added to Hidden ${show.traktId} ...")
    }
  }

  private suspend fun importShowsPinned(backup: BackupShows) {
    withContext(dispatchers.IO) {
      val localPinned = pinnedItemsRepository.getAllShows()
      for (pinned in backup.progressPinned) {
        if (!localPinned.contains(pinned)) {
          pinnedItemsRepository.addShowPinnedItem(IdTrakt(pinned))
        }
      }
    }
  }

  private suspend fun importShowsOnHold(backup: BackupShows) {
    withContext(dispatchers.IO) {
      val localOnHold = onHoldItemsRepository.getAll().map { it.id }
      for (onHoldShow in backup.progressOnHold) {
        if (!localOnHold.contains(onHoldShow)) {
          onHoldItemsRepository.addItem(IdTrakt(onHoldShow))
        }
      }
    }
  }

  private suspend fun importShowsRatings(backup: BackupShows) {
    withContext(dispatchers.IO) {
      val localRatings = ratingsRepository.loadShowsRatings()

      for (rating in backup.ratingsShows) {
        if (localRatings.any { it.idTrakt.id == rating.traktId }) {
          continue
        }

        val entity = Rating(
          idTrakt = rating.traktId,
          type = "show",
          rating = rating.rating,
          seasonNumber = null,
          episodeNumber = null,
          ratedAt = rating.ratedAt.toUtcDateTime() ?: nowUtc(),
          createdAt = nowUtc(),
          updatedAt = nowUtc(),
        )

        localSource.ratings.replace(entity)
      }
    }
  }

  private suspend fun importSeasonsRatings(backup: BackupShows) {
    withContext(dispatchers.IO) {
      val localRatings = ratingsRepository.loadSeasonsRatings()

      for (rating in backup.ratingsSeasons) {
        if (localRatings.any { it.idTrakt == rating.traktId }) {
          continue
        }

        val entity = Rating(
          idTrakt = rating.traktId,
          type = "season",
          rating = rating.rating,
          seasonNumber = rating.seasonNumber,
          episodeNumber = null,
          ratedAt = rating.ratedAt.toUtcDateTime() ?: nowUtc(),
          createdAt = nowUtc(),
          updatedAt = nowUtc(),
        )

        localSource.ratings.replace(entity)
      }
    }
  }

  private suspend fun importEpisodesRatings(backup: BackupShows) {
    withContext(dispatchers.IO) {
      val localRatings = ratingsRepository.loadEpisodesRatings()

      for (rating in backup.ratingsEpisodes) {
        if (localRatings.any { it.idTrakt == rating.traktId }) {
          continue
        }

        val entity = Rating(
          idTrakt = rating.traktId,
          type = "episode",
          rating = rating.rating,
          seasonNumber = rating.seasonNumber,
          episodeNumber = rating.episodeNumber,
          ratedAt = rating.ratedAt.toUtcDateTime() ?: nowUtc(),
          createdAt = nowUtc(),
          updatedAt = nowUtc(),
        )

        localSource.ratings.replace(entity)
      }
    }
  }

  private suspend fun importExistingMyShowEpisodes(
    showId: IdTrakt,
    backup: BackupShows,
  ) {
    Timber.d("Show already in My Shows. Importing episodes ...")
    withContext(dispatchers.IO) {
      val show = localSource.shows.getById(showId.id) ?: return@withContext
      val importEpisodes = backup.progressEpisodes
        .filter { it.showTraktId == showId.id }

      val localEpisodesAsync = async { localSource.episodes.getAllByShowId(show.idTrakt) }
      val localEpisodes = localEpisodesAsync.await()

      if (localEpisodes.isEmpty()) {
        return@withContext
      }

      for (importEpisode in importEpisodes) {
        val localEpisode = localEpisodes
          .firstOrNull {
            it.idTrakt == importEpisode.traktId ||
              (it.seasonNumber == importEpisode.seasonNumber && it.episodeNumber == importEpisode.episodeNumber)
          }

        if (localEpisode != null && !localEpisode.isWatched) {
          episodesManager.setEpisodeWatched(
            showId = showId,
            seasonId = localEpisode.idSeason,
            episodeId = localEpisode.idTrakt,
            customDate = importEpisode.addedAt?.toUtcDateTime(),
          )
        }
      }
    }
  }

  private suspend fun loadSeasonsEpisodes(
    showId: Long,
    backupShows: BackupShows,
  ): Pair<List<Season>, List<Episode>> =
    coroutineScope {
      val remoteSeasons = remoteSource.trakt.fetchSeasons(showId)

      val localEpisodesAsync = async { localSource.episodes.getAllWatchedIdsForShows(listOf(showId)) }
      val localSeasonsAsync = async { localSource.seasons.getAllWatchedIdsForShows(listOf(showId)) }
      val localEpisodesIds = localEpisodesAsync.await()
      val localSeasonsIds = localSeasonsAsync.await()

      val backupSeason = backupShows.progressSeasons.filter { it.showTraktId == showId }
      val backupEpisodes = backupShows.progressEpisodes.filter { it.showTraktId == showId }

      val seasons = remoteSeasons
        .filterNot { localSeasonsIds.contains(it.ids?.trakt) }
        .map { mappers.season.fromNetwork(it) }
        .map { remoteSeason ->
          val isWatchedNumber = backupSeason
            .any { it.seasonNumber == remoteSeason.number }

          val isWatchedSize = backupEpisodes
            .count { it.seasonNumber == remoteSeason.number } == remoteSeason.episodes.size

          mappers.season.toDatabase(
            season = remoteSeason,
            showId = IdTrakt(showId),
            isWatched = isWatchedNumber && isWatchedSize,
          )
        }

      val episodes = remoteSeasons.flatMap { season ->
        season.episodes
          ?.filterNot { localEpisodesIds.contains(it.ids?.trakt) }
          ?.map { episode ->
            val importEpisode = backupEpisodes
              .find {
                it.seasonNumber == episode.season &&
                  it.episodeNumber == episode.number
              }

            val watchedAt = importEpisode?.addedAt?.toUtcDateTime()
            val exportedAt = importEpisode?.let {
              it.addedAt?.toUtcDateTime() ?: nowUtc()
            }

            mappers.episode.toDatabase(
              showId = IdTrakt(showId),
              season = mappers.season.fromNetwork(season),
              episode = mappers.episode.fromNetwork(episode),
              isWatched = importEpisode != null,
              lastExportedAt = exportedAt,
              lastWatchedAt = watchedAt,
            )
          } ?: emptyList()
      }

      Pair(seasons, episodes)
    }

  private suspend fun fetchShowDetails(show: BackupShow): Boolean {
    Timber.d("Fetching remote show details for ${show.traktId} ...")
    return try {
      showsRepository.detailsShow.load(IdTrakt(show.traktId), force = true)
      true
    } catch (error: Throwable) {
      rethrowCancellation(error) {
        if (error is HttpException && error.code() == 404) {
          Timber.w("Failed to fetch show: ${show.traktId} ${show.title}")
        }
      }
      false
    }
  }
}
