package com.michaldrabik.data_remote.trakt.api

import com.michaldrabik.data_remote.trakt.AuthorizedTraktRemoteDataSource
import com.michaldrabik.data_remote.trakt.api.service.TraktCommentsService
import com.michaldrabik.data_remote.trakt.api.service.TraktSyncService
import com.michaldrabik.data_remote.trakt.api.service.TraktUsersService
import com.michaldrabik.data_remote.trakt.model.CustomList
import com.michaldrabik.data_remote.trakt.model.Episode
import com.michaldrabik.data_remote.trakt.model.HiddenItem
import com.michaldrabik.data_remote.trakt.model.Movie
import com.michaldrabik.data_remote.trakt.model.Season
import com.michaldrabik.data_remote.trakt.model.Show
import com.michaldrabik.data_remote.trakt.model.SyncActivity
import com.michaldrabik.data_remote.trakt.model.SyncExportItem
import com.michaldrabik.data_remote.trakt.model.SyncExportRequest
import com.michaldrabik.data_remote.trakt.model.SyncHistoryItem
import com.michaldrabik.data_remote.trakt.model.SyncItem
import com.michaldrabik.data_remote.trakt.model.request.CommentRequest
import com.michaldrabik.data_remote.trakt.model.request.CreateListRequest
import com.michaldrabik.data_remote.trakt.model.request.RatingRequest
import com.michaldrabik.data_remote.trakt.model.request.RatingRequestIds
import com.michaldrabik.data_remote.trakt.model.request.RatingRequestValue
import okhttp3.Headers

private const val TRAKT_SYNC_PAGE_LIMIT = 250

