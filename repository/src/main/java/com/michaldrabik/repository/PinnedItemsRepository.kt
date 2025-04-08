package com.michaldrabik.repository

import android.content.SharedPreferences
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.Show
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class PinnedItemsRepository @Inject constructor(
  @Named("watchlistPreferences") private val sharedPreferences: SharedPreferences,
  @Named("progressMoviesPreferences") private val sharedPreferencesMovies: SharedPreferences,
) {

  fun addPinnedItem(show: Show) = addShowPinnedItem(IdTrakt(show.traktId))

  fun addPinnedItem(movie: Movie) = addMoviePinnedItem(IdTrakt(movie.traktId))

  fun addShowPinnedItem(showId: IdTrakt) = sharedPreferences.edit().putLong(showId.id.toString(), showId.id).apply()

  fun addMoviePinnedItem(movieId: IdTrakt) =
    sharedPreferencesMovies.edit().putLong(movieId.id.toString(), movieId.id).apply()

  fun removePinnedItem(show: Show) = sharedPreferences.edit().remove(show.traktId.toString()).apply()

  fun removePinnedItem(movie: Movie) = sharedPreferencesMovies.edit().remove(movie.traktId.toString()).apply()

  fun isItemPinned(show: Show) = sharedPreferences.contains(show.traktId.toString())

  fun isItemPinned(movie: Movie) = sharedPreferencesMovies.contains(movie.traktId.toString())

  fun getAllMovies(): List<Long> = sharedPreferencesMovies.all.values.map { it as Long }

  fun getAllShows(): List<Long> = sharedPreferences.all.values.map { it as Long }
}
