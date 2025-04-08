package com.michaldrabik.ui_my_shows.common.views

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
import com.michaldrabik.ui_my_shows.R
import com.michaldrabik.ui_my_shows.common.recycler.CollectionListItem
import com.michaldrabik.ui_my_shows.databinding.ViewShowsFiltersBinding

class CollectionShowFiltersView : FrameLayout {

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  private val binding = ViewShowsFiltersBinding.inflate(LayoutInflater.from(context), this)

  var onSortChipClicked: ((SortOrder, SortType) -> Unit)? = null
  var onFilterUpcomingClicked: (() -> Unit)? = null
  var onListViewModeClicked: (() -> Unit)? = null
  var onNetworksChipClick: (() -> Unit)? = null
  var onGenresChipClick: (() -> Unit)? = null

  init {
    layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    clipChildren = false
    clipToPadding = false
  }

  var isUpcomingChipVisible: Boolean
    get() = binding.followedShowsUpcomingChip.visibility == VISIBLE
    set(value) {
      binding.followedShowsUpcomingChip.visibleIf(value)
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

      followedShowsCountText.text = "${item.count}"

      followedShowsSortingChip.closeIcon = ContextCompat.getDrawable(context, sortIcon)
      followedShowsSortingChip.text = context.getText(item.sortOrder.displayString)

      followedShowsNetworksChip.isSelected = item.networks.isNotEmpty()
      followedShowsNetworksChip.text = when {
        item.networks.isEmpty() -> context.getString(R.string.textNetworks).filter { it.isLetter() }
        item.networks.size == 1 -> item.networks[0].channels.first()
        else -> throw IllegalStateException()
      }

      followedShowsGenresChip.isSelected = item.genres.isNotEmpty()
      followedShowsGenresChip.text = when {
        item.genres.isEmpty() -> context.getString(R.string.textGenres).filter { it.isLetter() }
        item.genres.size == 1 -> context.getString(item.genres.first().displayName)
        item.genres.size == 2 -> "${context.getString(item.genres[0].displayName)}, " +
          context.getString(item.genres[1].displayName)
        else -> "${context.getString(item.genres[0].displayName)}, " +
          "${context.getString(item.genres[1].displayName)} + ${item.genres.size - 2}"
      }

      followedShowsUpcomingChip.isChecked = item.upcoming.isActive()
      followedShowsUpcomingChip.text = when (item.upcoming) {
        UpcomingFilter.OFF -> context.getString(R.string.textWatchlistIncoming)
        UpcomingFilter.UPCOMING -> context.getString(R.string.textWatchlistIncoming)
        UpcomingFilter.RELEASED -> context.getString(R.string.textMovieStatusReleased)
      }
      followedShowsListViewChip.setChipIconResource(
        when (viewMode) {
          LIST_NORMAL -> R.drawable.ic_view_list
        },
      )

      followedShowsSortingChip.onClick { onSortChipClicked?.invoke(item.sortOrder, item.sortType) }
      followedShowsUpcomingChip.onClick { onFilterUpcomingClicked?.invoke() }
      followedShowsListViewChip.onClick { onListViewModeClicked?.invoke() }
      followedShowsNetworksChip.onClick { onNetworksChipClick?.invoke() }
      followedShowsGenresChip.onClick { onGenresChipClick?.invoke() }
    }
  }
}
