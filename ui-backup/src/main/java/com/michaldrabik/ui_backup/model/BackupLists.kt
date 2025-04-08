package com.michaldrabik.ui_backup.model

import com.squareup.moshi.Json

data class BackupLists(
  @Json(name = "l") val lists: List<BackupList> = emptyList(),
)

data class BackupList(
  @Json(name = "id") val id: Long,
  @Json(name = "tId") val traktId: Long?,
  @Json(name = "sId") val slugId: String,
  @Json(name = "n") val name: String,
  @Json(name = "d") val description: String?,
  @Json(name = "p") val privacy: String,
  @Json(name = "ic") val itemCount: Long,
  @Json(name = "c") val createdAt: String,
  @Json(name = "u") val updatedAt: String,
  @Json(name = "it") val items: List<BackupListItem> = emptyList(),
)

data class BackupListItem(
  @Json(name = "id") val id: Long,
  @Json(name = "lId") val listId: Long,
  @Json(name = "tId") val traktId: Long,
  @Json(name = "tmId") val tmdbId: Long,
  @Json(name = "t") val type: String,
  @Json(name = "r") val rank: Long,
  @Json(name = "l") val listedAt: String,
  @Json(name = "c") val createdAt: String,
  @Json(name = "u") val updatedAt: String,
)
