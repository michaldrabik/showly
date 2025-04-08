package com.michaldrabik.repository.shows

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShowsRepository @Inject constructor(
  val discoverShows: DiscoverShowsRepository,
  val myShows: MyShowsRepository,
  val watchlistShows: WatchlistShowsRepository,
  val hiddenShows: HiddenShowsRepository,
  val relatedShows: RelatedShowsRepository,
  val detailsShow: ShowDetailsRepository,
) {

  suspend fun loadCollection(skipHidden: Boolean = false) =
    coroutineScope {
      val async1 = async { myShows.loadAll() }
      val async2 = async { watchlistShows.loadAll() }
      val async3 = async { if (skipHidden) emptyList() else hiddenShows.loadAll() }
      val (my, watchlist, hidden) = awaitAll(async1, async2, async3)
      (my + watchlist + hidden).distinctBy { it.traktId }
    }
}
