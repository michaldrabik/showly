package com.michaldrabik.ui_base.trakt.imports

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.common.extensions.toUtcDateTime
import com.michaldrabik.common.extensions.toZonedDateTime
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.ArchiveMovie
import com.michaldrabik.data_local.database.model.ArchiveShow
import com.michaldrabik.data_local.database.model.Episode
import com.michaldrabik.data_local.database.model.MyMovie
import com.michaldrabik.data_local.database.model.MyShow
import com.michaldrabik.data_local.database.model.Season
import com.michaldrabik.data_local.utilities.TransactionsProvider
import com.michaldrabik.data_remote.trakt.AuthorizedTraktRemoteDataSource
import com.michaldrabik.data_remote.trakt.TraktRemoteDataSource
import com.michaldrabik.data_remote.trakt.model.SyncActivity
import com.michaldrabik.data_remote.trakt.model.SyncItem
import com.michaldrabik.repository.UserTraktManager
import com.michaldrabik.repository.images.MovieImagesProvider
import com.michaldrabik.repository.images.ShowImagesProvider
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_base.Logger
import com.michaldrabik.ui_base.trakt.TraktSyncRunner
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.ImageType.FANART
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.Show
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNEL_CAPACITY = 10

@Singleton
class TraktImportWatchedRunner @Inject constructor(
  private val remoteAuthSource: AuthorizedTraktRemoteDataSource,
  private val remoteSource: TraktRemoteDataSource,
  private val localSource: LocalDataSource,
  private val mappers: Mappers,
  private val transactions: TransactionsProvider,
  private val showImagesProvider: ShowImagesProvider,
  private val movieImagesProvider: MovieImagesProvider,
  private val settingsRepository: SettingsRepository,
  private val dispatchers: CoroutineDispatchers,
  userTraktManager: UserTraktManager,
) : TraktSyncRunner(userTraktManager) {

  override suspend fun run(): Int {
    Timber.d("Initialized.")

    checkAuthorization()
    val activity = runSyncActivity()

    resetRetries()
    runShows(activity)

    resetRetries()
    runMovies(activity)

    Timber.d("Finished with success.")
    return 0
  }

  private suspend fun runSyncActivity(): SyncActivity =
    try {
      remoteAuthSource.fetchSyncActivity()
    } catch (error: Throwable) {
      if (retryCount.getAndIncrement() < MAX_IMPORT_RETRY_COUNT) {
        Timber.w("checkSyncActivity HTTP failed. Will retry in $RETRY_DELAY_MS ms... $error")
        delay(RETRY_DELAY_MS)
        runSyncActivity()
      } else {
        throw error
      }
    }

  private suspend fun runShows(activity: SyncActivity) {
    try {
      importWatchedShows(activity)
    } catch (error: Throwable) {
      if (retryCount.getAndIncrement() < MAX_IMPORT_RETRY_COUNT) {
        Timber.w("runShows HTTP failed. Will retry in $RETRY_DELAY_MS ms... $error")
        delay(RETRY_DELAY_MS)
        runShows(activity)
      } else {
        throw error
      }
    }
  }

  private suspend fun importWatchedShows(syncActivity: SyncActivity) {
    val mutex = Mutex()
    val channel = Channel<Unit>(CHANNEL_CAPACITY)
    withContext(dispatchers.IO) {
      Timber.d("Importing watched shows...")

      val episodesWatchedAt = syncActivity.episodes.watched_at.toUtcDateTime()!!
      val showsHiddenAt = syncActivity.shows.hidden_at.toUtcDateTime()!!
      val localEpisodesWatchedAt = settingsRepository.sync.activityEpisodesWatchedAt.toUtcDateTime()
      val localShowsHiddenAt = settingsRepository.sync.activityShowsHiddenAt.toUtcDateTime()

      val episodesNeeded = localEpisodesWatchedAt == null || localEpisodesWatchedAt.isBefore(episodesWatchedAt)
      val showsNeeded = localShowsHiddenAt == null || localShowsHiddenAt.isBefore(showsHiddenAt)

      if (!episodesNeeded && !showsNeeded) {
        Timber.d("No changes in watched sync activity. Skipping...")
        return@withContext 0
      }

      val syncResults = remoteAuthSource
        .fetchSyncWatchedShows("full")
        .distinctBy { it.show?.ids?.trakt }

      Timber.d("Importing hidden shows...")
      remoteAuthSource
        .fetchHiddenShows()
        .forEach { item ->
          item.show?.let {
            val show = mappers.show.fromNetwork(it)
            val dbShow = mappers.show.toDatabase(show)
            val archiveShow = ArchiveShow.fromTraktId(
              traktId = show.traktId,
              createdAt = item.hiddenAtMillis(),
            )
            transactions.withTransaction {
              with(localSource) {
                shows.upsert(listOf(dbShow))
                archiveShows.insert(archiveShow)
                myShows.deleteById(show.traktId)
                watchlistShows.deleteById(show.traktId)
              }
            }
          }
        }

      val myShowsIds = localSource.myShows.getAllTraktIds()
      val watchlistShowsIds = localSource.watchlistShows.getAllTraktIds()
      val hiddenShowsIds = localSource.archiveShows.getAllTraktIds()
      val traktSyncLogs = localSource.traktSyncLog.getAllShows()

      val results = mutableListOf<Deferred<Unit>>()
      val progressTitles = mutableSetOf<String?>()

      syncResults
        .forEachIndexed { _, syncItem ->
          channel.send(Unit)
          val result = async {
            val showTitle = syncItem.requireShow().title
            try {
              Timber.d("Processing \'$showTitle\'...")
              mutex.withLock {
                progressListener?.invoke(progressTitles.joinToString("\n"))
                progressTitles.add(showTitle)
              }

              val log = traktSyncLogs.firstOrNull { it.idTrakt == syncItem.show?.ids?.trakt }
              if (syncItem.lastUpdateMillis() == (log?.syncedAt ?: 0)) {
                Timber.d("Nothing changed in \'$showTitle\'. Skipping...")
                return@async
              }

              val showId = syncItem.getTraktId()!!
              val (seasons, episodes) = loadSeasonsEpisodes(showId, syncItem)

              transactions.withTransaction {
                val isMyShow = showId in myShowsIds
                val isWatchlistShow = showId in watchlistShowsIds
                val isHiddenShow = showId in hiddenShowsIds

                if (!isMyShow && !isHiddenShow) {
                  val show = mappers.show.fromNetwork(syncItem.requireShow())
                  val showDb = mappers.show.toDatabase(show)

                  val myShow = MyShow.fromTraktId(
                    traktId = showDb.idTrakt,
                    createdAt = syncItem.lastWatchedMillis(),
                    updatedAt = syncItem.lastWatchedMillis(),
                    watchedAt = syncItem.lastWatchedMillis(),
                  )
                  localSource.shows.upsert(listOf(showDb))
                  localSource.myShows.insert(listOf(myShow))

                  loadImage(show)

                  if (isWatchlistShow) {
                    localSource.watchlistShows.deleteById(showId)
                  }
                }

                localSource.seasons.upsert(seasons)
                localSource.episodes.upsert(episodes)

                localSource.myShows.updateWatchedAt(showId, syncItem.lastWatchedMillis())
                localSource.traktSyncLog.upsertShow(showId, syncItem.lastUpdateMillis())
              }
            } catch (error: Throwable) {
              if (error !is CancellationException) {
                Timber.w("Processing \'${syncItem.show!!.title}\' failed. Skipping...")
                Logger.record(error, "TraktImportWatchedRunner::importWatchedShows()")
              }
              rethrowCancellation(error)
            } finally {
              channel.receive()
              mutex.withLock {
                progressTitles.remove(showTitle)
              }
            }
          }
          results.add(result)
        }

      results.awaitAll()

      settingsRepository.sync.activityEpisodesWatchedAt = syncActivity.episodes.watched_at
      settingsRepository.sync.activityShowsHiddenAt = syncActivity.shows.hidden_at
    }
  }

  private suspend fun loadSeasonsEpisodes(
    showId: Long,
    syncItem: SyncItem,
  ): Pair<List<Season>, List<Episode>> {
    val remoteSeasons = remoteSource.fetchSeasons(showId)
    val localSeasonsIds = localSource.seasons.getAllWatchedIdsForShows(listOf(showId))
    val localEpisodesIds = localSource.episodes.getAllWatchedIdsForShows(listOf(showId))

    val seasons = remoteSeasons
      .filterNot { localSeasonsIds.contains(it.ids?.trakt) }
      .map { mappers.season.fromNetwork(it) }
      .map { remoteSeason ->
        val isWatched = syncItem.seasons?.any {
          it.number == remoteSeason.number && it.episodes?.size == remoteSeason.episodes.size
        } ?: false
        mappers.season.toDatabase(remoteSeason, IdTrakt(showId), isWatched)
      }

    val episodes = remoteSeasons.flatMap { season ->
      season.episodes
        ?.filterNot { localEpisodesIds.contains(it.ids?.trakt) }
        ?.map { episode ->
          val syncEpisode = syncItem.seasons
            ?.find { it.number == season.number }
            ?.episodes
            ?.find { it.number == episode.number }

          val watchedAt = syncEpisode?.last_watched_at?.toZonedDateTime()
          val exportedAt = syncEpisode?.let {
            it.last_watched_at?.toZonedDateTime() ?: nowUtc()
          }

          val seasonDb = mappers.season.fromNetwork(season)
          val episodeDb = mappers.episode.fromNetwork(episode)
          mappers.episode.toDatabase(
            showId = IdTrakt(showId),
            season = seasonDb,
            episode = episodeDb,
            isWatched = syncEpisode != null,
            lastExportedAt = exportedAt,
            lastWatchedAt = watchedAt,
          )
        } ?: emptyList()
    }

    return Pair(seasons, episodes)
  }

  private suspend fun runMovies(activity: SyncActivity) {
    if (!settingsRepository.isMoviesEnabled) {
      Timber.d("Movies are disabled. Exiting...")
      return
    }
    try {
      importWatchedMovies(activity)
    } catch (error: Throwable) {
      if (retryCount.getAndIncrement() < MAX_IMPORT_RETRY_COUNT) {
        Timber.w("runMovies HTTP failed. Will retry in $RETRY_DELAY_MS ms... $error")
        delay(RETRY_DELAY_MS)
        runMovies(activity)
      } else {
        throw error
      }
    }
  }

  private suspend fun importWatchedMovies(syncActivity: SyncActivity) {
    val mutex = Mutex()
    val channel = Channel<Unit>(CHANNEL_CAPACITY)
    withContext(dispatchers.IO) {
      Timber.d("Importing watched movies...")

      val moviesWatchedAt = syncActivity.movies.watched_at.toUtcDateTime()!!
      val moviesHiddenAt = syncActivity.movies.hidden_at.toUtcDateTime()!!
      val localMoviesWatchedAt = settingsRepository.sync.activityMoviesWatchedAt.toUtcDateTime()
      val localMoviesHiddenAt = settingsRepository.sync.activityMoviesHiddenAt.toUtcDateTime()

      val watchedAtNeeded = localMoviesWatchedAt == null || localMoviesWatchedAt.isBefore(moviesWatchedAt)
      val hiddenAtNeeded = localMoviesHiddenAt == null || localMoviesHiddenAt.isBefore(moviesHiddenAt)

      if (!watchedAtNeeded && !hiddenAtNeeded) {
        Timber.d("No changes in sync activity. Skipping...")
        return@withContext
      }

      val syncItems = remoteAuthSource
        .fetchSyncWatchedMovies("full")
        .filter { it.movie != null }
        .distinctBy { it.movie?.ids?.trakt }

      Timber.d("Importing hidden movies...")

      val hiddenMovies = remoteAuthSource.fetchHiddenMovies()
      hiddenMovies.forEach { hiddenMovie ->
        hiddenMovie.movie?.let {
          val movie = mappers.movie.fromNetwork(it)
          val dbMovie = mappers.movie.toDatabase(movie)
          val archiveMovie = ArchiveMovie.fromTraktId(movie.traktId, hiddenMovie.hiddenAtMillis())
          transactions.withTransaction {
            with(localSource) {
              movies.upsert(listOf(dbMovie))
              archiveMovies.insert(archiveMovie)
              myMovies.deleteById(movie.traktId)
              watchlistMovies.deleteById(movie.traktId)
            }
          }
        }
      }

      val myMoviesIds = localSource.myMovies.getAllTraktIds()
      val watchlistMoviesIds = localSource.watchlistMovies.getAllTraktIds()
      val hiddenMoviesIds = localSource.archiveMovies.getAllTraktIds()

      val results = mutableListOf<Deferred<Unit>>()
      val progressTitles = mutableSetOf<String?>()

      syncItems
        .forEachIndexed { _, item ->
          channel.send(Unit)
          val result = async {
            val movieTitle = item.requireMovie().title
            try {
              Timber.d("Processing \'$movieTitle\'...")
              mutex.withLock {
                progressListener?.invoke(progressTitles.joinToString("\n"))
                progressTitles.add(movieTitle)
              }

              transactions.withTransaction {
                val movieId = item.getTraktId()!!
                if (movieId !in myMoviesIds && movieId !in hiddenMoviesIds) {
                  val movie = mappers.movie.fromNetwork(item.requireMovie())
                  val movieDb = mappers.movie.toDatabase(movie)

                  val myMovie = MyMovie.fromTraktId(movieDb.idTrakt, item.lastWatchedMillis())
                  localSource.movies.upsert(listOf(movieDb))
                  localSource.myMovies.insert(listOf(myMovie))

                  loadImage(movie)

                  if (movieId in watchlistMoviesIds) {
                    localSource.watchlistMovies.deleteById(movieId)
                  }
                }
              }
            } catch (error: Throwable) {
              if (error !is CancellationException) {
                Timber.w("Processing \'$movieTitle\' failed. Skipping...")
                Logger.record(error, "TraktImportWatchedRunner::importWatchedMovies()")
              }
              rethrowCancellation(error)
            } finally {
              channel.receive()
              mutex.withLock {
                progressTitles.remove(movieTitle)
              }
            }
          }
          results.add(result)
        }

      results.awaitAll()

      settingsRepository.sync.activityMoviesWatchedAt = syncActivity.movies.watched_at
      settingsRepository.sync.activityMoviesHiddenAt = syncActivity.movies.hidden_at
    }
  }

  private suspend fun loadImage(show: Show) {
    try {
      showImagesProvider.loadRemoteImage(show, FANART)
    } catch (error: Throwable) {
      Timber.w(error)
      rethrowCancellation(error)
      // Ignore image for now. It will be fetched later if needed.
    }
  }

  private suspend fun loadImage(movie: Movie) {
    try {
      movieImagesProvider.loadRemoteImage(movie, FANART)
    } catch (error: Throwable) {
      Timber.w(error)
      rethrowCancellation(error)
      // Ignore image for now. It will be fetched later if needed.
    }
  }
}
