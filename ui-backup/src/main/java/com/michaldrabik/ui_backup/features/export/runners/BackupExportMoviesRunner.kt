package com.michaldrabik.ui_backup.features.export.runners

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.common.extensions.dateIsoStringFromMillis
import com.michaldrabik.common.extensions.toMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.repository.PinnedItemsRepository
import com.michaldrabik.repository.movies.ratings.MoviesRatingsRepository
import com.michaldrabik.ui_backup.model.BackupMovie
import com.michaldrabik.ui_backup.model.BackupMovieRating
import com.michaldrabik.ui_backup.model.BackupMovies
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

internal class BackupExportMoviesRunner @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val localSource: LocalDataSource,
  private val pinnedItemsRepository: PinnedItemsRepository,
  private val ratingsRepository: MoviesRatingsRepository,
) : BackupExportRunner<BackupMovies>() {

  override suspend fun run(): BackupMovies {
    Timber.d("Initialized.")
    return runExport()
      .also {
        Timber.d("Success.")
      }
  }

  private suspend fun runExport(): BackupMovies =
    withContext(dispatchers.IO) {
      val backupMoviesCollection = exportMoviesCollection()
      val backupMoviesProgress = exportMoviesProgress()
      val backupMoviesRatings = exportMoviesRatings()

      BackupMovies(
        collectionHistory = backupMoviesCollection.collectionHistory,
        collectionWatchlist = backupMoviesCollection.collectionWatchlist,
        collectionHidden = backupMoviesCollection.collectionHidden,
        progressPinned = backupMoviesProgress.progressPinned,
        ratingsMovies = backupMoviesRatings.ratingsMovies,
      )
    }

  private suspend fun exportMoviesCollection(): BackupMovies =
    withContext(dispatchers.IO) {
      val historyMoviesAsync = async { localSource.myMovies.getAll() }
      val watchlistMoviesAsync = async { localSource.watchlistMovies.getAll() }
      val hiddenMoviesAsync = async { localSource.archiveMovies.getAll() }

      val historyMovies = historyMoviesAsync.await()
      val watchlistMovies = watchlistMoviesAsync.await()
      val hiddenMovies = hiddenMoviesAsync.await()

      val collectionHistory = historyMovies.map {
        BackupMovie(
          traktId = it.idTrakt,
          tmdbId = it.idTmdb,
          title = it.title,
          addedAt = dateIsoStringFromMillis(it.updatedAt),
        )
      }
      val collectionWatchlist = watchlistMovies.map {
        BackupMovie(
          traktId = it.idTrakt,
          tmdbId = it.idTmdb,
          title = it.title,
          addedAt = dateIsoStringFromMillis(it.createdAt),
        )
      }
      val collectionHidden = hiddenMovies.map {
        BackupMovie(
          traktId = it.idTrakt,
          tmdbId = it.idTmdb,
          title = it.title,
          addedAt = dateIsoStringFromMillis(it.createdAt),
        )
      }

      BackupMovies(
        collectionHistory = collectionHistory,
        collectionWatchlist = collectionWatchlist,
        collectionHidden = collectionHidden,
      )
    }

  private suspend fun exportMoviesProgress(): BackupMovies =
    withContext(dispatchers.IO) {
      val pinnedMoviesIds = pinnedItemsRepository.getAllMovies()
      BackupMovies(
        progressPinned = pinnedMoviesIds,
      )
    }

  // Ratings

  private suspend fun exportMoviesRatings(): BackupMovies =
    withContext(dispatchers.IO) {
      val ratings = ratingsRepository.loadMoviesRatings()

      val moviesIds = ratings.map { it.idTrakt.id }
      val moviesTmdbIds = localSource.movies.getAllTmdbIds(traktIds = moviesIds)

      val ratingsMovies = ratings.map {
        BackupMovieRating(
          traktId = it.idTrakt.id,
          tmdbId = moviesTmdbIds.getOrDefault(it.idTrakt.id, -1),
          rating = it.rating,
          ratedAt = dateIsoStringFromMillis(it.ratedAt.toMillis()),
        )
      }

      BackupMovies(
        ratingsMovies = ratingsMovies,
      )
    }
}
