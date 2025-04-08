package com.michaldrabik.ui_my_shows.watchlist.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.RatingsRepository
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.TraktRating
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
class WatchlistRatingsCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val ratingsRepository: RatingsRepository,
) {

  suspend fun loadRatings(): Map<IdTrakt, TraktRating?> =
    withContext(dispatchers.IO) {
      ratingsRepository.shows.loadShowsRatings().associateBy { it.idTrakt }
    }
}
