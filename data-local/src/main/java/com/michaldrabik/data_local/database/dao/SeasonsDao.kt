package com.michaldrabik.data_local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.michaldrabik.data_local.database.model.Season
import com.michaldrabik.data_local.sources.SeasonsLocalDataSource

@Dao
interface SeasonsDao : SeasonsLocalDataSource {

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insert(items: List<Season>): List<Long>

  @Update(onConflict = OnConflictStrategy.REPLACE)
  override suspend fun update(items: List<Season>)

  @Delete
  override suspend fun delete(items: List<Season>)

  @Query("SELECT * FROM seasons WHERE id_trakt IN (:traktIds)")
  override suspend fun getAll(traktIds: List<Long>): List<Season>

  @Transaction
  override suspend fun getAllByShowsIds(traktIds: List<Long>): List<Season> {
    val result = mutableListOf<Season>()
    val chunks = traktIds.chunked(50)
    chunks.forEach { chunk ->
      result += getAllByShowsIdsChunk(chunk)
    }
    return result
  }

  @Query("SELECT * FROM seasons WHERE id_show_trakt IN (:traktIds)")
  override suspend fun getAllByShowsIdsChunk(traktIds: List<Long>): List<Season>

  @Query("SELECT * FROM seasons WHERE is_watched = 1")
  override suspend fun getAllWatched(): List<Season>

  @Query("SELECT * FROM seasons WHERE id_show_trakt IN (:traktIds) AND is_watched = 1")
  override suspend fun getAllWatchedForShows(traktIds: List<Long>): List<Season>

  @Query("SELECT id_trakt FROM seasons WHERE id_show_trakt IN (:traktIds) AND is_watched = 1")
  override suspend fun getAllWatchedIdsForShows(traktIds: List<Long>): List<Long>

  @Query("SELECT * FROM seasons WHERE id_show_trakt = :traktId")
  override suspend fun getAllByShowId(traktId: Long): List<Season>

  @Query("SELECT * FROM seasons WHERE id_trakt = :traktId")
  override suspend fun getById(traktId: Long): Season?

  @Transaction
  override suspend fun upsert(items: List<Season>) {
    val result = insert(items)
    val updateList = mutableListOf<Season>()

    result.forEachIndexed { index, id ->
      if (id == -1L) updateList.add(items[index])
    }

    if (updateList.isNotEmpty()) update(updateList)
  }

  @Query("DELETE FROM seasons WHERE id_show_trakt = :showTraktId")
  override suspend fun deleteAllForShow(showTraktId: Long)
}
