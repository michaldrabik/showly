package com.michaldrabik.ui_my_movies.common.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.michaldrabik.ui_base.common.ListViewMode
import com.michaldrabik.ui_base.common.ListViewMode.LIST_NORMAL
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortType
import com.michaldrabik.ui_model.SortType.ASCENDING
import com.michaldrabik.ui_model.SortType.DESCENDING
import com.michaldrabik.ui_model.UpcomingFilter
import com.michaldrabik.ui_my_movies.R
import com.michaldrabik.ui_my_movies.common.recycler.CollectionListItem
import com.michaldrabik.ui_my_movies.databinding.ViewMoviesFiltersBinding

class CollectionMovieFiltersView : FrameLayout {

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  private val binding = ViewMoviesFiltersBinding.inflate(LayoutInflater.from(context), this)

  var onSortChipClicked: ((SortOrder, SortType) -> Unit)? = null
  var onGenreChipClicked: (() -> Unit)? = null
  var onFilterUpcomingClicked: (() -> Unit)? = null
  var onListViewModeClicked: (() -> Unit)? = null

  init {
    layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    clipChildren = false
    clipToPadding = false
  }

  var isUpcomingChipVisible: Boolean
    get() = binding.followedMoviesUpcomingChip.visibility == VISIBLE
    set(value) {
      binding.followedMoviesUpcomingChip.visibleIf(value)
    }

  fun bind(
    item: CollectionListItem.FiltersItem,
    viewMode: ListViewMode,
  ) {
    with(binding) {
      val sortIcon = when (item.sortType) {
        ASCENDING -> R.drawable.ic_arrow_alt_up
        DESCENDING -> R.drawable.ic_arrow_alt_down
      }
      followedMoviesCountText.text = "${item.count}"
      followedMoviesSortingChip.closeIcon = ContextCompat.getDrawable(context, sortIcon)
      followedMoviesSortingChip.text = context.getText(item.sortOrder.displayString)
      followedMoviesUpcomingChip.isChecked = item.upcoming.isActive()
      followedMoviesUpcomingChip.text = when (item.upcoming) {
        UpcomingFilter.OFF -> context.getString(R.string.textWatchlistIncoming)
        UpcomingFilter.UPCOMING -> context.getString(R.string.textWatchlistIncoming)
        UpcomingFilter.RELEASED -> context.getString(R.string.textMovieStatusReleased)
      }
      followedMoviesListViewChip.setChipIconResource(
        when (viewMode) {
          LIST_NORMAL -> R.drawable.ic_view_list
        },
      )

      followedMoviesGenresChip.isSelected = item.genres.isNotEmpty()
      followedMoviesGenresChip.text = when {
        item.genres.isEmpty() -> context.getString(R.string.textGenres).filter { it.isLetter() }
        item.genres.size == 1 -> context.getString(item.genres.first().displayName)
        item.genres.size == 2 -> "${context.getString(item.genres[0].displayName)}, " +
          context.getString(item.genres[1].displayName)
        else -> "${context.getString(item.genres[0].displayName)}, " +
          "${context.getString(item.genres[1].displayName)} + ${item.genres.size - 2}"
      }

      followedMoviesGenresChip.onClick { onGenreChipClicked?.invoke() }
      followedMoviesSortingChip.onClick { onSortChipClicked?.invoke(item.sortOrder, item.sortType) }
      followedMoviesUpcomingChip.onClick { onFilterUpcomingClicked?.invoke() }
      followedMoviesListViewChip.onClick { onListViewModeClicked?.invoke() }
    }
  }
}
