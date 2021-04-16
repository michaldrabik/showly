package com.michaldrabik.data_local.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class Settings(
  @PrimaryKey @ColumnInfo(name = "id") var id: Long = 1,
  @ColumnInfo(name = "is_initial_run", defaultValue = "0") var isInitialRun: Boolean,
  @ColumnInfo(name = "push_notifications_enabled", defaultValue = "1") var pushNotificationsEnabled: Boolean,
  @ColumnInfo(name = "episodes_notifications_enabled", defaultValue = "1") var episodesNotificationsEnabled: Boolean,
  @ColumnInfo(name = "episodes_notifications_delay", defaultValue = "0") var episodesNotificationsDelay: Long,
  @ColumnInfo(name = "my_shows_recent_amount", defaultValue = "6") var myShowsRecentsAmount: Int,
  @ColumnInfo(name = "my_shows_running_sort_by", defaultValue = "NAME") var myShowsRunningSortBy: String,
  @ColumnInfo(name = "my_shows_incoming_sort_by", defaultValue = "NAME") var myShowsIncomingSortBy: String,
  @ColumnInfo(name = "my_shows_ended_sort_by", defaultValue = "NAME") var myShowsEndedSortBy: String,
  @ColumnInfo(name = "my_shows_all_sort_by", defaultValue = "NAME") var myShowsAllSortBy: String,
  @ColumnInfo(name = "my_shows_running_is_collapsed", defaultValue = "0") var myShowsRunningIsCollapsed: Boolean,
  @ColumnInfo(name = "my_shows_incoming_is_collapsed", defaultValue = "0") var myShowsIncomingIsCollapsed: Boolean,
  @ColumnInfo(name = "my_shows_ended_is_collapsed", defaultValue = "0") var myShowsEndedIsCollapsed: Boolean,
  @ColumnInfo(name = "my_shows_running_is_enabled", defaultValue = "1") var myShowsRunningIsEnabled: Boolean,
  @ColumnInfo(name = "my_shows_incoming_is_enabled", defaultValue = "1") var myShowsIncomingIsEnabled: Boolean,
  @ColumnInfo(name = "my_shows_ended_is_enabled", defaultValue = "1") var myShowsEndedIsEnabled: Boolean,
  @ColumnInfo(name = "my_shows_recent_is_enabled", defaultValue = "1") var myShowsRecentIsEnabled: Boolean,
  @ColumnInfo(name = "see_later_shows_sort_by", defaultValue = "NAME") var seeLaterShowsSortBy: String,
  @ColumnInfo(name = "show_anticipated_shows", defaultValue = "1") var showAnticipatedShows: Boolean,
  @ColumnInfo(name = "discover_filter_genres", defaultValue = "") var discoverFilterGenres: String,
  @ColumnInfo(name = "discover_filter_feed", defaultValue = "HOT") var discoverFilterFeed: String,
  @ColumnInfo(name = "trakt_sync_schedule", defaultValue = "OFF") var traktSyncSchedule: String,
  @ColumnInfo(name = "trakt_quick_sync_enabled", defaultValue = "0") var traktQuickSyncEnabled: Boolean,
  @ColumnInfo(name = "trakt_quick_remove_enabled", defaultValue = "0") var traktQuickRemoveEnabled: Boolean,
  @ColumnInfo(name = "watchlist_sort_by", defaultValue = "NAME") var watchlistSortBy: String,
  @ColumnInfo(name = "archive_shows_sort_by", defaultValue = "NAME") var archiveShowsSortBy: String,
  @ColumnInfo(name = "archive_shows_include_statistics", defaultValue = "1") var archiveShowsIncludeStatistics: Boolean,
  @ColumnInfo(name = "special_seasons_enabled", defaultValue = "0") var specialSeasonsEnabled: Boolean,
  @ColumnInfo(name = "show_anticipated_movies", defaultValue = "0") var showAnticipatedMovies: Boolean,
  @ColumnInfo(name = "discover_movies_filter_genres", defaultValue = "") var discoverMoviesFilterGenres: String,
  @ColumnInfo(name = "discover_movies_filter_feed", defaultValue = "HOT") var discoverMoviesFilterFeed: String,
  @ColumnInfo(name = "my_movies_all_sort_by", defaultValue = "NAME") var myMoviesAllSortBy: String,
  @ColumnInfo(name = "see_later_movies_sort_by", defaultValue = "NAME") var seeLaterMoviesSortBy: String,
  @ColumnInfo(name = "progress_movies_sort_by", defaultValue = "NAME") var progressMoviesSortBy: String,
  @ColumnInfo(name = "show_collection_shows", defaultValue = "1") var showCollectionShows: Boolean,
  @ColumnInfo(name = "show_collection_movies", defaultValue = "1") var showCollectionMovies: Boolean,
  @ColumnInfo(name = "widgets_show_label", defaultValue = "1") var widgetsShowLabel: Boolean,
  @ColumnInfo(name = "my_movies_recent_is_enabled", defaultValue = "1") var myMoviesRecentIsEnabled: Boolean,
  @ColumnInfo(name = "quick_rate_enabled", defaultValue = "0") var quickRateEnabled: Boolean,
  @ColumnInfo(name = "lists_sort_by", defaultValue = "DATE_UPDATED") var listsSortBy: String
)