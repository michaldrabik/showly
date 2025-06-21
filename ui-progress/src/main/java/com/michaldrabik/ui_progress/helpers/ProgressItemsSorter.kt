package com.michaldrabik.ui_progress.helpers

import com.michaldrabik.common.extensions.toMillis
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortOrder.EPISODES_LEFT
import com.michaldrabik.ui_model.SortOrder.NAME
import com.michaldrabik.ui_model.SortOrder.NEWEST
import com.michaldrabik.ui_model.SortOrder.RANDOM
import com.michaldrabik.ui_model.SortOrder.RATING
import com.michaldrabik.ui_model.SortOrder.RECENTLY_WATCHED
import com.michaldrabik.ui_model.SortOrder.USER_RATING
import com.michaldrabik.ui_model.SortType
import com.michaldrabik.ui_model.SortType.ASCENDING
import com.michaldrabik.ui_model.SortType.DESCENDING
import com.michaldrabik.ui_progress.progress.recycler.ProgressListItem
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class ProgressItemsSorter @Inject constructor() {
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
      RECENTLY_WATCHED -> compareBy { it.show.updatedAt }
      NEWEST -> compareBy { it.episode?.firstAired?.toMillis() }
      RATING -> compareBy { it.show.rating }
      USER_RATING ->
        compareByDescending<ProgressListItem.Episode> { it.userRating != null }
          .thenBy { it.userRating }
          .thenBy { getTitle(it) }
      EPISODES_LEFT -> compareBy<ProgressListItem.Episode> { it.totalCount - it.watchedCount }.thenBy { getTitle(it) }
      RANDOM -> compareBy { Random.nextInt() }
      else -> throw IllegalStateException("Invalid sort order")
    }

  private fun sortDescending(sortOrder: SortOrder) =
    when (sortOrder) {
      NAME -> compareByDescending { getTitle(it) }
      RECENTLY_WATCHED -> compareByDescending { it.show.updatedAt }
      NEWEST -> compareByDescending { it.episode?.firstAired?.toMillis() }
      RATING -> compareByDescending { it.show.rating }
      USER_RATING ->
        compareByDescending<ProgressListItem.Episode> { it.userRating != null }
          .thenByDescending { it.userRating }
          .thenBy { getTitle(it) }
      EPISODES_LEFT -> compareByDescending<ProgressListItem.Episode> {
        it.totalCount - it.watchedCount
      }.thenBy { getTitle(it) }
      RANDOM -> compareBy { Random.nextInt() }
      else -> throw IllegalStateException("Invalid sort order")
    }

  private fun getTitle(item: ProgressListItem.Episode): String {
    val translatedTitle =
      if (item.translations?.show?.hasTitle == false) {
        null
      } else {
        item.translations?.show?.title
      }
    return (translatedTitle ?: item.show.titleNoThe).uppercase(Locale.ROOT)
  }
}
