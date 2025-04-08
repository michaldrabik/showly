package com.michaldrabik.ui_backup.model.v1

import com.squareup.moshi.Json

data class BackupLists1(
  @Json(name = "l") val lists: List<BackupList1> = emptyList(),
)

data class BackupList1(
  @Json(name = "id") val id: Long,
  @Json(name = "tId") val traktId: Long?,
  @Json(name = "sId") val slugId: String,
  @Json(name = "n") val name: String,
  @Json(name = "d") val description: String?,
  @Json(name = "p") val privacy: String,
  @Json(name = "ic") val itemCount: Long,
  @Json(name = "c") val createdAt: String,
  @Json(name = "u") val updatedAt: String,
  @Json(name = "it") val items: List<BackupListItem1> = emptyList(),
)

data class BackupListItem1(
  @Json(name = "id") val id: Long,
  @Json(name = "lId") val listId: Long,
  @Json(name = "tId") val traktId: Long,
  @Json(name = "t") val type: String,
  @Json(name = "r") val rank: Long,
  @Json(name = "l") val listedAt: String,
  @Json(name = "c") val createdAt: String,
  @Json(name = "u") val updatedAt: String,
)
