package com.michaldrabik.ui_base.trakt.imports

import com.michaldrabik.repository.RatingsRepository
import com.michaldrabik.repository.UserTraktManager
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_base.trakt.TraktSyncRunner
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TraktImportRatingsRunner @Inject constructor(
  private val ratingsRepository: RatingsRepository,
  private val settingsRepository: SettingsRepository,
  userTraktManager: UserTraktManager,
) : TraktSyncRunner(userTraktManager) {

  override suspend fun run(): Int {
    Timber.d("Initialized.")

    checkAuthorization()

    resetRetries()
    runShows()

    resetRetries()
    runMovies()

    Timber.d("Finished with success.")
    return 0
  }

  private suspend fun runShows() {
    try {
      importShowsRatings()
    } catch (error: Throwable) {
      if (retryCount.getAndIncrement() < MAX_IMPORT_RETRY_COUNT) {
        Timber.w("runShows HTTP failed. Will retry in $RETRY_DELAY_MS ms... $error")
        delay(RETRY_DELAY_MS)
        runShows()
      } else {
        throw error
      }
    }
  }

  private suspend fun runMovies() {
    if (!settingsRepository.isMoviesEnabled) {
      Timber.d("Movies are disabled. Exiting...")
      return
    }

    return try {
      importMoviesRatings()
    } catch (error: Throwable) {
      if (retryCount.getAndIncrement() < MAX_IMPORT_RETRY_COUNT) {
        Timber.w("runMovies HTTP failed. Will retry in $RETRY_DELAY_MS ms... $error")
        delay(RETRY_DELAY_MS)
        runMovies()
      } else {
        throw error
      }
    }
  }

  private suspend fun importShowsRatings() {
    Timber.d("Importing shows, seasons, episodes ratings...")
    ratingsRepository.shows.preloadRatings()
  }

  private suspend fun importMoviesRatings() {
    Timber.d("Importing movies ratings...")
    ratingsRepository.movies.preloadRatings()
  }
}
