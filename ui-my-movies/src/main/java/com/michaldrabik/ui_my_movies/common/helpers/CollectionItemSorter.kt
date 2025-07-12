package com.michaldrabik.ui_my_movies.common.helpers

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
import com.michaldrabik.ui_my_movies.common.recycler.CollectionListItem
import java.util.UUID
import javax.inject.Inject

class CollectionItemSorter @Inject constructor() {

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
      RATING -> compareBy { it.movie.rating }
      RUNTIME -> compareBy { it.movie.runtime }
      USER_RATING ->
        compareByDescending<CollectionListItem.MovieItem> { it.userRating != null }
          .thenBy { it.userRating }
          .thenBy { getTitle(it) }
      DATE_ADDED -> compareBy { it.movie.createdAt }
      NEWEST -> compareBy<CollectionListItem.MovieItem> { it.movie.released == null }
        .thenBy { it.movie.released }
        .thenBy { it.movie.year }
      RANDOM -> compareBy { UUID.randomUUID() }
      else -> throw IllegalStateException("Invalid sort order")
    }

  private fun sortDescending(sortOrder: SortOrder) =
    when (sortOrder) {
      NAME -> compareByDescending { getTitle(it) }
      RATING -> compareByDescending { it.movie.rating }
      RUNTIME -> compareByDescending { it.movie.runtime }
      USER_RATING ->
        compareByDescending<CollectionListItem.MovieItem> { it.userRating != null }
          .thenByDescending { it.userRating }
          .thenBy { getTitle(it) }
      DATE_ADDED -> compareByDescending { it.movie.createdAt }
      NEWEST -> compareByDescending<CollectionListItem.MovieItem> { it.movie.released }
        .thenByDescending { it.movie.year }
      RANDOM -> compareBy { UUID.randomUUID() }
      else -> throw IllegalStateException("Invalid sort order")
    }

  private fun getTitle(item: CollectionListItem.MovieItem): String {
    val translatedTitle =
      if (item.translation?.hasTitle == true) {
        item.translation.title
      } else {
        item.movie.titleNoThe
      }
    return translatedTitle.uppercase()
  }
}
