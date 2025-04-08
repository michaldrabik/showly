package com.michaldrabik.data_local.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.michaldrabik.data_local.database.model.Rating
import com.michaldrabik.data_local.sources.RatingsLocalDataSource

@Dao
interface RatingsDao :
  BaseDao<Rating>,
  RatingsLocalDataSource {

  @Query("SELECT * FROM ratings ORDER BY rated_at DESC")
  override suspend fun getAll(): List<Rating>

  @Query("SELECT * FROM ratings WHERE type == :type ORDER BY rated_at DESC")
  override suspend fun getAllByType(type: String): List<Rating>

  @Query("SELECT * FROM ratings WHERE id_trakt IN (:idsTrakt) AND type == :type ORDER BY rated_at DESC")
  override suspend fun getAllByType(
    idsTrakt: List<Long>,
    type: String,
  ): List<Rating>

  @Query("DELETE FROM ratings WHERE type == :type AND id_trakt IN (:ids)")
  suspend fun deleteAllByType(
    type: String,
    ids: Set<Long>,
  )

  @Query("DELETE FROM ratings WHERE id_trakt == :traktId AND type == :type")
  override suspend fun deleteByType(
    traktId: Long,
    type: String,
  )

  @Transaction
  override suspend fun replaceAll(
    ratings: List<Rating>,
    type: String,
  ) {
    deleteAllByType(type, ratings.map { it.idTrakt }.toSet())
    insert(ratings)
  }

  @Transaction
  override suspend fun replace(rating: Rating) {
    deleteByType(rating.idTrakt, rating.type)
    insert(listOf(rating))
  }
}
