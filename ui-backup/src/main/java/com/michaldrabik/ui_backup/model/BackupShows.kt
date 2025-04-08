package com.michaldrabik.ui_backup.model

import com.squareup.moshi.Json

data class BackupShows(
  @Json(name = "cH") val collectionHistory: List<BackupShow> = emptyList(),
  @Json(name = "cW") val collectionWatchlist: List<BackupShow> = emptyList(),
  @Json(name = "cHid") val collectionHidden: List<BackupShow> = emptyList(),
  @Json(name = "pEp") val progressEpisodes: List<BackupEpisode> = emptyList(),
  @Json(name = "pSe") val progressSeasons: List<BackupSeason> = emptyList(),
  @Json(name = "pP") val progressPinned: List<Long> = emptyList(),
  @Json(name = "pOH") val progressOnHold: List<Long> = emptyList(),
  @Json(name = "rS") val ratingsShows: List<BackupShowRating> = emptyList(),
  @Json(name = "rSe") val ratingsSeasons: List<BackupSeasonRating> = emptyList(),
  @Json(name = "rEp") val ratingsEpisodes: List<BackupEpisodeRating> = emptyList(),
)

data class BackupShow(
  @Json(name = "id") val traktId: Long,
  @Json(name = "tmId") val tmdbId: Long,
  @Json(name = "t") val title: String,
  @Json(name = "a") val addedAt: String,
  @Json(name = "u") val updatedAt: String,
)

data class BackupSeason(
  @Json(name = "id") val traktId: Long,
  @Json(name = "sId") val showTraktId: Long,
  @Json(name = "stmId") val showTmdbId: Long,
  @Json(name = "sN") val seasonNumber: Int,
)

data class BackupEpisode(
  @Json(name = "id") val traktId: Long,
  @Json(name = "sId") val showTraktId: Long,
  @Json(name = "stmId") val showTmdbId: Long,
  @Json(name = "eN") val episodeNumber: Int,
  @Json(name = "sN") val seasonNumber: Int,
  @Json(name = "a") val addedAt: String?,
)

// Ratings

data class BackupShowRating(
  @Json(name = "id") val traktId: Long,
  @Json(name = "tmId") val tmdbId: Long,
  @Json(name = "r") val rating: Int,
  @Json(name = "rA") val ratedAt: String,
)

data class BackupSeasonRating(
  @Json(name = "id") val traktId: Long,
  @Json(name = "sId") val showTraktId: Long,
  @Json(name = "stmId") val showTmdbId: Long,
  @Json(name = "sN") val seasonNumber: Int,
  @Json(name = "r") val rating: Int,
  @Json(name = "rA") val ratedAt: String,
)

data class BackupEpisodeRating(
  @Json(name = "id") val traktId: Long,
  @Json(name = "sId") val showTraktId: Long,
  @Json(name = "stmId") val showTmdbId: Long,
  @Json(name = "sN") val seasonNumber: Int,
  @Json(name = "eN") val episodeNumber: Int,
  @Json(name = "r") val rating: Int,
  @Json(name = "rA") val ratedAt: String,
)