internal class AuthorizedTraktApi(
  private val usersService: TraktUsersService,
  private val syncService: TraktSyncService,
  private val commentsService: TraktCommentsService,
) : AuthorizedTraktRemoteDataSource {

  override suspend fun postComment(commentRequest: CommentRequest) = commentsService.postComment(commentRequest)

  override suspend fun postCommentReply(
    commentId: Long,
    commentRequest: CommentRequest,
  ) = commentsService.postCommentReply(commentId, commentRequest)

  override suspend fun deleteComment(commentId: Long) = commentsService.deleteComment(commentId)

  override suspend fun fetchMyProfile() = usersService.fetchMyProfile()

  override suspend fun fetchHiddenShows(): List<HiddenItem> {
    var page = 1
    val results = mutableListOf<HiddenItem>()

    do {
      val response = usersService.fetchHiddenShows(page, TRAKT_SYNC_PAGE_LIMIT)
      results.addAll(response.body().orEmpty())
      page += 1
    } while (page <= response.headers().getPaginationPageCount())

    return results
  }

  override suspend fun postHiddenShows(shows: List<SyncExportItem>) {
    usersService.postHiddenShows(SyncExportRequest(shows = shows))
  }

  override suspend fun postHiddenMovies(movies: List<SyncExportItem>) {
    usersService.postHiddenMovies(SyncExportRequest(movies = movies))
  }

  override suspend fun fetchHiddenMovies(): List<HiddenItem> {
    var page = 1
    val results = mutableListOf<HiddenItem>()

    do {
      val response = usersService.fetchHiddenMovies(page, TRAKT_SYNC_PAGE_LIMIT)
      results.addAll(response.body().orEmpty())
      page += 1
    } while (page <= response.headers().getPaginationPageCount())

    return results
  }

  override suspend fun fetchSyncActivity(): SyncActivity = syncService.fetchSyncActivity()

  override suspend fun fetchSyncShowHistory(showId: Long): List<SyncHistoryItem> {
    var page = 1
    val results = mutableListOf<SyncHistoryItem>()

    do {
      val response = syncService.fetchSyncShowHistory(showId, page, TRAKT_SYNC_PAGE_LIMIT)
      results.addAll(response.body().orEmpty())
      page += 1
    } while (page <= response.headers().getPaginationPageCount())

    return results
  }

  override suspend fun fetchSyncWatchedShows(extended: String?) =
    syncService.fetchSyncWatched("shows", extended).filter { it.show != null }

  override suspend fun fetchSyncWatchedMovies(extended: String?) =
    syncService.fetchSyncWatched("movies", extended).filter { it.movie != null }

  override suspend fun fetchSyncShowsWatchlist() = fetchSyncWatchlist("shows")

  override suspend fun fetchSyncMoviesWatchlist() = fetchSyncWatchlist("movies")

  override suspend fun fetchSyncWatchlist(type: String): List<SyncItem> {
    var page = 1
    val results = mutableListOf<SyncItem>()

    do {
      val response = syncService.fetchSyncWatchlist(type, page, TRAKT_SYNC_PAGE_LIMIT)
      results.addAll(response.body().orEmpty())
      page += 1
    } while (page <= response.headers().getPaginationPageCount())

    return results
  }

  override suspend fun fetchSyncLists() = usersService.fetchSyncLists()

  override suspend fun fetchSyncList(listId: Long) = usersService.fetchSyncList(listId)

  override suspend fun fetchSyncListItems(
    listId: Long,
    withMovies: Boolean,
  ): List<SyncItem> {
    var page = 1
    val results = mutableListOf<SyncItem>()
    val types = arrayListOf("show")
      .apply { if (withMovies) add("movie") }
      .joinToString(",")

    do {
      val response = usersService.fetchSyncListItems(listId, types, page, TRAKT_SYNC_PAGE_LIMIT)
      results.addAll(response.body().orEmpty())
      page += 1
    } while (page <= response.headers().getPaginationPageCount())

    return results
  }

  override suspend fun postCreateList(
    name: String,
    description: String?,
  ): CustomList {
    val body = CreateListRequest(name, description)
    return usersService.postCreateList(body)
  }

  override suspend fun postUpdateList(customList: CustomList): CustomList {
    val body = CreateListRequest(customList.name, customList.description)
    return usersService.postUpdateList(customList.ids.trakt, body)
  }

  override suspend fun deleteList(listId: Long) {
    usersService.deleteList(listId)
  }

  override suspend fun postAddListItems(
    listTraktId: Long,
    showsIds: List<Long>,
    moviesIds: List<Long>,
  ) {
    val body = SyncExportRequest(
      shows = showsIds.map { SyncExportItem.create(it, null) },
      movies = moviesIds.map { SyncExportItem.create(it, null) },
    )
    usersService.postAddListItems(listTraktId, body)
  }

  override suspend fun postRemoveListItems(
    listTraktId: Long,
    showsIds: List<Long>,
    moviesIds: List<Long>,
  ) {
    val body = SyncExportRequest(
      shows = showsIds.map { SyncExportItem.create(it, null) },
      movies = moviesIds.map { SyncExportItem.create(it, null) },
    )
    usersService.postRemoveListItems(listTraktId, body)
  }

  override suspend fun postSyncWatchlist(request: SyncExportRequest) = syncService.postSyncWatchlist(request)

  override suspend fun postSyncWatched(request: SyncExportRequest) = syncService.postSyncWatched(request)

  override suspend fun postDeleteProgress(request: SyncExportRequest) = syncService.deleteHistory(request)

  override suspend fun postDeleteWatchlist(request: SyncExportRequest) {
    syncService.deleteWatchlist(request)
  }

  override suspend fun deleteHiddenShow(request: SyncExportRequest) {
    usersService.deleteHidden("progress_watched", request)
  }

  override suspend fun deleteHiddenMovie(request: SyncExportRequest) {
    usersService.deleteHidden("calendar", request)
  }

  override suspend fun deleteRating(show: Show) {
    val requestIds = RatingRequestIds(show.ids?.trakt ?: -1)
    val requestValue = RatingRequestValue(0, "", requestIds)
    val body = RatingRequest(shows = listOf(requestValue))
    syncService.postRemoveRating(body)
  }

  override suspend fun deleteRating(movie: Movie) {
    val requestIds = RatingRequestIds(movie.ids?.trakt ?: -1)
    val requestValue = RatingRequestValue(0, "", requestIds)
    val body = RatingRequest(movies = listOf(requestValue))
    syncService.postRemoveRating(body)
  }

  override suspend fun deleteRating(episode: Episode) {
    val requestIds = RatingRequestIds(episode.ids?.trakt ?: -1)
    val requestValue = RatingRequestValue(0, "", requestIds)
    val body = RatingRequest(episodes = listOf(requestValue))
    syncService.postRemoveRating(body)
  }

  override suspend fun deleteRating(season: Season) {
    val requestIds = RatingRequestIds(season.ids?.trakt ?: -1)
    val requestValue = RatingRequestValue(0, "", requestIds)
    val body = RatingRequest(seasons = listOf(requestValue))
    syncService.postRemoveRating(body)
  }

  override suspend fun postRatings(request: RatingRequest) = syncService.postRating(request)

  override suspend fun postRating(
    movie: Movie,
    rating: Int,
    ratedAt: String,
  ) {
    val requestIds = RatingRequestIds(movie.ids?.trakt ?: -1)
    val requestValue = RatingRequestValue(rating, ratedAt, requestIds)
    val body = RatingRequest(movies = listOf(requestValue))
    syncService.postRating(body)
  }

  override suspend fun postRating(
    show: Show,
    rating: Int,
    ratedAt: String,
  ) {
    val requestIds = RatingRequestIds(show.ids?.trakt ?: -1)
    val requestValue = RatingRequestValue(rating, ratedAt, requestIds)
    val body = RatingRequest(shows = listOf(requestValue))
    syncService.postRating(body)
  }

  override suspend fun postRating(
    episode: Episode,
    rating: Int,
    ratedAt: String,
  ) {
    val requestIds = RatingRequestIds(episode.ids?.trakt ?: -1)
    val requestValue = RatingRequestValue(rating, ratedAt, requestIds)
    val body = RatingRequest(episodes = listOf(requestValue))
    syncService.postRating(body)
  }

  override suspend fun postRating(
    season: Season,
    rating: Int,
    ratedAt: String,
  ) {
    val requestIds = RatingRequestIds(season.ids?.trakt ?: -1)
    val requestValue = RatingRequestValue(rating, ratedAt, requestIds)
    val body = RatingRequest(seasons = listOf(requestValue))
    syncService.postRating(body)
  }

  override suspend fun fetchShowsRatings() = syncService.fetchShowsRatings()

  override suspend fun fetchMoviesRatings() = syncService.fetchMoviesRatings()

  override suspend fun fetchEpisodesRatings() = syncService.fetchEpisodesRatings()

  override suspend fun fetchSeasonsRatings() = syncService.fetchSeasonsRatings()
}

private fun Headers.getPaginationPageCount(): Int = this["x-pagination-page-count"]?.toInt() ?: 0
