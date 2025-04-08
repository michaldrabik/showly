package com.michaldrabik.ui_movie.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.PinnedItemsRepository
import com.michaldrabik.repository.movies.MoviesRepository
import com.michaldrabik.ui_base.notifications.AnnouncementManager
import com.michaldrabik.ui_base.trakt.quicksync.QuickSyncManager
import com.michaldrabik.ui_model.Movie
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import javax.inject.Inject

@ViewModelScoped
class MovieDetailsMyMoviesCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val moviesRepository: MoviesRepository,
  private val pinnedItemsRepository: PinnedItemsRepository,
  private val quickSyncManager: QuickSyncManager,
  private val announcementManager: AnnouncementManager,
) {

  suspend fun getAllIds() =
    withContext(dispatchers.IO) {
      val (myMovies, watchlistMovies) = awaitAll(
        async { moviesRepository.myMovies.loadAllIds() },
        async { moviesRepository.watchlistMovies.loadAllIds() },
      )
      Pair(myMovies, watchlistMovies)
    }

  suspend fun getMyMovie(movie: Movie): Movie? =
    withContext(dispatchers.IO) {
      moviesRepository.myMovies.load(movie.ids.trakt)
    }

  suspend fun addToMyMovies(
    movie: Movie,
    customDate: ZonedDateTime?,
  ) {
    withContext(dispatchers.IO) {
      moviesRepository.myMovies.insert(movie.ids.trakt, customDate)
      quickSyncManager.scheduleMovies(listOf(movie.traktId), customDate)
      pinnedItemsRepository.removePinnedItem(movie)
      announcementManager.refreshMoviesAnnouncements()
    }
  }

  suspend fun removeFromMyMovies(movie: Movie) {
    withContext(dispatchers.IO) {
      moviesRepository.myMovies.delete(movie.ids.trakt)
      pinnedItemsRepository.removePinnedItem(movie)
      quickSyncManager.clearMovies(listOf(movie.traktId))
    }
  }
}
