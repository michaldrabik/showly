@file:Suppress("ktlint:standard:max-line-length")

package com.michaldrabik.data_local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import androidx.room.Transaction
import com.michaldrabik.data_local.database.model.Episode
import com.michaldrabik.data_local.sources.EpisodesLocalDataSource

@Dao
interface EpisodesDao : EpisodesLocalDataSource {

  @Insert(onConflict = REPLACE)
  override suspend fun upsert(episodes: List<Episode>)

  @Transaction
  override suspend fun upsertChunked(items: List<Episode>) {
    val chunks = items.chunked(500)
    chunks.forEach { chunk -> upsert(chunk) }
  }

  @Query("SELECT * FROM episodes WHERE id_show_trakt = :showTraktId AND id_trakt = :episodeTraktId")
  override suspend fun getById(
    showTraktId: Long,
    episodeTraktId: Long,
  ): Episode?

  @Query(
    "SELECT EXISTS(SELECT 1 FROM episodes WHERE id_show_trakt = :showTraktId AND id_trakt = :episodeTraktId AND is_watched = 1)",
  )
  override suspend fun isEpisodeWatched(
    showTraktId: Long,
    episodeTraktId: Long,
  ): Boolean

  @Query("SELECT * FROM episodes WHERE id_trakt IN(:episodesIds)")
  override suspend fun getAll(episodesIds: List<Long>): List<Episode>

  @Query("SELECT * FROM episodes WHERE id_season = :seasonTraktId")
  override suspend fun getAllForSeason(seasonTraktId: Long): List<Episode>

  @Query("SELECT * FROM episodes WHERE id_show_trakt = :showTraktId")
  override suspend fun getAllByShowId(showTraktId: Long): List<Episode>

  @Query("SELECT * FROM episodes WHERE id_show_trakt = :showTraktId AND season_number = :seasonNumber")
  override suspend fun getAllByShowId(
    showTraktId: Long,
    seasonNumber: Int,
  ): List<Episode>

  @Transaction
  override suspend fun getAllByShowsIds(showTraktIds: List<Long>): List<Episode> {
    val result = mutableListOf<Episode>()
    val chunks = showTraktIds.chunked(50)
    chunks.forEach { chunk ->
      result += getAllByShowsIdsChunk(chunk)
    }
    return result
  }

  @Transaction
  @Query("SELECT * FROM episodes WHERE id_show_trakt IN (:showTraktIds)")
  override suspend fun getAllByShowsIdsChunk(showTraktIds: List<Long>): List<Episode>

  @Query(
    "SELECT * from episodes where id_show_trakt = :showTraktId AND is_watched = 0 AND season_number != 0 AND first_aired <= :toTime ORDER BY season_number ASC, episode_number ASC LIMIT 1",
  )
  override suspend fun getFirstUnwatched(
    showTraktId: Long,
    toTime: Long,
  ): Episode?

  @Query(
    "SELECT * from episodes where id_show_trakt = :showTraktId AND is_watched = 0 AND season_number != 0 AND first_aired > :fromTime AND first_aired <= :toTime ORDER BY season_number ASC, episode_number ASC LIMIT 1",
  )
  override suspend fun getFirstUnwatched(
    showTraktId: Long,
    fromTime: Long,
    toTime: Long,
  ): Episode?

  @Query(
    "SELECT * from episodes where id_show_trakt = :showTraktId " +
      "AND is_watched = 0 " +
      "AND season_number != 0 " +
      "AND ((season_number * 10000) + episode_number) > ((:seasonNumber * 10000) + :episodeNumber) " +
      "AND first_aired <= :toTime " +
      "ORDER BY season_number ASC, episode_number ASC LIMIT 1",
  )
  override suspend fun getFirstUnwatchedAfterEpisode(
    showTraktId: Long,
    seasonNumber: Int,
    episodeNumber: Int,
    toTime: Long,
  ): Episode?

  @Query(
    "SELECT * from episodes where id_show_trakt = :showTraktId AND is_watched = 1 AND season_number != 0 ORDER BY last_watched_at DESC LIMIT 1",
  )
  override suspend fun getLastWatched(showTraktId: Long): Episode?

  @Query(
    "SELECT COUNT(id_trakt) FROM episodes WHERE id_show_trakt = :showTraktId AND first_aired < :toTime AND season_number != 0",
  )
  override suspend fun getTotalCount(
    showTraktId: Long,
    toTime: Long,
  ): Int

  @Query("SELECT COUNT(id_trakt) FROM episodes WHERE id_show_trakt = :showTraktId AND season_number != 0")
  override suspend fun getTotalCount(showTraktId: Long): Int

  @Query(
    "SELECT COUNT(id_trakt) FROM episodes WHERE id_show_trakt = :showTraktId AND is_watched = 1 AND first_aired < :toTime AND season_number != 0",
  )
  override suspend fun getWatchedCount(
    showTraktId: Long,
    toTime: Long,
  ): Int

  @Query(
    "SELECT COUNT(id_trakt) FROM episodes WHERE id_show_trakt = :showTraktId AND is_watched = 1 AND season_number != 0",
  )
  override suspend fun getWatchedCount(showTraktId: Long): Int

  @Query("SELECT * FROM episodes WHERE is_watched = 1")
  override suspend fun getAllWatched(): List<Episode>

  @Query("SELECT * FROM episodes WHERE id_show_trakt IN(:showsIds) AND is_watched = 1")
  override suspend fun getAllWatchedForShows(showsIds: List<Long>): List<Episode>

  @Query(
    "SELECT * FROM episodes WHERE id_show_trakt IN(:showsIds) AND is_watched = 1 AND last_watched_at NOT NULL AND last_watched_at >= :fromTime AND last_watched_at <= :toTime",
  )
  override suspend fun getAllWatchedForShows(
    showsIds: List<Long>,
    fromTime: Long,
    toTime: Long,
  ): List<Episode>

  @Query("SELECT id_trakt FROM episodes WHERE id_show_trakt IN(:showsIds) AND is_watched = 1")
  override suspend fun getAllWatchedIdsForShows(showsIds: List<Long>): List<Long>

  @Transaction
  override suspend fun updateIsExported(
    episodesIds: List<Long>,
    exportedAt: Long,
  ) {
    episodesIds.forEach {
      updateIsExported(it, exportedAt)
    }
  }

  @Query("UPDATE episodes SET last_exported_at = :exportedAt WHERE id_trakt = :episodeId")
  suspend fun updateIsExported(
    episodeId: Long,
    exportedAt: Long,
  )

  @Query("DELETE FROM episodes WHERE id_show_trakt = :showTraktId AND is_watched = 0")
  override suspend fun deleteAllUnwatchedForShow(showTraktId: Long)

  @Query("DELETE FROM episodes WHERE id_show_trakt = :showTraktId")
  override suspend fun deleteAllForShow(showTraktId: Long)

  @Delete
  override suspend fun delete(items: List<Episode>)
}
