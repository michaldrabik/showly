package com.michaldrabik.data_local.sources

import com.michaldrabik.data_local.database.model.Movie
import com.michaldrabik.data_local.database.model.MovieSearch

interface MoviesLocalDataSource {

  suspend fun getAll(): List<Movie>

  suspend fun getAllForSearch(): List<MovieSearch>

  suspend fun getAll(ids: List<Long>): List<Movie>

  suspend fun getAllTmdbIds(traktIds: List<Long>): Map<Long, Long>

  suspend fun getAllChunked(ids: List<Long>): List<Movie>

  suspend fun getById(traktId: Long): Movie?

  suspend fun getByTmdbId(tmdbId: Long): Movie?

  suspend fun getBySlug(slug: String): Movie?

  suspend fun getById(imdbId: String): Movie?

  suspend fun deleteById(traktId: Long)

  suspend fun upsert(movies: List<Movie>)
}
