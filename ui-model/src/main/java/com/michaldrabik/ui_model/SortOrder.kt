package com.michaldrabik.ui_model

enum class SortOrder(
  val slug: String,
  val displayString: Int,
) {
  RANK("rank", R.string.textSortRank),
  NAME("title", R.string.textSortName),
  NEWEST("released", R.string.textSortNewest),
  RUNTIME("runtime", R.string.textSortRuntime),
  RATING("percentage", R.string.textSortRated),
  USER_RATING("user_rating", R.string.textSortRatedUser),
  DATE_ADDED("added", R.string.textSortDateAdded),
  DATE_UPDATED("updated", R.string.textSortDateUpdated),
  RECENTLY_WATCHED("recently_watched", R.string.textSortRecentlyWatched),
  EPISODES_LEFT("episodes_left", R.string.textSortEpisodesLeft),
  RANDOM("random", R.string.textSortRandom),
  ;

  companion object {
    fun fromSlug(slug: String) = entries.firstOrNull { it.slug == slug }
  }
}
