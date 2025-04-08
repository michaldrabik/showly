package com.michaldrabik.ui_backup.model.v1

import com.squareup.moshi.Json

data class BackupShows1(
  @Json(name = "cH") val collectionHistory: List<BackupShow1> = emptyList(),
  @Json(name = "cW") val collectionWatchlist: List<BackupShow1> = emptyList(),
  @Json(name = "cHid") val collectionHidden: List<BackupShow1> = emptyList(),
  @Json(name = "pEp") val progressEpisodes: List<BackupEpisode1> = emptyList(),
  @Json(name = "pSe") val progressSeasons: List<BackupSeason1> = emptyList(),
  @Json(name = "pP") val progressPinned: List<Long> = emptyList(),
  @Json(name = "pOH") val progressOnHold: List<Long> = emptyList(),
)

data class BackupShow1(
  @Json(name = "id") val traktId: Long,
  @Json(name = "tmId") val tmdbId: Long,
  @Json(name = "t") val title: String,
  @Json(name = "a") val addedAt: String,
  @Json(name = "u") val updatedAt: String,
)

data class BackupSeason1(
  @Json(name = "id") val traktId: Long,
  @Json(name = "sId") val showTraktId: Long,
  @Json(name = "sN") val seasonNumber: Int,
)

data class BackupEpisode1(
  @Json(name = "id") val traktId: Long,
  @Json(name = "sId") val showTraktId: Long,
  @Json(name = "eN") val episodeNumber: Int,
  @Json(name = "sN") val seasonNumber: Int,
  @Json(name = "a") val addedAt: String?,
)
