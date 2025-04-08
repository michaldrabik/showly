package com.michaldrabik.repository.mappers

import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.data_local.database.model.Rating
import com.michaldrabik.data_remote.trakt.model.RatingResultEpisode
import com.michaldrabik.data_remote.trakt.model.RatingResultMovie
import com.michaldrabik.data_remote.trakt.model.RatingResultSeason
import com.michaldrabik.data_remote.trakt.model.RatingResultShow
import com.michaldrabik.ui_model.Episode
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.Season
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_model.TraktRating
import java.time.ZonedDateTime
import javax.inject.Inject

class UserRatingsMapper @Inject constructor() {

  fun fromDatabase(entity: Rating) =
    TraktRating(
      idTrakt = IdTrakt(entity.idTrakt),
      rating = entity.rating,
      ratedAt = entity.ratedAt,
    )

  fun toDatabaseMovie(rating: RatingResultMovie) =
    Rating(
      idTrakt = rating.movie.ids.trakt!!,
      type = "movie",
      rating = rating.rating,
      seasonNumber = null,
      episodeNumber = null,
      ratedAt = ZonedDateTime.parse(rating.rated_at),
      createdAt = nowUtc(),
      updatedAt = nowUtc(),
    )

  fun toDatabaseMovie(
    movie: Movie,
    rating: Int,
    ratedAt: ZonedDateTime,
  ) = Rating(
    idTrakt = movie.traktId,
    type = "movie",
    rating = rating,
    seasonNumber = null,
    episodeNumber = null,
    ratedAt = ratedAt,
    createdAt = nowUtc(),
    updatedAt = nowUtc(),
  )

  fun toDatabaseShow(rating: RatingResultShow) =
    Rating(
      idTrakt = rating.show.ids.trakt!!,
      type = "show",
      rating = rating.rating,
      seasonNumber = null,
      episodeNumber = null,
      ratedAt = ZonedDateTime.parse(rating.rated_at),
      createdAt = nowUtc(),
      updatedAt = nowUtc(),
    )

  fun toDatabaseShow(
    show: Show,
    rating: Int,
    ratedAt: ZonedDateTime,
  ) = Rating(
    idTrakt = show.traktId,
    type = "show",
    rating = rating,
    seasonNumber = null,
    episodeNumber = null,
    ratedAt = ratedAt,
    createdAt = nowUtc(),
    updatedAt = nowUtc(),
  )

  fun toDatabaseEpisode(rating: RatingResultEpisode) =
    Rating(
      idTrakt = rating.episode.ids.trakt!!,
      type = "episode",
      rating = rating.rating,
      seasonNumber = rating.episode.season,
      episodeNumber = rating.episode.number,
      ratedAt = ZonedDateTime.parse(rating.rated_at),
      createdAt = nowUtc(),
      updatedAt = nowUtc(),
    )

  fun toDatabaseEpisode(
    episode: Episode,
    rating: Int,
    ratedAt: ZonedDateTime,
  ) = Rating(
    idTrakt = episode.ids.trakt.id,
    type = "episode",
    rating = rating,
    seasonNumber = episode.season,
    episodeNumber = episode.number,
    ratedAt = ratedAt,
    createdAt = nowUtc(),
    updatedAt = nowUtc(),
  )

  fun toDatabaseSeason(rating: RatingResultSeason) =
    Rating(
      idTrakt = rating.season.ids.trakt!!,
      type = "season",
      rating = rating.rating,
      seasonNumber = rating.season.number,
      episodeNumber = null,
      ratedAt = ZonedDateTime.parse(rating.rated_at),
      createdAt = nowUtc(),
      updatedAt = nowUtc(),
    )

  fun toDatabaseSeason(
    season: Season,
    rating: Int,
    ratedAt: ZonedDateTime,
  ) = Rating(
    idTrakt = season.ids.trakt.id,
    type = "season",
    rating = rating,
    seasonNumber = season.number,
    episodeNumber = null,
    ratedAt = ratedAt,
    createdAt = nowUtc(),
    updatedAt = nowUtc(),
  )
}
