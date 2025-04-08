package com.michaldrabik.data_remote.trakt.api.service

import com.michaldrabik.data_remote.trakt.model.RatingResultEpisode
import com.michaldrabik.data_remote.trakt.model.RatingResultMovie
import com.michaldrabik.data_remote.trakt.model.RatingResultSeason
import com.michaldrabik.data_remote.trakt.model.RatingResultShow
import com.michaldrabik.data_remote.trakt.model.SyncActivity
import com.michaldrabik.data_remote.trakt.model.SyncExportRequest
import com.michaldrabik.data_remote.trakt.model.SyncHistoryItem
import com.michaldrabik.data_remote.trakt.model.SyncItem
import com.michaldrabik.data_remote.trakt.model.request.RatingRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TraktSyncService {

  @GET("sync/last_activities")
  suspend fun fetchSyncActivity(): SyncActivity

  @GET("sync/history/shows/{showId}")
  suspend fun fetchSyncShowHistory(
    @Path("showId") showId: Long,
    @Query("page") page: Int? = null,
    @Query("limit") limit: Int? = null,
  ): Response<List<SyncHistoryItem>>

  @GET("sync/watched/{type}")
  suspend fun fetchSyncWatched(
    @Path("type") type: String,
    @Query("extended") extended: String?,
  ): List<SyncItem>

  @GET("sync/watchlist/{type}?extended=full")
  suspend fun fetchSyncWatchlist(
    @Path("type") type: String,
    @Query("page") page: Int? = null,
    @Query("limit") limit: Int? = null,
  ): Response<List<SyncItem>>

  @POST("sync/watchlist")
  suspend fun postSyncWatchlist(
    @Body request: SyncExportRequest,
  )

  @POST("sync/history")
  suspend fun postSyncWatched(
    @Body request: SyncExportRequest,
  )

  @POST("sync/watchlist/remove")
  suspend fun deleteWatchlist(
    @Body request: SyncExportRequest,
  )

  @POST("sync/history/remove")
  suspend fun deleteHistory(
    @Body request: SyncExportRequest,
  )

  // Ratings

  @POST("sync/ratings")
  suspend fun postRating(
    @Body request: RatingRequest,
  )

  @POST("sync/ratings/remove")
  suspend fun postRemoveRating(
    @Body request: RatingRequest,
  )

  @GET("sync/ratings/shows")
  suspend fun fetchShowsRatings(): List<RatingResultShow>

  @GET("sync/ratings/movies")
  suspend fun fetchMoviesRatings(): List<RatingResultMovie>

  @GET("sync/ratings/episodes")
  suspend fun fetchEpisodesRatings(): List<RatingResultEpisode>

  @GET("sync/ratings/seasons")
  suspend fun fetchSeasonsRatings(): List<RatingResultSeason>
}
