package com.michaldrabik.ui_backup.features.import_.runners

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.common.extensions.toMillis
import com.michaldrabik.common.extensions.toUtcDateTime
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.ArchiveMovie
import com.michaldrabik.data_local.database.model.MyMovie
import com.michaldrabik.data_local.database.model.Rating
import com.michaldrabik.data_local.database.model.WatchlistMovie
import com.michaldrabik.repository.PinnedItemsRepository
import com.michaldrabik.repository.movies.MoviesRepository
import com.michaldrabik.repository.movies.ratings.MoviesRatingsRepository
import com.michaldrabik.ui_backup.features.import_.model.BackupImportStatus.Importing
import com.michaldrabik.ui_backup.model.BackupMovie
import com.michaldrabik.ui_backup.model.BackupMovies
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import com.michaldrabik.ui_model.IdTrakt
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import timber.log.Timber
import javax.inject.Inject

internal class BackupImportMoviesRunner @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val localSource: LocalDataSource,
  private val moviesRepository: MoviesRepository,
  private val ratingsRepository: MoviesRatingsRepository,
  private val pinnedItemsRepository: PinnedItemsRepository,
) : BackupImportRunner<BackupMovies>() {

  override suspend fun run(backup: BackupMovies) {
    Timber.d("Initialized.")
    runImport(backup)
      .also {
        Timber.d("Success.")
      }
  }

  private suspend fun runImport(backup: BackupMovies) {
    withContext(dispatchers.IO) {
      importMoviesCollection(backup)
      importMoviesPinned(backup)
      importMoviesRatings(backup)
    }
  }

  private suspend fun importMoviesPinned(backup: BackupMovies) {
    withContext(dispatchers.IO) {
      val localPinned = pinnedItemsRepository.getAllMovies()
      for (pinned in backup.progressPinned) {
        if (!localPinned.contains(pinned)) {
          pinnedItemsRepository.addMoviePinnedItem(IdTrakt(pinned))
        }
      }
    }
  }

  private suspend fun importMoviesRatings(backup: BackupMovies) {
    withContext(dispatchers.IO) {
      val localRatings = ratingsRepository.loadMoviesRatings()

      for (rating in backup.ratingsMovies) {
        if (localRatings.any { it.idTrakt.id == rating.traktId }) {
          continue
        }

        val entity = Rating(
          idTrakt = rating.traktId,
          type = "movie",
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

  private suspend fun importMoviesCollection(backup: BackupMovies) {
    withContext(dispatchers.IO) {
      val localCollection = moviesRepository
        .loadCollection()
        .map { it.traktId }

      importMyMovies(backup, localCollection)
      importWatchlistMovies(backup, localCollection)
      importHiddenMovies(backup, localCollection)
    }
  }

  private suspend fun importMyMovies(
    backupMovies: BackupMovies,
    localCollection: List<Long>,
  ) {
    for (movie in backupMovies.collectionHistory) {
      Timber.d("Importing movie ${movie.traktId} ...")
      statusListener?.invoke(Importing(movie.title))

      if (localCollection.contains(movie.traktId)) {
        Timber.d("Movie already in collection. Skipping.")
        continue
      }

      val movieDetails = localSource.movies.getById(movie.traktId)
      if (movieDetails == null) {
        if (!fetchMovieDetails(movie)) {
          continue
        }
      }

      val timestamp = movie.addedAt.toUtcDateTime()?.toMillis() ?: nowUtcMillis()
      val myMovie = MyMovie.fromTraktId(movie.traktId, timestamp)
      localSource.myMovies.insert(listOf(myMovie))

      Timber.d("Added to My Movies ${movie.traktId} ...")
    }
  }

  private suspend fun importWatchlistMovies(
    backupMovies: BackupMovies,
    localCollection: List<Long>,
  ) {
    for (movie in backupMovies.collectionWatchlist) {
      Timber.d("Importing movie ${movie.traktId} ...")
      statusListener?.invoke(Importing(movie.title))

      if (localCollection.contains(movie.traktId)) {
        Timber.d("Movie already in collection. Skipping.")
        continue
      }

      val movieDetails = localSource.movies.getById(movie.traktId)
      if (movieDetails == null) {
        if (!fetchMovieDetails(movie)) {
          continue
        }
      }

      val timestamp = movie.addedAt.toUtcDateTime()?.toMillis() ?: nowUtcMillis()
      val watchlistMovie = WatchlistMovie.fromTraktId(movie.traktId, timestamp)
      localSource.watchlistMovies.insert(watchlistMovie)

      Timber.d("Added to Watchlist ${movie.traktId} ...")
    }
  }

  private suspend fun importHiddenMovies(
    backupMovies: BackupMovies,
    localCollection: List<Long>,
  ) {
    for (movie in backupMovies.collectionHidden) {
      Timber.d("Importing movie ${movie.traktId} ...")
      statusListener?.invoke(Importing(movie.title))

      if (localCollection.contains(movie.traktId)) {
        Timber.d("Movie already in collection. Skipping.")
        continue
      }

      val movieDetails = localSource.movies.getById(movie.traktId)
      if (movieDetails == null) {
        if (!fetchMovieDetails(movie)) {
          continue
        }
      }

      val timestamp = movie.addedAt.toUtcDateTime()?.toMillis() ?: nowUtcMillis()
      val hiddenMovie = ArchiveMovie.fromTraktId(movie.traktId, timestamp)
      localSource.archiveMovies.insert(hiddenMovie)

      Timber.d("Added to Hidden ${movie.traktId} ...")
    }
  }

  private suspend fun fetchMovieDetails(movie: BackupMovie): Boolean {
    Timber.d("Fetching remote movie details for ${movie.traktId} ...")
    return try {
      moviesRepository.movieDetails.load(IdTrakt(movie.traktId), force = true)
      true
    } catch (error: Throwable) {
      rethrowCancellation(error) {
        if (error is HttpException && error.code() == 404) {
          Timber.w("Failed to fetch movie: ${movie.traktId} ${movie.title}")
        }
      }
      false
    }
  }
}
