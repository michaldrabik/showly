package com.michaldrabik.ui_my_movies.mymovies

import android.os.Bundle
import android.view.View
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.postDelayed
import androidx.core.view.updatePadding
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
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
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortOrder.DATE_ADDED
import com.michaldrabik.ui_model.SortOrder.NAME
import com.michaldrabik.ui_model.SortOrder.NEWEST
import com.michaldrabik.ui_model.SortOrder.RANDOM
import com.michaldrabik.ui_model.SortOrder.RATING
import com.michaldrabik.ui_model.SortOrder.RUNTIME
import com.michaldrabik.ui_model.SortOrder.USER_RATING
import com.michaldrabik.ui_model.SortType
import com.michaldrabik.ui_my_movies.R
import com.michaldrabik.ui_my_movies.databinding.FragmentMyMoviesBinding
import com.michaldrabik.ui_my_movies.filters.CollectionFiltersOrigin.MY_MOVIES
import com.michaldrabik.ui_my_movies.filters.genre.CollectionFiltersGenreBottomSheet
import com.michaldrabik.ui_my_movies.filters.genre.CollectionFiltersGenreBottomSheet.Companion.REQUEST_COLLECTION_FILTERS_GENRE
import com.michaldrabik.ui_my_movies.main.FollowedMoviesFragment
import com.michaldrabik.ui_my_movies.main.FollowedMoviesViewModel
import com.michaldrabik.ui_my_movies.mymovies.recycler.MyMoviesAdapter
import com.michaldrabik.ui_my_movies.mymovies.recycler.MyMoviesItem.Type.ALL_MOVIES_ITEM
import com.michaldrabik.ui_my_movies.mymovies.recycler.MyMoviesItem.Type.HEADER
import com.michaldrabik.ui_my_movies.mymovies.recycler.MyMoviesItem.Type.RECENT_MOVIES
import com.michaldrabik.ui_my_movies.mymovies.recycler.MyMoviesLayoutManagerProvider
import com.michaldrabik.ui_my_movies.mymovies.utilities.MyMoviesListItemDecoration
import com.michaldrabik.ui_navigation.java.NavigationArgs
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MyMoviesFragment :
  BaseFragment<MyMoviesViewModel>(R.layout.fragment_my_movies),
  OnScrollResetListener,
  OnSearchClickListener {

  @Inject lateinit var settings: SettingsViewModeRepository

  override val navigationId = R.id.followedMoviesFragment
  private val binding by viewBinding(FragmentMyMoviesBinding::bind)

  private val parentViewModel by viewModels<FollowedMoviesViewModel>({ requireParentFragment() })
  override val viewModel by viewModels<MyMoviesViewModel>()

  private var adapter: MyMoviesAdapter? = null
  private var layoutManager: LayoutManager? = null
  private var isSearching = false
  private val gridSpanSize by lazy { settings.tabletGridSpanSize }

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
      doAfterLaunch = { viewModel.loadMovies() },
    )
  }

  private fun setupRecycler() {
    layoutManager = MyMoviesLayoutManagerProvider.provideLayoutManger(
      context = requireContext(),
      viewMode = LIST_NORMAL,
      gridSpanSize = gridSpanSize,
    )
    adapter = MyMoviesAdapter(
      itemClickListener = { openMovieDetails(it.movie) },
      itemLongClickListener = { openMovieMenu(it.movie) },
      onSortOrderClickListener = ::openSortOrderDialog,
      onGenresClickListener = ::openGenresDialog,
      onListViewModeClickListener = { (requireParentFragment() as? FollowedMoviesFragment)?.openPremium() },
      missingImageListener = { item, force -> viewModel.loadMissingImage(item, force) },
      missingTranslationListener = { viewModel.loadMissingTranslation(it) },
      listChangeListener = {
        layoutManager?.scrollToPosition(0)
        (requireParentFragment() as FollowedMoviesFragment).resetTranslations()
      },
    )
    binding.myMoviesRecycler.apply {
      adapter = this@MyMoviesFragment.adapter
      layoutManager = this@MyMoviesFragment.layoutManager
      (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
      setHasFixedSize(true)
      addItemDecoration(MyMoviesListItemDecoration(requireContext(), R.dimen.spaceSmall))
    }
  }

  private fun setupInsets() {
    with(binding) {
      root.doOnApplyWindowInsets { _, insets, _, _ ->
        val tabletOffset = if (isTablet) dimenToPx(R.dimen.spaceMedium) else 0
        val systemInset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        myMoviesRoot.updatePadding(top = systemInset.top + tabletOffset)
        myMoviesRecycler.updatePadding(
          top = dimenToPx(R.dimen.myMoviesTabsViewPadding),
          bottom = dimenToPx(R.dimen.myMoviesBottomPadding) + systemInset.bottom,
        )
      }
    }
  }

  private fun render(uiState: MyMoviesUiState) {
    uiState.run {
      with(binding) {
        viewMode.let {
          if (adapter?.listViewMode != it) {
            val state = myMoviesRecycler.layoutManager?.onSaveInstanceState()
            layoutManager = MyMoviesLayoutManagerProvider.provideLayoutManger(requireContext(), it, gridSpanSize)
            adapter?.listViewMode = it
            myMoviesRecycler.let { recycler ->
              recycler.layoutManager = layoutManager
              recycler.adapter = adapter
              recycler.layoutManager?.onRestoreInstanceState(state)
            }
          }
        }
        items?.let {
          val notifyChange = resetScroll?.consume() == true
          adapter?.setItems(it, notifyChange)
          (layoutManager as? GridLayoutManager)?.withSpanSizeLookup { pos ->
            val item = adapter?.getItems()?.get(pos)
            when (item?.type) {
              RECENT_MOVIES, HEADER -> {
                when (viewMode) {
                  LIST_NORMAL -> if (isTablet) gridSpanSize else LISTS_GRID_SPAN
                }
              }
              ALL_MOVIES_ITEM -> 1
              null -> throw Error("Unsupported span size!")
            }
          }
          myMoviesEmptyView.root.fadeIf(showEmptyView && !isSearching)
        }
      }
    }
  }

  private fun openMovieDetails(movie: Movie) {
    (requireParentFragment() as? FollowedMoviesFragment)?.openMovieDetails(movie)
  }

  private fun openMovieMenu(movie: Movie) {
    (requireParentFragment() as? FollowedMoviesFragment)?.openMovieMenu(movie)
  }

  private fun openSortOrderDialog(
    order: SortOrder,
    type: SortType,
  ) {
    val options = listOf(NAME, RATING, USER_RATING, RUNTIME, NEWEST, DATE_ADDED, RANDOM)
    val args = SortOrderBottomSheet.createBundle(options, order, type)

    requireParentFragment().setFragmentResultListener(NavigationArgs.REQUEST_SORT_ORDER) { _, bundle ->
      val sortOrder = bundle.getSerializable(NavigationArgs.ARG_SELECTED_SORT_ORDER) as SortOrder
      val sortType = bundle.getSerializable(NavigationArgs.ARG_SELECTED_SORT_TYPE) as SortType
      viewModel.setSortOrder(sortOrder, sortType)
    }

    navigateTo(R.id.actionFollowedMoviesFragmentToSortOrder, args)
  }

  private fun openGenresDialog() {
    requireParentFragment().setFragmentResultListener(REQUEST_COLLECTION_FILTERS_GENRE) { _, _ ->
      viewModel.loadMovies()
    }
    val bundle = CollectionFiltersGenreBottomSheet.createBundle(MY_MOVIES)
    navigateToSafe(R.id.actionFollowedMoviesFragmentToGenres, bundle)
  }

  override fun onEnterSearch() {
    isSearching = true
    with(binding.myMoviesRecycler) {
      translationY = dimenToPx(R.dimen.myMoviesSearchLocalOffset).toFloat()
      smoothScrollToPosition(0)
    }
  }

  override fun onExitSearch() {
    isSearching = false
    with(binding.myMoviesRecycler) {
      translationY = 0F
      postDelayed(200) { layoutManager?.scrollToPosition(0) }
    }
  }

  override fun onScrollReset() = binding.myMoviesRecycler.scrollToPosition(0)

  override fun setupBackPressed() = Unit

  override fun onDestroyView() {
    adapter = null
    layoutManager = null
    super.onDestroyView()
  }
}
