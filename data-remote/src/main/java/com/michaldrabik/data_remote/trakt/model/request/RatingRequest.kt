package com.michaldrabik.data_remote.trakt.model.request

data class RatingRequest(
  val shows: List<RatingRequestValue>? = null,
  val movies: List<RatingRequestValue>? = null,
  val episodes: List<RatingRequestValue>? = null,
  val seasons: List<RatingRequestValue>? = null,
)

data class RatingRequestValue(
  val rating: Int,
  val rated_at: String,
  val ids: RatingRequestIds,
)

data class RatingRequestIds(
  val trakt: Long,
)
