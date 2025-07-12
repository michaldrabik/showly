package com.michaldrabik.ui_lists.details.helpers

import com.michaldrabik.ui_lists.details.recycler.ListDetailsItem
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortOrder.DATE_ADDED
import com.michaldrabik.ui_model.SortOrder.NAME
import com.michaldrabik.ui_model.SortOrder.NEWEST
import com.michaldrabik.ui_model.SortOrder.RANDOM
import com.michaldrabik.ui_model.SortOrder.RANK
import com.michaldrabik.ui_model.SortOrder.RATING
import com.michaldrabik.ui_model.SortOrder.USER_RATING
import com.michaldrabik.ui_model.SortType
import com.michaldrabik.ui_model.SortType.ASCENDING
import com.michaldrabik.ui_model.SortType.DESCENDING
import java.util.UUID
import javax.inject.Inject

class ListDetailsSorter @Inject constructor() {

  fun sort(
    sortOrder: SortOrder,
    sortType: SortType,
  ) = when (sortType) {
    ASCENDING -> sortAscending(sortOrder)
    DESCENDING -> sortDescending(sortOrder)
  }

  private fun sortAscending(sortOrder: SortOrder): Comparator<ListDetailsItem> =
    when (sortOrder) {
      RANK -> compareBy { it.rank }
      NAME -> compareBy { getTitle(it) }
      NEWEST -> compareBy<ListDetailsItem> { it.getYear() }.thenBy { it.getDate() }
      RATING -> compareBy { it.getRating() }
      USER_RATING ->
        compareByDescending<ListDetailsItem> { it.userRating != null }
          .thenBy { it.userRating }
          .thenBy { getTitle(it) }
      DATE_ADDED -> compareBy { it.listedAt }
      RANDOM -> compareBy { UUID.randomUUID() }
      else -> throw IllegalStateException("Invalid sort order")
    }

  private fun sortDescending(sortOrder: SortOrder): Comparator<ListDetailsItem> =
    when (sortOrder) {
      RANK -> compareByDescending { it.rank }
      NAME -> compareByDescending { getTitle(it) }
      NEWEST -> compareByDescending<ListDetailsItem> { it.getYear() }.thenByDescending { it.getDate() }
      RATING -> compareByDescending { it.getRating() }
      USER_RATING ->
        compareByDescending<ListDetailsItem> { it.userRating != null }
          .thenByDescending { it.userRating }
          .thenBy { getTitle(it) }
      DATE_ADDED -> compareByDescending { it.listedAt }
      RANDOM -> compareBy { UUID.randomUUID() }
      else -> throw IllegalStateException("Invalid sort order")
    }

  private fun getTitle(item: ListDetailsItem): String =
    if (item.translation?.hasTitle == true) {
      item.translation.title
    } else {
      item.getTitleNoThe().uppercase()
    }
}
