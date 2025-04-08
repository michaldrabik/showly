package com.michaldrabik.repository.movies.ratings

import com.michaldrabik.common.extensions.dateIsoStringFromMillis
import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.common.extensions.toMillis
import com.michaldrabik.common.extensions.toUtcZone
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.Rating
import com.michaldrabik.data_remote.trakt.AuthorizedTraktRemoteDataSource
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.TraktRating
import java.time.temporal.ChronoUnit.SECONDS
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoviesRatingsRepository @Inject constructor(
  val external: MoviesExternalRatingsRepository,
  private val remoteSource: AuthorizedTraktRemoteDataSource,
  private val localSource: LocalDataSource,
  private val mappers: Mappers,
) {

  companion object {
    private const val TYPE_MOVIE = "movie"
  }

  suspend fun preloadRatings() {
    val remoteRatings = remoteSource.fetchMoviesRatings()
    val localRatings = localSource.ratings
      .getAllByType(TYPE_MOVIE)
      .map { mappers.userRatings.fromDatabase(it) }

    val entities = remoteRatings
      .filter { it.rated_at != null && it.movie.ids.trakt != null }
      .map { mappers.userRatings.toDatabaseMovie(it) }
      .filter { remoteRating ->
        val localRating = localRatings.find { remoteRating.idTrakt == it.idTrakt.id }
        if (localRating != null) {
          return@filter localRating.ratedAt
            .toUtcZone()
            .truncatedTo(SECONDS)
            .isBefore(remoteRating.ratedAt.toUtcZone().truncatedTo(SECONDS))
        }
        true
      }

    localSource.ratings.replaceAll(entities, TYPE_MOVIE)
  }

  suspend fun loadMoviesRatings(): List<TraktRating> {
    val ratings = localSource.ratings.getAllByType(TYPE_MOVIE)
    return ratings.map {
      mappers.userRatings.fromDatabase(it)
    }
  }

  suspend fun loadRatings(movies: List<Movie>): List<TraktRating> {
    val ratings = mutableListOf<Rating>()
    movies.chunked(250).forEach { chunk ->
      val items = localSource.ratings.getAllByType(chunk.map { it.traktId }, TYPE_MOVIE)
      ratings.addAll(items)
    }
    return ratings.map {
      mappers.userRatings.fromDatabase(it)
    }
  }

  suspend fun addRating(
    movie: Movie,
    rating: Int,
    withSync: Boolean,
  ) {
    val ratedAt = nowUtc()
    if (withSync) {
      remoteSource.postRating(
        mappers.movie.toNetwork(movie),
        rating,
        dateIsoStringFromMillis(ratedAt.toMillis()),
      )
    }
    val entity = mappers.userRatings.toDatabaseMovie(movie, rating, ratedAt)
    localSource.ratings.replace(entity)
  }

  suspend fun deleteRating(
    movie: Movie,
    withSync: Boolean,
  ) {
    if (withSync) {
      remoteSource.deleteRating(
        mappers.movie.toNetwork(movie),
      )
    }
    localSource.ratings.deleteByType(movie.traktId, TYPE_MOVIE)
  }
}
