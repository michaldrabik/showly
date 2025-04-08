package com.michaldrabik.ui_base.trakt.exports

import com.michaldrabik.common.extensions.dateIsoStringFromMillis
import com.michaldrabik.common.extensions.toMillis
import com.michaldrabik.common.extensions.toUtcDateTime
import com.michaldrabik.common.extensions.toUtcZone
import com.michaldrabik.data_remote.trakt.AuthorizedTraktRemoteDataSource
import com.michaldrabik.data_remote.trakt.model.request.RatingRequest
import com.michaldrabik.data_remote.trakt.model.request.RatingRequestIds
import com.michaldrabik.data_remote.trakt.model.request.RatingRequestValue
import com.michaldrabik.repository.RatingsRepository
import com.michaldrabik.repository.UserTraktManager
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_base.trakt.TraktSyncRunner
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import timber.log.Timber
import java.time.temporal.ChronoUnit.SECONDS
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TraktExportRatingsRunner @Inject constructor(
  private val remoteSource: AuthorizedTraktRemoteDataSource,
  private val ratingsRepository: RatingsRepository,
  private val settingsRepository: SettingsRepository,
  userTraktManager: UserTraktManager,
) : TraktSyncRunner(userTraktManager) {

  override suspend fun run(): Int {
    Timber.d("Initialized.")

    checkAuthorization()
    resetRetries()
    runExport()

    Timber.d("Finished with success.")
    return 0
  }

  private suspend fun runExport() {
    try {
      exportRatings()
    } catch (error: Throwable) {
      rethrowCancellation(error)
      if (retryCount.getAndIncrement() < MAX_EXPORT_RETRY_COUNT) {
        Timber.w("exportRatings failed. Will retry in $RETRY_DELAY_MS ms... $error")
        delay(RETRY_DELAY_MS)
        runExport()
      } else {
        throw error
      }
    }
  }

  private suspend fun exportRatings() =
    coroutineScope {
      Timber.d("Exporting ratings...")
      val isMoviesEnables = settingsRepository.isMoviesEnabled

      val localShowsRatings = ratingsRepository.shows.loadShowsRatings()
      val localSeasonsRatings = ratingsRepository.shows.loadSeasonsRatings()
      val localEpisodesRatings = ratingsRepository.shows.loadEpisodesRatings()
      val localMoviesRatings = if (isMoviesEnables) ratingsRepository.movies.loadMoviesRatings() else emptyList()

      if (localShowsRatings.isEmpty() &&
        localSeasonsRatings.isEmpty() &&
        localEpisodesRatings.isEmpty() &&
        localMoviesRatings.isEmpty()
      ) {
        Timber.d("Nothing to export. All local ratings are empty.")
        return@coroutineScope
      }

      val remoteShowsRatingsAsync = async { remoteSource.fetchShowsRatings() }
      val remoteSeasonsRatingsAsync = async { remoteSource.fetchSeasonsRatings() }
      val remoteEpisodesRatingsAsync = async { remoteSource.fetchEpisodesRatings() }
      val remoteMoviesRatingsAsync = async { if (isMoviesEnables) remoteSource.fetchMoviesRatings() else emptyList() }

      val remoteShowsRatings = remoteShowsRatingsAsync.await()
      val remoteSeasonsRatings = remoteSeasonsRatingsAsync.await()
      val remoteEpisodesRatings = remoteEpisodesRatingsAsync.await()
      val remoteMoviesRatings = remoteMoviesRatingsAsync.await()

      val showsRatingsToExport = localShowsRatings
        .filter { localRating ->
          val remoteRating = remoteShowsRatings.find { it.show.ids.trakt == localRating.idTrakt.id }
          if (remoteRating == null) {
            return@filter true
          }

          val localTime = localRating.ratedAt.toUtcZone().truncatedTo(SECONDS)
          val remoteTime = remoteRating.rated_at.toUtcDateTime()?.truncatedTo(SECONDS) ?: return@filter true
          return@filter localTime.isAfter(remoteTime)
        }.toMutableList()

      val seasonsRatingsToExport = localSeasonsRatings
        .filter { localRating ->
          val remoteRating = remoteSeasonsRatings.find { it.season.ids.trakt == localRating.idTrakt }
          if (remoteRating == null) {
            return@filter true
          }

          val localTime = localRating.ratedAt.toUtcZone().truncatedTo(SECONDS)
          val remoteTime = remoteRating.rated_at.toUtcDateTime()?.truncatedTo(SECONDS) ?: return@filter true
          return@filter localTime.isAfter(remoteTime)
        }.toMutableList()

      val episodesRatingsToExport = localEpisodesRatings
        .filter { localRating ->
          val remoteRating = remoteEpisodesRatings.find { it.episode.ids.trakt == localRating.idTrakt }
          if (remoteRating == null) {
            return@filter true
          }

          val localTime = localRating.ratedAt.toUtcZone().truncatedTo(SECONDS)
          val remoteTime = remoteRating.rated_at.toUtcDateTime()?.truncatedTo(SECONDS) ?: return@filter true
          return@filter localTime.isAfter(remoteTime)
        }.toMutableList()

      val moviesRatingsToExport = localMoviesRatings
        .filter { localRating ->
          val remoteRating = remoteMoviesRatings.find { it.movie.ids.trakt == localRating.idTrakt.id }
          if (remoteRating == null) {
            return@filter true
          }

          val localTime = localRating.ratedAt.toUtcZone().truncatedTo(SECONDS)
          val remoteTime = remoteRating.rated_at.toUtcDateTime()?.truncatedTo(SECONDS) ?: return@filter true
          return@filter localTime.isAfter(remoteTime)
        }.toMutableList()

      while (true) {
        val showsChunk = showsRatingsToExport.take(200)
        val seasonsChunk = seasonsRatingsToExport.take(200)
        val episodesChunk = episodesRatingsToExport.take(200)
        val moviesChunk = moviesRatingsToExport.take(200)

        if (showsChunk.isEmpty() &&
          seasonsChunk.isEmpty() &&
          episodesChunk.isEmpty() &&
          moviesChunk.isEmpty()
        ) {
          Timber.d("No more ratings chunks. Breaking.")
          break
        }

        val request = RatingRequest(
          shows = showsChunk.map {
            RatingRequestValue(
              rating = it.rating,
              rated_at = dateIsoStringFromMillis(it.ratedAt.toUtcZone().toMillis()),
              ids = RatingRequestIds(it.idTrakt.id),
            )
          },
          seasons = seasonsChunk.map {
            RatingRequestValue(
              rating = it.rating,
              rated_at = dateIsoStringFromMillis(it.ratedAt.toUtcZone().toMillis()),
              ids = RatingRequestIds(it.idTrakt),
            )
          },
          episodes = episodesChunk.map {
            RatingRequestValue(
              rating = it.rating,
              rated_at = dateIsoStringFromMillis(it.ratedAt.toUtcZone().toMillis()),
              ids = RatingRequestIds(it.idTrakt),
            )
          },
          movies = moviesChunk.map {
            RatingRequestValue(
              rating = it.rating,
              rated_at = dateIsoStringFromMillis(it.ratedAt.toUtcZone().toMillis()),
              ids = RatingRequestIds(it.idTrakt.id),
            )
          },
        )

        Timber.d(
          "Exporting chunk of " +
            "${showsChunk.size} shows, " +
            "${seasonsChunk.size} seasons, " +
            "${episodesChunk.size} episodes, " +
            "${moviesChunk.size} movies...",
        )

        remoteSource.postRatings(request)

        showsRatingsToExport.removeAll(showsChunk)
        seasonsRatingsToExport.removeAll(seasonsChunk)
        episodesRatingsToExport.removeAll(episodesChunk)
        moviesRatingsToExport.removeAll(moviesChunk)

        delay(TRAKT_LIMIT_DELAY_MS)
      }
    }
}
