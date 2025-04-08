package com.michaldrabik.data_local.sources

import com.michaldrabik.data_local.database.model.Season

interface SeasonsLocalDataSource {

  suspend fun getAll(traktIds: List<Long>): List<Season>

  suspend fun getAllByShowsIds(traktIds: List<Long>): List<Season>

  suspend fun getAllByShowsIdsChunk(traktIds: List<Long>): List<Season>

  suspend fun getAllWatched(): List<Season>

  suspend fun getAllWatchedForShows(traktIds: List<Long>): List<Season>

  suspend fun getAllWatchedIdsForShows(traktIds: List<Long>): List<Long>

  suspend fun getAllByShowId(traktId: Long): List<Season>

  suspend fun getById(traktId: Long): Season?

  suspend fun update(items: List<Season>)

  suspend fun upsert(items: List<Season>)

  suspend fun delete(items: List<Season>)

  suspend fun deleteAllForShow(showTraktId: Long)
}
