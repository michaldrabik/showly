package com.michaldrabik.ui_backup.model.v1

import com.squareup.moshi.Json

data class BackupMovies1(
  @Json(name = "cH") val collectionHistory: List<BackupMovie1> = emptyList(),
  @Json(name = "cW") val collectionWatchlist: List<BackupMovie1> = emptyList(),
  @Json(name = "cHid") val collectionHidden: List<BackupMovie1> = emptyList(),
  @Json(name = "pP") val progressPinned: List<Long> = emptyList(),
)

data class BackupMovie1(
  @Json(name = "id") val traktId: Long,
  @Json(name = "tmId") val tmdbId: Long,
  @Json(name = "t") val title: String,
  @Json(name = "a") val addedAt: String,
)
