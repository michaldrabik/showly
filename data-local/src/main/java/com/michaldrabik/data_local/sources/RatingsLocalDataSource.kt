package com.michaldrabik.data_local.sources

import com.michaldrabik.data_local.database.model.Rating

interface RatingsLocalDataSource {

  suspend fun getAll(): List<Rating>

  suspend fun getAllByType(type: String): List<Rating>

  suspend fun getAllByType(
    idsTrakt: List<Long>,
    type: String,
  ): List<Rating>

  suspend fun deleteByType(
    traktId: Long,
    type: String,
  )

  suspend fun replaceAll(
    ratings: List<Rating>,
    type: String,
  )

  suspend fun replace(rating: Rating)
}
