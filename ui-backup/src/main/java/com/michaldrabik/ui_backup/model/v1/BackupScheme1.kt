package com.michaldrabik.ui_backup.model.v1

/**
 * All dates should be exported in ISO 8601 format.
 * Example: "2021-01-01T00:00:00.000Z" -> YYYY-MM-DDTHH:mm:ss.sssZ
 */
data class BackupScheme1(
  val version: Int,
  val shows: BackupShows1 = BackupShows1(),
  val movies: BackupMovies1 = BackupMovies1(),
  val lists: BackupLists1 = BackupLists1(),
)
