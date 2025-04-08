package com.michaldrabik.repository

import com.michaldrabik.repository.movies.ratings.MoviesRatingsRepository
import com.michaldrabik.repository.shows.ratings.ShowsRatingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RatingsRepository @Inject constructor(
  val shows: ShowsRatingsRepository,
  val movies: MoviesRatingsRepository,
)
