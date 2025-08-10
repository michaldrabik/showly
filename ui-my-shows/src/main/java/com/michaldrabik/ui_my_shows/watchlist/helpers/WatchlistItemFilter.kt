package com.michaldrabik.ui_my_shows.watchlist.helpers

import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.ui_base.utilities.extensions.removeDiacritics
import com.michaldrabik.ui_model.UpcomingFilter
import com.michaldrabik.ui_model.UpcomingFilter.OFF
import com.michaldrabik.ui_model.UpcomingFilter.RELEASED
import com.michaldrabik.ui_model.UpcomingFilter.UPCOMING
import com.michaldrabik.ui_my_shows.common.recycler.CollectionListItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchlistItemFilter @Inject constructor() {

  fun filterUpcoming(
    item: CollectionListItem,
    upcomingFilter: UpcomingFilter,
  ): Boolean {
    val releasedAt = item.getReleaseDate()
    return when (upcomingFilter) {
      OFF -> true
      UPCOMING -> releasedAt != null && releasedAt.isAfter(nowUtc())
      RELEASED -> releasedAt != null && releasedAt.isBefore(nowUtc())
    }
  }

  fun filterNetworks(
    item: CollectionListItem,
    networks: List<String>,
  ): Boolean {
    if (networks.isEmpty()) {
      return true
    }
    return item.show.network in networks
  }

  fun filterGenres(
    item: CollectionListItem,
    genres: List<String>,
  ): Boolean {
    if (genres.isEmpty()) {
      return true
    }
    return item.show.genres.any { genre -> genre.lowercase() in genres }
  }

  fun filterByQuery(
    item: CollectionListItem.ShowItem,
    query: String,
  ): Boolean =
    item.show.title
      .removeDiacritics()
      .contains(query, true) ||
      item.translation
        ?.title
        ?.removeDiacritics()
        ?.contains(query, true) == true
}
