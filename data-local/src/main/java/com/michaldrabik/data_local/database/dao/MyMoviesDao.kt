package com.michaldrabik.data_local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.michaldrabik.data_local.database.model.Movie
import com.michaldrabik.data_local.database.model.MyMovie
import com.michaldrabik.data_local.sources.MyMoviesLocalDataSource

@Dao
interface MyMoviesDao : MyMoviesLocalDataSource {

  @Query(
    "SELECT movies.*, movies_my_movies.updated_at AS updated_at FROM movies " +
      "INNER JOIN movies_my_movies USING(id_trakt)",
  )
  override suspend fun getAll(): List<Movie>

  @Query(
    "SELECT movies.*, movies_my_movies.updated_at AS updated_at FROM movies " +
      "INNER JOIN movies_my_movies USING(id_trakt) WHERE id_trakt IN (:ids)",
  )
  override suspend fun getAll(ids: List<Long>): List<Movie>

  @Query(
    "SELECT movies.* FROM movies " +
      "INNER JOIN movies_my_movies USING(id_trakt) ORDER BY movies_my_movies.updated_at DESC LIMIT :limit",
  )
  override suspend fun getAllRecent(limit: Int): List<Movie>

  @Query("SELECT movies.id_trakt FROM movies INNER JOIN movies_my_movies USING(id_trakt)")
  override suspend fun getAllTraktIds(): List<Long>

  @Query(
    "SELECT movies.*, movies_my_movies.updated_at AS updated_at FROM movies INNER JOIN movies_my_movies USING(id_trakt) WHERE id_trakt == :traktId",
  )
  override suspend fun getById(traktId: Long): Movie?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  override suspend fun insert(movies: List<MyMovie>)

  @Query("DELETE FROM movies_my_movies WHERE id_trakt == :traktId")
  override suspend fun deleteById(traktId: Long)

  @Query("SELECT EXISTS(SELECT 1 FROM movies_my_movies WHERE id_trakt = :traktId LIMIT 1);")
  override suspend fun checkExists(traktId: Long): Boolean
}
