package com.michaldrabik.ui_statistics.cases

import com.michaldrabik.repository.RatingsRepository
import com.michaldrabik.repository.images.ShowImagesProvider
import com.michaldrabik.repository.shows.ShowsRepository
import com.michaldrabik.ui_model.ImageType
import com.michaldrabik.ui_statistics.views.ratings.recycler.StatisticsRatingItem
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject

@ViewModelScoped
class StatisticsLoadRatingsCase @Inject constructor(
  private val showsRepository: ShowsRepository,
  private val ratingsRepository: RatingsRepository,
  private val imagesProvider: ShowImagesProvider,
) {

  companion object {
    private const val LIMIT = 25
  }

  suspend fun loadRatings(): List<StatisticsRatingItem> {
    val ratings = ratingsRepository.shows.loadShowsRatings()

    val ratingsIds = ratings.map { it.idTrakt }
    val myShows = showsRepository.myShows.loadAll(ratingsIds)

    return ratings
      .filter { rating -> myShows.any { it.traktId == rating.idTrakt.id } }
      .take(LIMIT)
      .map { rating ->
        val show = myShows.first { it.traktId == rating.idTrakt.id }
        StatisticsRatingItem(
          isLoading = false,
          show = show,
          image = imagesProvider.findCachedImage(show, ImageType.POSTER),
          rating = rating,
        )
      }.sortedByDescending { it.rating.ratedAt }
  }
}
