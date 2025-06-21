package com.michaldrabik.ui_my_shows.myshows

import android.os.Bundle
import android.view.View
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.postDelayed
import androidx.core.view.updatePadding
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.michaldrabik.common.Config.LISTS_GRID_SPAN
import com.michaldrabik.repository.settings.SettingsViewModeRepository
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.common.ListViewMode.LIST_NORMAL
import com.michaldrabik.ui_base.common.OnScrollResetListener
import com.michaldrabik.ui_base.common.OnSearchClickListener
import com.michaldrabik.ui_base.common.sheets.sort_order.SortOrderBottomSheet
import com.michaldrabik.ui_base.utilities.extensions.dimenToPx
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.ui_base.utilities.extensions.fadeIf
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.navigateToSafe
import com.michaldrabik.ui_base.utilities.extensions.withSpanSizeLookup
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_model.MyShowsSection
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortOrder.DATE_ADDED
import com.michaldrabik.ui_model.SortOrder.NAME
import com.michaldrabik.ui_model.SortOrder.NEWEST
import com.michaldrabik.ui_model.SortOrder.RANDOM
import com.michaldrabik.ui_model.SortOrder.RATING
import com.michaldrabik.ui_model.SortOrder.RECENTLY_WATCHED
import com.michaldrabik.ui_model.SortOrder.USER_RATING
import com.michaldrabik.ui_model.SortType
import com.michaldrabik.ui_my_shows.R
import com.michaldrabik.ui_my_shows.common.filters.CollectionFiltersOrigin.MY_SHOWS
import com.michaldrabik.ui_my_shows.common.filters.genre.CollectionFiltersGenreBottomSheet
import com.michaldrabik.ui_my_shows.common.filters.genre.CollectionFiltersGenreBottomSheet.Companion.REQUEST_COLLECTION_FILTERS_GENRE
import com.michaldrabik.ui_my_shows.common.filters.network.CollectionFiltersNetworkBottomSheet
import com.michaldrabik.ui_my_shows.common.filters.network.CollectionFiltersNetworkBottomSheet.Companion.REQUEST_COLLECTION_FILTERS_NETWORK
import com.michaldrabik.ui_my_shows.databinding.FragmentMyShowsBinding
import com.michaldrabik.ui_my_shows.main.FollowedShowsFragment
import com.michaldrabik.ui_my_shows.main.FollowedShowsViewModel
import com.michaldrabik.ui_my_shows.myshows.recycler.MyShowsAdapter
import com.michaldrabik.ui_my_shows.myshows.recycler.MyShowsItem
import com.michaldrabik.ui_my_shows.myshows.recycler.MyShowsItem.Type.ALL_SHOWS_HEADER
import com.michaldrabik.ui_my_shows.myshows.recycler.MyShowsItem.Type.ALL_SHOWS_ITEM
import com.michaldrabik.ui_my_shows.myshows.recycler.MyShowsItem.Type.RECENT_SHOWS
import com.michaldrabik.ui_my_shows.myshows.recycler.MyShowsLayoutManagerProvider
import com.michaldrabik.ui_my_shows.utilities.MyShowsListItemDecoration
import com.michaldrabik.ui_navigation.java.NavigationArgs
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MyShowsFragment :
  BaseFragment<MyShowsViewModel>(R.layout.fragment_my_shows),
  OnScrollResetListener,
  OnSearchClickListener {

  @Inject lateinit var settings: SettingsViewModeRepository

  override val navigationId = R.id.followedShowsFragment
  private val binding by viewBinding(FragmentMyShowsBinding::bind)

  private val parentViewModel by viewModels<FollowedShowsViewModel>({ requireParentFragment() })
  override val viewModel by viewModels<MyShowsViewModel>()

  private var adapter: MyShowsAdapter? = null
  private var layoutManager: LayoutManager? = null
  private var isSearching = false
  private val tabletGridSpanSize by lazy { settings.tabletGridSpanSize }

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)
    setupInsets()
    setupRecycler()

    launchAndRepeatStarted(
      { parentViewModel.uiState.collect { viewModel.onParentState(it) } },
      { viewModel.uiState.collect { render(it) } },
      doAfterLaunch = { viewModel.loadShows() },
    )
  }

  private fun setupRecycler() {
    layoutManager = MyShowsLayoutManagerProvider.provideLayoutManger(requireContext(), LIST_NORMAL, tabletGridSpanSize)
    adapter = MyShowsAdapter(
      itemClickListener = { openShowDetails(it.show) },
      itemLongClickListener = { item -> openShowMenu(item.show) },
      onSortOrderClickListener = { section, order, type -> openSortOrderDialog(section, order, type) },
      onTypeClickListener = { navigateToSafe(R.id.actionFollowedShowsFragmentToMyShowsFilters) },
      onListViewModeClickListener = { (requireParentFragment() as? FollowedShowsFragment)?.openPremium() },
      onNetworksClickListener = ::openNetworksDialog,
      onGenresClickListener = ::openGenresDialog,
      missingImageListener = { item, force -> viewModel.loadMissingImage(item as MyShowsItem, force) },
      missingTranslationListener = { viewModel.loadMissingTranslation(it as MyShowsItem) },
      listChangeListener = {
        layoutManager?.scrollToPosition(0)
        (requireParentFragment() as FollowedShowsFragment).resetTranslations()
      },
    ).apply {
      stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }
    binding.myShowsRecycler.apply {
      adapter = this@MyShowsFragment.adapter
      layoutManager = this@MyShowsFragment.layoutManager
      (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
      setHasFixedSize(true)
      addItemDecoration(MyShowsListItemDecoration(requireContext(), R.dimen.spaceSmall))
    }
  }

  private fun setupInsets() {
    with(binding) {
      root.doOnApplyWindowInsets { view, insets, _, _ ->
        val tabletOffset = if (isTablet) dimenToPx(R.dimen.spaceMedium) else 0
        val systemInset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        myShowsRoot.updatePadding(top = systemInset.top + tabletOffset)
        myShowsRecycler.updatePadding(
          top = dimenToPx(R.dimen.myShowsTabsViewPadding),
          bottom = dimenToPx(R.dimen.myShowsBottomPadding) + systemInset.bottom,
        )
      }
    }
  }

  private fun render(uiState: MyShowsUiState) {
    uiState.run {
      with(binding) {
        viewMode.let {
          if (adapter?.listViewMode != it) {
            val state = myShowsRecycler.layoutManager?.onSaveInstanceState()
            layoutManager = MyShowsLayoutManagerProvider.provideLayoutManger(requireContext(), it, tabletGridSpanSize)
            adapter?.listViewMode = it
            myShowsRecycler.let { recycler ->
              recycler.layoutManager = layoutManager
              recycler.adapter = adapter
              recycler.layoutManager?.onRestoreInstanceState(state)
            }
          }
        }
        items?.let { items ->
          val notifyChangeList = resetScrollMap?.consume()
          adapter?.setItems(items, notifyChangeList)
          (layoutManager as? GridLayoutManager)?.withSpanSizeLookup { pos ->
            val item = adapter?.getItems()?.get(pos)
            when (item?.type) {
              RECENT_SHOWS, ALL_SHOWS_HEADER -> {
                when (viewMode) {
                  LIST_NORMAL -> if (isTablet) tabletGridSpanSize else LISTS_GRID_SPAN
                }
              }
              ALL_SHOWS_ITEM -> 1
              null -> throw Error("Unsupported span size!")
            }
          }
          myShowsEmptyView.root.fadeIf(showEmptyView && !isSearching)
        }
      }
    }
  }

  private fun openShowDetails(show: Show) {
    (requireParentFragment() as? FollowedShowsFragment)?.openShowDetails(show)
  }

  private fun openShowMenu(show: Show) {
    (requireParentFragment() as? FollowedShowsFragment)?.openShowMenu(show)
  }

  @Suppress("DEPRECATION")
  private fun openSortOrderDialog(
    section: MyShowsSection,
    order: SortOrder,
    type: SortType,
  ) {
    val options = listOf(NAME, RATING, USER_RATING, NEWEST, DATE_ADDED, RECENTLY_WATCHED, RANDOM)
    val key = NavigationArgs.requestSortOrderSection(section.name)
    val args = SortOrderBottomSheet.createBundle(options, order, type, key)

    requireParentFragment().setFragmentResultListener(key) { requestKey, bundle ->
      val sortOrder = bundle.getSerializable(NavigationArgs.ARG_SELECTED_SORT_ORDER) as SortOrder
      val sortType = bundle.getSerializable(NavigationArgs.ARG_SELECTED_SORT_TYPE) as SortType
      MyShowsSection
        .values()
        .find { NavigationArgs.requestSortOrderSection(it.name) == requestKey }
        ?.let { viewModel.setSortOrder(sortOrder, sortType) }
    }

    navigateTo(R.id.actionFollowedShowsFragmentToSortOrder, args)
  }

  private fun openNetworksDialog() {
    requireParentFragment().setFragmentResultListener(REQUEST_COLLECTION_FILTERS_NETWORK) { _, _ ->
      viewModel.loadShows()
    }

    val bundle = CollectionFiltersNetworkBottomSheet.createBundle(MY_SHOWS)
    navigateToSafe(R.id.actionFollowedShowsFragmentToNetworks, bundle)
  }

  private fun openGenresDialog() {
    requireParentFragment().setFragmentResultListener(REQUEST_COLLECTION_FILTERS_GENRE) { _, _ ->
      viewModel.loadShows()
    }

    val bundle = CollectionFiltersGenreBottomSheet.createBundle(MY_SHOWS)
    navigateToSafe(R.id.actionFollowedShowsFragmentToGenres, bundle)
  }

  override fun onEnterSearch() {
    isSearching = true
    with(binding) {
      myShowsRecycler.translationY = dimenToPx(R.dimen.myShowsSearchLocalOffset).toFloat()
      myShowsRecycler.smoothScrollToPosition(0)
    }
  }

  override fun onExitSearch() {
    isSearching = false
    with(binding.myShowsRecycler) {
      translationY = 0F
      postDelayed(200) { layoutManager?.scrollToPosition(0) }
    }
  }

  override fun onScrollReset() = binding.myShowsRecycler.scrollToPosition(0)

  override fun setupBackPressed() = Unit

  override fun onDestroyView() {
    adapter = null
    layoutManager = null
    super.onDestroyView()
  }
}
