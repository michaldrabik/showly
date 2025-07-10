package com.michaldrabik.ui_progress_movies.helpers

import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortOrder.DATE_ADDED
import com.michaldrabik.ui_model.SortOrder.NAME
import com.michaldrabik.ui_model.SortOrder.NEWEST
import com.michaldrabik.ui_model.SortOrder.RANDOM
import com.michaldrabik.ui_model.SortOrder.RATING
import com.michaldrabik.ui_model.SortOrder.RUNTIME
import com.michaldrabik.ui_model.SortOrder.USER_RATING
import com.michaldrabik.ui_model.SortType
import com.michaldrabik.ui_model.SortType.ASCENDING
import com.michaldrabik.ui_model.SortType.DESCENDING
import com.michaldrabik.ui_progress_movies.progress.recycler.ProgressMovieListItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class ProgressMoviesItemsSorter @Inject constructor() {

  fun sort(
    sortOrder: SortOrder,
    sortType: SortType,
  ) = when (sortType) {
    ASCENDING -> sortAscending(sortOrder)
    DESCENDING -> sortDescending(sortOrder)
  }

  private fun sortAscending(sortOrder: SortOrder) =
    when (sortOrder) {
      NAME -> compareBy { getTitle(it) }
      RUNTIME -> compareBy { it.movie.runtime }
      RATING -> compareBy { it.movie.rating }
      USER_RATING ->
        compareByDescending<ProgressMovieListItem.MovieItem> { it.userRating != null }
          .thenBy { it.userRating }
          .thenBy { getTitle(it) }
      DATE_ADDED -> compareBy { it.movie.updatedAt }
      NEWEST ->
        compareBy<ProgressMovieListItem.MovieItem> { it.movie.released }
          .thenBy { it.movie.year }
      RANDOM -> compareBy { Random.nextInt() }
      else -> throw IllegalStateException("Invalid sort order")
    }

  private fun sortDescending(sortOrder: SortOrder) =
    when (sortOrder) {
      NAME -> compareByDescending { getTitle(it) }
      RUNTIME -> compareByDescending { it.movie.runtime }
      RATING -> compareByDescending { it.movie.rating }
      USER_RATING ->
        compareByDescending<ProgressMovieListItem.MovieItem> { it.userRating != null }
          .thenByDescending { it.userRating }
          .thenBy { getTitle(it) }
      DATE_ADDED -> compareByDescending { it.movie.updatedAt }
      NEWEST ->
        compareByDescending<ProgressMovieListItem.MovieItem> { it.movie.released }
          .thenByDescending { it.movie.year }
      RANDOM -> compareBy { Random.nextInt() }
      else -> throw IllegalStateException("Invalid sort order")
    }

  private fun getTitle(item: ProgressMovieListItem.MovieItem): String {
    val translatedTitle =
      if (item.translation?.hasTitle == true) {
        item.translation.title
      } else {
        item.movie.titleNoThe
      }
    return translatedTitle.uppercase()
  }
}
