package com.michaldrabik.ui_progress_movies.calendar.cases.items

import com.michaldrabik.common.Config
import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.common.extensions.toLocalZone
import com.michaldrabik.repository.TranslationsRepository
import com.michaldrabik.repository.images.MovieImagesProvider
import com.michaldrabik.repository.movies.MoviesRepository
import com.michaldrabik.repository.settings.SettingsSpoilersRepository
import com.michaldrabik.ui_base.dates.DateFormatProvider
import com.michaldrabik.ui_base.utilities.extensions.removeDiacritics
import com.michaldrabik.ui_model.CalendarMode.PRESENT_FUTURE
import com.michaldrabik.ui_model.CalendarMode.RECENTS
import com.michaldrabik.ui_model.ImageType
import com.michaldrabik.ui_model.Translation
import com.michaldrabik.ui_progress_movies.calendar.helpers.filters.CalendarFilter
import com.michaldrabik.ui_progress_movies.calendar.helpers.groupers.CalendarGrouper
import com.michaldrabik.ui_progress_movies.calendar.helpers.sorter.CalendarSorter
import com.michaldrabik.ui_progress_movies.calendar.recycler.CalendarMovieListItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

abstract class CalendarMoviesItemsCase constructor(
  private val dispatchers: CoroutineDispatchers,
  private val moviesRepository: MoviesRepository,
  private val translationsRepository: TranslationsRepository,
  private val settingsSpoilersRepository: SettingsSpoilersRepository,
  private val imagesProvider: MovieImagesProvider,
  private val dateFormatProvider: DateFormatProvider,
) {

  abstract val filter: CalendarFilter
  abstract val grouper: CalendarGrouper
  abstract val sorter: CalendarSorter

  suspend fun loadItems(
    searchQuery: String? = "",
    withFilters: Boolean = true,
  ): List<CalendarMovieListItem> =
    withContext(dispatchers.IO) {
      val now = nowUtc().toLocalZone()
      val language = translationsRepository.getLanguage()
      val dateFormat = dateFormatProvider.loadFullDayFormat()
      val spoilers = settingsSpoilersRepository.getAll()

      val (myMovies, watchlistMovies) = awaitAll(
        async { moviesRepository.myMovies.loadAll() },
        async { moviesRepository.watchlistMovies.loadAll() },
      )

      val elements = (myMovies + watchlistMovies)
        .filter { filter.filter(now, it) }
        .sortedWith(sorter.sort())
        .map { movie ->
          async {
            var translation: Translation? = null
            if (language != Config.DEFAULT_LANGUAGE) {
              translation = translationsRepository.loadTranslation(movie, language, onlyLocal = true)
            }
            CalendarMovieListItem.MovieItem(
              movie = movie,
              image = imagesProvider.findCachedImage(movie, ImageType.POSTER),
              isWatched = myMovies.any { it.traktId == movie.traktId },
              isWatchlist = watchlistMovies.any { it.traktId == movie.traktId },
              dateFormat = dateFormat,
              translation = translation,
              spoilers = spoilers,
            )
          }
        }.awaitAll()

      val queryElements = filterByQuery(searchQuery ?: "", elements)
      val groupedItems = grouper.groupByTime(nowUtc(), queryElements)

      if (withFilters) {
        val filtersItem = when (this@CalendarMoviesItemsCase) {
          is CalendarMoviesFutureCase -> CalendarMovieListItem.Filters(PRESENT_FUTURE)
          is CalendarMoviesRecentsCase -> CalendarMovieListItem.Filters(RECENTS)
          else -> throw IllegalStateException()
        }
        listOf(filtersItem) + groupedItems
      } else {
        groupedItems
      }
    }

  private fun filterByQuery(
    query: String,
    items: List<CalendarMovieListItem.MovieItem>,
  ) = items.filter {
    it.movie.title
      .removeDiacritics()
      .contains(query, true) ||
      it.translation
        ?.title
        ?.removeDiacritics()
        ?.contains(query, true) == true ||
      it.movie.released
        ?.format(it.dateFormat)
        ?.contains(query, true) == true
  }
}
