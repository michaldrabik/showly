package com.michaldrabik.ui_statistics_movies.cases

import BaseMockTest
import com.google.common.truth.Truth.assertThat
import com.michaldrabik.repository.RatingsRepository
import com.michaldrabik.repository.images.MovieImagesProvider
import com.michaldrabik.repository.movies.MoviesRepository
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Ids
import com.michaldrabik.ui_model.Image
import com.michaldrabik.ui_model.ImageType
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.TraktRating
import com.michaldrabik.ui_statistics_movies.views.ratings.recycler.StatisticsMoviesRatingItem
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

@Suppress("EXPERIMENTAL_API_USAGE")
class StatisticsMoviesLoadRatingsCaseTest : BaseMockTest() {

  @MockK lateinit var moviesRepository: MoviesRepository
  @MockK lateinit var ratingsRepository: RatingsRepository
  @MockK lateinit var movieImagesProvider: MovieImagesProvider

  private lateinit var SUT: StatisticsMoviesLoadRatingsCase

  @Before
  override fun setUp() {
    super.setUp()

    SUT = StatisticsMoviesLoadRatingsCase(
      moviesRepository,
      ratingsRepository,
      movieImagesProvider,
    )
  }

  @Test
  fun `Should load sorted ratings properly`() =
    runTest {
      val ratings = listOf(
        TraktRating.EMPTY.copy(IdTrakt(1), ratedAt = ZonedDateTime.of(2000, 3, 1, 1, 1, 1, 1, ZoneId.systemDefault())),
        TraktRating.EMPTY.copy(IdTrakt(2), ratedAt = ZonedDateTime.of(2001, 3, 1, 1, 1, 1, 1, ZoneId.systemDefault())),
        TraktRating.EMPTY.copy(IdTrakt(3), ratedAt = ZonedDateTime.of(2002, 3, 1, 1, 1, 1, 1, ZoneId.systemDefault())),
      )

      val movies = listOf(
        Movie.EMPTY.copy(Ids.EMPTY.copy(trakt = IdTrakt(1))),
        Movie.EMPTY.copy(Ids.EMPTY.copy(trakt = IdTrakt(2))),
        Movie.EMPTY.copy(Ids.EMPTY.copy(trakt = IdTrakt(3))),
        Movie.EMPTY.copy(Ids.EMPTY.copy(trakt = IdTrakt(4))),
        Movie.EMPTY.copy(Ids.EMPTY.copy(trakt = IdTrakt(5))),
      )

      val image = Image.createUnknown(ImageType.POSTER)

      coEvery { ratingsRepository.movies.loadMoviesRatings() } returns ratings
      coEvery { moviesRepository.myMovies.loadAll(any()) } returns movies
      coEvery { movieImagesProvider.findCachedImage(any(), any()) } returns image

      val result = SUT.loadRatings()

      assertThat(result).hasSize(3)
      assertThat(result).containsExactly(
        StatisticsMoviesRatingItem(movies[2], image, false, ratings[2]),
        StatisticsMoviesRatingItem(movies[1], image, false, ratings[1]),
        StatisticsMoviesRatingItem(movies[0], image, false, ratings[0]),
      )
    }
}
