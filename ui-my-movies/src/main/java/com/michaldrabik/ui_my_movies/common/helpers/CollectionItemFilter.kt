package com.michaldrabik.ui_my_movies.common.helpers

import com.michaldrabik.common.extensions.nowUtcDay
import com.michaldrabik.ui_base.utilities.extensions.removeDiacritics
import com.michaldrabik.ui_model.UpcomingFilter
import com.michaldrabik.ui_my_movies.common.recycler.CollectionListItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectionItemFilter @Inject constructor() {

  fun filterUpcoming(
    item: CollectionListItem,
    upcomingFilter: UpcomingFilter,
  ): Boolean {
    val releasedAt = item.movie.released
    return when (upcomingFilter) {
      UpcomingFilter.OFF -> true
      UpcomingFilter.UPCOMING -> {
        val nowUtcDay = nowUtcDay()
        val isUpcomingDay = releasedAt != null && releasedAt.toEpochDay() > nowUtcDay.toEpochDay()
        val isUpcomingYear = releasedAt == null && item.movie.year > nowUtcDay.year
        isUpcomingDay || isUpcomingYear
      }
      UpcomingFilter.RELEASED -> {
        val nowUtcDay = nowUtcDay()
        val isReleasedDay = releasedAt != null && releasedAt.toEpochDay() < nowUtcDay.toEpochDay()
        val isReleasedYear = releasedAt == null && item.movie.year > 0 && item.movie.year < nowUtcDay.year
        isReleasedDay || isReleasedYear
      }
    }
  }

  fun filterByQuery(
    item: CollectionListItem.MovieItem,
    query: String,
  ): Boolean =
    item.movie.title
      .removeDiacritics()
      .contains(query, true) ||
      item.translation
        ?.title
        ?.removeDiacritics()
        ?.contains(query, true) == true

  fun filterGenres(
    item: CollectionListItem,
    genres: List<String>,
  ): Boolean {
    if (genres.isEmpty()) {
      return true
    }
    return item.movie.genres.any { genre -> genre.lowercase() in genres }
  }
}
