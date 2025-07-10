package com.michaldrabik.ui_my_shows.watchlist.helpers

import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortOrder.DATE_ADDED
import com.michaldrabik.ui_model.SortOrder.NAME
import com.michaldrabik.ui_model.SortOrder.NEWEST
import com.michaldrabik.ui_model.SortOrder.RANDOM
import com.michaldrabik.ui_model.SortOrder.RATING
import com.michaldrabik.ui_model.SortOrder.USER_RATING
import com.michaldrabik.ui_model.SortType
import com.michaldrabik.ui_model.SortType.ASCENDING
import com.michaldrabik.ui_model.SortType.DESCENDING
import com.michaldrabik.ui_my_shows.common.recycler.CollectionListItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class WatchlistItemSorter @Inject constructor() {

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
      RATING -> compareBy { it.show.rating }
      USER_RATING ->
        compareByDescending<CollectionListItem.ShowItem> { it.userRating != null }
          .thenBy { it.userRating }
          .thenBy { getTitle(it) }
      DATE_ADDED -> compareBy { it.show.createdAt }
      NEWEST -> compareBy<CollectionListItem.ShowItem> { it.show.firstAired }.thenBy { it.show.year }
      RANDOM -> compareBy { Random.nextInt() }
      else -> throw IllegalStateException("Invalid sort order")
    }

  private fun sortDescending(sortOrder: SortOrder) =
    when (sortOrder) {
      NAME -> compareByDescending { getTitle(it) }
      RATING -> compareByDescending { it.show.rating }
      USER_RATING ->
        compareByDescending<CollectionListItem.ShowItem> { it.userRating != null }
          .thenByDescending { it.userRating }
          .thenBy { getTitle(it) }
      DATE_ADDED -> compareByDescending { it.show.createdAt }
      NEWEST -> compareByDescending<CollectionListItem.ShowItem> {
        it.show.firstAired
      }.thenByDescending { it.show.year }
      RANDOM -> compareBy { Random.nextInt() }
      else -> throw IllegalStateException("Invalid sort order")
    }

  private fun getTitle(item: CollectionListItem.ShowItem): String {
    val translatedTitle =
      if (item.translation?.hasTitle == true) {
        item.translation.title
      } else {
        item.show.titleNoThe
      }
    return translatedTitle.uppercase()
  }
}
