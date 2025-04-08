package com.michaldrabik.repository.shows.ratings

import com.michaldrabik.common.extensions.dateIsoStringFromMillis
import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.common.extensions.toMillis
import com.michaldrabik.common.extensions.toUtcZone
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.Rating
import com.michaldrabik.data_remote.trakt.AuthorizedTraktRemoteDataSource
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.ui_model.Episode
import com.michaldrabik.ui_model.Season
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_model.TraktRating
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import java.time.temporal.ChronoUnit.SECONDS
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShowsRatingsRepository @Inject constructor(
  val external: ShowsExternalRatingsRepository,
  private val remoteSource: AuthorizedTraktRemoteDataSource,
  private val localSource: LocalDataSource,
  private val mappers: Mappers,
) {

  companion object {
    private const val TYPE_SHOW = "show"
    private const val TYPE_EPISODE = "episode"
    private const val TYPE_SEASON = "season"
    private const val CHUNK_SIZE = 250
  }

  suspend fun preloadRatings() =
    supervisorScope {
      suspend fun preloadShowsRatings() {
        val remoteRatings = remoteSource.fetchShowsRatings()
        val localRatings = localSource.ratings
          .getAllByType(TYPE_SHOW)
          .map { mappers.userRatings.fromDatabase(it) }

        val entities = remoteRatings
          .filter { it.rated_at != null && it.show.ids.trakt != null }
          .map { mappers.userRatings.toDatabaseShow(it) }
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

        localSource.ratings.replaceAll(entities, TYPE_SHOW)
      }

      suspend fun preloadEpisodesRatings() {
        val remoteRatings = remoteSource.fetchEpisodesRatings()
        val localRatings = localSource.ratings
          .getAllByType(TYPE_EPISODE)
          .map { mappers.userRatings.fromDatabase(it) }

        val entities = remoteRatings
          .filter { it.rated_at != null && it.episode.ids.trakt != null }
          .map { mappers.userRatings.toDatabaseEpisode(it) }
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

        localSource.ratings.replaceAll(entities, TYPE_EPISODE)
      }

      suspend fun preloadSeasonsRatings() {
        val remoteRatings = remoteSource.fetchSeasonsRatings()
        val localRatings = localSource.ratings
          .getAllByType(TYPE_SEASON)
          .map { mappers.userRatings.fromDatabase(it) }

        val entities = remoteRatings
          .filter { it.rated_at != null && it.season.ids.trakt != null }
          .map { mappers.userRatings.toDatabaseSeason(it) }
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

        localSource.ratings.replaceAll(entities, TYPE_SEASON)
      }

      val errorHandler = CoroutineExceptionHandler { _, _ ->
        Timber.e("Failed to preload some of ratings.")
      }

      launch(errorHandler) { preloadShowsRatings() }
      launch(errorHandler) { preloadEpisodesRatings() }
      launch(errorHandler) { preloadSeasonsRatings() }
    }

  suspend fun loadShowsRatings(): List<TraktRating> {
    val ratings = localSource.ratings.getAllByType(TYPE_SHOW)
    return ratings.map {
      mappers.userRatings.fromDatabase(it)
    }
  }

  suspend fun loadSeasonsRatings(): List<Rating> {
    val ratings = localSource.ratings.getAllByType(TYPE_SEASON)
    return ratings
  }

  suspend fun loadEpisodesRatings(): List<Rating> {
    val ratings = localSource.ratings.getAllByType(TYPE_EPISODE)
    return ratings
  }

  suspend fun loadRatings(shows: List<Show>): List<TraktRating> {
    val ratings = mutableListOf<Rating>()
    shows.chunked(CHUNK_SIZE).forEach { chunk ->
      val items = localSource.ratings.getAllByType(chunk.map { it.traktId }, TYPE_SHOW)
      ratings.addAll(items)
    }
    return ratings.map {
      mappers.userRatings.fromDatabase(it)
    }
  }

  suspend fun loadRatingsSeasons(seasons: List<Season>): List<TraktRating> {
    val ratings = mutableListOf<Rating>()
    seasons.chunked(CHUNK_SIZE).forEach { chunk ->
      val items = localSource.ratings.getAllByType(chunk.map { it.ids.trakt.id }, TYPE_SEASON)
      ratings.addAll(items)
    }
    return ratings.map {
      mappers.userRatings.fromDatabase(it)
    }
  }

  suspend fun loadRating(episode: Episode): TraktRating? {
    val rating = localSource.ratings.getAllByType(listOf(episode.ids.trakt.id), TYPE_EPISODE)
    return rating.firstOrNull()?.let {
      mappers.userRatings.fromDatabase(it)
    }
  }

  suspend fun loadRating(season: Season): TraktRating? {
    val rating = localSource.ratings.getAllByType(listOf(season.ids.trakt.id), TYPE_SEASON)
    return rating.firstOrNull()?.let {
      mappers.userRatings.fromDatabase(it)
    }
  }

  suspend fun addRating(
    show: Show,
    rating: Int,
    withSync: Boolean,
  ) {
    val ratedAt = nowUtc()
    if (withSync) {
      remoteSource.postRating(
        mappers.show.toNetwork(show),
        rating,
        dateIsoStringFromMillis(ratedAt.toMillis()),
      )
    }
    val entity = mappers.userRatings.toDatabaseShow(show, rating, ratedAt)
    localSource.ratings.replace(entity)
  }

  suspend fun addRating(
    episode: Episode,
    rating: Int,
    withSync: Boolean,
  ) {
    val ratedAt = nowUtc()
    if (withSync) {
      remoteSource.postRating(
        mappers.episode.toNetwork(episode),
        rating,
        dateIsoStringFromMillis(ratedAt.toMillis()),
      )
    }
    val entity = mappers.userRatings.toDatabaseEpisode(episode, rating, ratedAt)
    localSource.ratings.replace(entity)
  }

  suspend fun addRating(
    season: Season,
    rating: Int,
    withSync: Boolean,
  ) {
    val ratedAt = nowUtc()
    if (withSync) {
      remoteSource.postRating(
        mappers.season.toNetwork(season),
        rating,
        dateIsoStringFromMillis(ratedAt.toMillis()),
      )
    }
    val entity = mappers.userRatings.toDatabaseSeason(season, rating, ratedAt)
    localSource.ratings.replace(entity)
  }

  suspend fun deleteRating(
    show: Show,
    withSync: Boolean,
  ) {
    if (withSync) {
      remoteSource.deleteRating(
        mappers.show.toNetwork(show),
      )
    }
    localSource.ratings.deleteByType(show.traktId, TYPE_SHOW)
  }

  suspend fun deleteRating(
    episode: Episode,
    withSync: Boolean,
  ) {
    if (withSync) {
      remoteSource.deleteRating(
        mappers.episode.toNetwork(episode),
      )
    }
    localSource.ratings.deleteByType(episode.ids.trakt.id, TYPE_EPISODE)
  }

  suspend fun deleteRating(
    season: Season,
    withSync: Boolean,
  ) {
    if (withSync) {
      remoteSource.deleteRating(
        mappers.season.toNetwork(season),
      )
    }
    localSource.ratings.deleteByType(season.ids.trakt.id, TYPE_SEASON)
  }
}
