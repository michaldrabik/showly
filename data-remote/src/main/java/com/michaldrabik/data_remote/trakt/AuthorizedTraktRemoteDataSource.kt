package com.michaldrabik.data_remote.trakt

import com.michaldrabik.data_remote.trakt.model.Comment
import com.michaldrabik.data_remote.trakt.model.CustomList
import com.michaldrabik.data_remote.trakt.model.Episode
import com.michaldrabik.data_remote.trakt.model.HiddenItem
import com.michaldrabik.data_remote.trakt.model.Movie
import com.michaldrabik.data_remote.trakt.model.RatingResultEpisode
import com.michaldrabik.data_remote.trakt.model.RatingResultMovie
import com.michaldrabik.data_remote.trakt.model.RatingResultSeason
import com.michaldrabik.data_remote.trakt.model.RatingResultShow
import com.michaldrabik.data_remote.trakt.model.Season
import com.michaldrabik.data_remote.trakt.model.Show
import com.michaldrabik.data_remote.trakt.model.SyncActivity
import com.michaldrabik.data_remote.trakt.model.SyncExportItem
import com.michaldrabik.data_remote.trakt.model.SyncExportRequest
import com.michaldrabik.data_remote.trakt.model.SyncHistoryItem
import com.michaldrabik.data_remote.trakt.model.SyncItem
import com.michaldrabik.data_remote.trakt.model.User
import com.michaldrabik.data_remote.trakt.model.request.CommentRequest
import com.michaldrabik.data_remote.trakt.model.request.RatingRequest
import retrofit2.Response

/**
 * Fetch/post remote resources via authorized Trakt API
 */
interface AuthorizedTraktRemoteDataSource {

  suspend fun postComment(commentRequest: CommentRequest): Comment

  suspend fun postCommentReply(
    commentId: Long,
    commentRequest: CommentRequest,
  ): Comment

  suspend fun deleteComment(commentId: Long): Response<Any>

  suspend fun fetchMyProfile(): User

  suspend fun fetchHiddenShows(): List<HiddenItem>

  suspend fun postHiddenShows(shows: List<SyncExportItem> = emptyList())

  suspend fun postHiddenMovies(movies: List<SyncExportItem> = emptyList())

  suspend fun fetchHiddenMovies(): List<HiddenItem>

  suspend fun fetchSyncActivity(): SyncActivity

  suspend fun fetchSyncShowHistory(showId: Long): List<SyncHistoryItem>

  suspend fun fetchSyncWatchedShows(extended: String? = null): List<SyncItem>

  suspend fun fetchSyncWatchedMovies(extended: String? = null): List<SyncItem>

  suspend fun fetchSyncShowsWatchlist(): List<SyncItem>

  suspend fun fetchSyncMoviesWatchlist(): List<SyncItem>

  suspend fun fetchSyncWatchlist(type: String): List<SyncItem>

  suspend fun fetchSyncLists(): List<CustomList>

  suspend fun fetchSyncList(listId: Long): CustomList

  suspend fun fetchSyncListItems(
    listId: Long,
    withMovies: Boolean,
  ): List<SyncItem>

  suspend fun postCreateList(
    name: String,
    description: String?,
  ): CustomList

  suspend fun postUpdateList(customList: CustomList): CustomList

  suspend fun deleteList(listId: Long)

  suspend fun postAddListItems(
    listTraktId: Long,
    showsIds: List<Long>,
    moviesIds: List<Long>,
  )

  suspend fun postRemoveListItems(
    listTraktId: Long,
    showsIds: List<Long>,
    moviesIds: List<Long>,
  )

  suspend fun postSyncWatchlist(request: SyncExportRequest)

  suspend fun postSyncWatched(request: SyncExportRequest)

  suspend fun postDeleteProgress(request: SyncExportRequest)

  suspend fun postDeleteWatchlist(request: SyncExportRequest)

  suspend fun deleteHiddenShow(request: SyncExportRequest)

  suspend fun deleteHiddenMovie(request: SyncExportRequest)

  suspend fun deleteRating(show: Show)

  suspend fun deleteRating(movie: Movie)

  suspend fun deleteRating(episode: Episode)

  suspend fun deleteRating(season: Season)

  suspend fun postRatings(request: RatingRequest)

  suspend fun postRating(
    movie: Movie,
    rating: Int,
    ratedAt: String,
  )

  suspend fun postRating(
    show: Show,
    rating: Int,
    ratedAt: String,
  )

  suspend fun postRating(
    episode: Episode,
    rating: Int,
    ratedAt: String,
  )

  suspend fun postRating(
    season: Season,
    rating: Int,
    ratedAt: String,
  )

  suspend fun fetchShowsRatings(): List<RatingResultShow>

  suspend fun fetchMoviesRatings(): List<RatingResultMovie>

  suspend fun fetchEpisodesRatings(): List<RatingResultEpisode>

  suspend fun fetchSeasonsRatings(): List<RatingResultSeason>
}
