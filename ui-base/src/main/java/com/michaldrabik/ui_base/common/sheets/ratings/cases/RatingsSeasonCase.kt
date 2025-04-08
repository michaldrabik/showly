package com.michaldrabik.ui_base.common.sheets.ratings.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.common.errors.ErrorHelper
import com.michaldrabik.common.errors.ShowlyError
import com.michaldrabik.repository.RatingsRepository
import com.michaldrabik.repository.UserTraktManager
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Ids
import com.michaldrabik.ui_model.Season
import com.michaldrabik.ui_model.TraktRating
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
class RatingsSeasonCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val userTraktManager: UserTraktManager,
  private val ratingsRepository: RatingsRepository,
) {

  companion object {
    private val RATING_VALID_RANGE = 1..10
  }

  suspend fun loadRating(idTrakt: IdTrakt): TraktRating =
    withContext(dispatchers.IO) {
      val season = Season.EMPTY.copy(ids = Ids.EMPTY.copy(trakt = idTrakt))
      try {
        val rating = ratingsRepository.shows.loadRatingsSeasons(listOf(season))
        rating.firstOrNull() ?: TraktRating.EMPTY
      } catch (error: Throwable) {
        handleError(error)
        TraktRating.EMPTY
      }
    }

  suspend fun saveRating(
    idTrakt: IdTrakt,
    rating: Int,
    seasonNumber: Int,
  ) = withContext(dispatchers.IO) {
    check(rating in RATING_VALID_RANGE)

    val season = Season.EMPTY.copy(
      ids = Ids.EMPTY.copy(trakt = idTrakt),
      number = seasonNumber,
    )

    try {
      ratingsRepository.shows.addRating(
        season = season,
        rating = rating,
        withSync = userTraktManager.isAuthorized(),
      )
    } catch (error: Throwable) {
      handleError(error)
    }
  }

  suspend fun deleteRating(idTrakt: IdTrakt) =
    withContext(dispatchers.IO) {
      val season = Season.EMPTY.copy(ids = Ids.EMPTY.copy(trakt = idTrakt))
      try {
        ratingsRepository.shows.deleteRating(
          season = season,
          withSync = userTraktManager.isAuthorized(),
        )
      } catch (error: Throwable) {
        handleError(error)
      }
    }

  private suspend fun handleError(error: Throwable) {
    val showlyError = ErrorHelper.parse(error)
    if (showlyError is ShowlyError.UnauthorizedError) {
      userTraktManager.revokeToken()
    }
    throw error
  }
}
