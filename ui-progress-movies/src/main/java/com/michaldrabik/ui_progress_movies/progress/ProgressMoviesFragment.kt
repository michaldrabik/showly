package com.michaldrabik.ui_progress_movies.progress

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateMargins
import androidx.core.view.updatePadding
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.michaldrabik.repository.settings.SettingsViewModeRepository
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.common.OnScrollResetListener
import com.michaldrabik.ui_base.common.OnSearchClickListener
import com.michaldrabik.ui_base.common.WidgetsProvider
import com.michaldrabik.ui_base.common.sheets.sort_order.SortOrderBottomSheet
import com.michaldrabik.ui_base.utilities.NavigationHost
import com.michaldrabik.ui_base.utilities.events.Event
import com.michaldrabik.ui_base.utilities.extensions.add
import com.michaldrabik.ui_base.utilities.extensions.bump
import com.michaldrabik.ui_base.utilities.extensions.dimenToPx
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.ui_base.utilities.extensions.fadeIf
import com.michaldrabik.ui_base.utilities.extensions.fadeIn
import com.michaldrabik.ui_base.utilities.extensions.gone
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.withSpanSizeLookup
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_model.ProgressDateSelectionType.ALWAYS_ASK
import com.michaldrabik.ui_model.ProgressDateSelectionType.NOW
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortOrder.DATE_ADDED
import com.michaldrabik.ui_model.SortOrder.NAME
import com.michaldrabik.ui_model.SortOrder.NEWEST
import com.michaldrabik.ui_model.SortOrder.RANDOM
import com.michaldrabik.ui_model.SortOrder.RATING
import com.michaldrabik.ui_model.SortOrder.RUNTIME
import com.michaldrabik.ui_model.SortOrder.USER_RATING
import com.michaldrabik.ui_model.SortType
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_SELECTED_SORT_ORDER
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_SELECTED_SORT_TYPE
import com.michaldrabik.ui_navigation.java.NavigationArgs.REQUEST_SORT_ORDER
import com.michaldrabik.ui_progress_movies.R
import com.michaldrabik.ui_progress_movies.databinding.FragmentProgressMoviesBinding
import com.michaldrabik.ui_progress_movies.helpers.ProgressMoviesLayoutManagerProvider
import com.michaldrabik.ui_progress_movies.helpers.TopOverscrollAdapter
import com.michaldrabik.ui_progress_movies.main.MovieCheckActionUiEvent
import com.michaldrabik.ui_progress_movies.main.ProgressMoviesMainFragment
import com.michaldrabik.ui_progress_movies.main.ProgressMoviesMainViewModel
import com.michaldrabik.ui_progress_movies.main.RequestWidgetsUpdate
import com.michaldrabik.ui_progress_movies.progress.recycler.ProgressMovieListItem.FiltersItem
import com.michaldrabik.ui_progress_movies.progress.recycler.ProgressMovieListItem.HeaderItem
import com.michaldrabik.ui_progress_movies.progress.recycler.ProgressMovieListItem.MovieItem
import com.michaldrabik.ui_progress_movies.progress.recycler.ProgressMoviesAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.everything.android.ui.overscroll.IOverScrollDecor
import me.everything.android.ui.overscroll.IOverScrollState.STATE_BOUNCE_BACK
import me.everything.android.ui.overscroll.IOverScrollState.STATE_DRAG_START_SIDE
import me.everything.android.ui.overscroll.OverScrollBounceEffectDecoratorBase
import me.everything.android.ui.overscroll.VerticalOverScrollBounceEffectDecorator
import javax.inject.Inject

@AndroidEntryPoint
class ProgressMoviesFragment :
  BaseFragment<ProgressMoviesViewModel>(R.layout.fragment_progress_movies),
  OnSearchClickListener,
  OnScrollResetListener {

  private companion object {
    const val OVERSCROLL_OFFSET = 225F
    const val OVERSCROLL_OFFSET_TRANSLATION = 4.5F
  }

  @Inject lateinit var settings: SettingsViewModeRepository

  private val parentViewModel by viewModels<ProgressMoviesMainViewModel>({ requireParentFragment() })
  override val viewModel by viewModels<ProgressMoviesViewModel>()

  private val binding by viewBinding(FragmentProgressMoviesBinding::bind)

  private var adapter: ProgressMoviesAdapter? = null
  private var layoutManager: LayoutManager? = null
  private var statusBarHeight = 0
  private var overscroll: IOverScrollDecor? = null
  private var overscrollJob: Job? = null
  private var overscrollEnabled = true
  private var isSearching = false

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)
    setupView()
    setupRecycler()
    setupInsets()

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        with(parentViewModel) {
          launch { uiState.collect { viewModel.onParentState(it) } }
        }
        with(viewModel) {
          launch { uiState.collect { render(it) } }
          launch { messageFlow.collect { showSnack(it) } }
          launch { eventFlow.collect { handleEvent(it) } }
        }
      }
    }
  }

  private fun setupView() {
    with(binding) {
      progressMoviesEmptyView.progressMoviesEmptyTraktButton.onClick { requireMainFragment().openTraktSync() }
      progressMoviesEmptyView.progressMoviesEmptyDiscoverButton.onClick {
        (requireActivity() as NavigationHost).navigateToDiscover()
      }
    }
  }

  private fun setupRecycler() {
    val gridSpanSize = settings.tabletGridSpanSize
    layoutManager = ProgressMoviesLayoutManagerProvider.provideLayoutManger(requireContext(), gridSpanSize)
    (layoutManager as? GridLayoutManager)?.run {
      withSpanSizeLookup { position ->
        when (adapter?.getItems()?.get(position)) {
          is HeaderItem -> gridSpanSize
          is FiltersItem -> gridSpanSize
          is MovieItem -> 1
          else -> throw IllegalStateException()
        }
      }
    }
    adapter = ProgressMoviesAdapter(
      itemClickListener = { requireMainFragment().openMovieDetails(it.movie) },
      itemLongClickListener = { requireMainFragment().openMovieMenu(it.movie) },
      sortChipClickListener = ::openSortOrderDialog,
      missingImageListener = viewModel::findMissingImage,
      missingTranslationListener = viewModel::findMissingTranslation,
      checkClickListener = { viewModel.onMovieChecked(it.movie) },
      listChangeListener = {
        requireMainFragment().resetTranslations()
        layoutManager?.scrollToPosition(0)
      },
    )
    binding.progressMoviesMainRecycler.apply {
      adapter = this@ProgressMoviesFragment.adapter
      layoutManager = this@ProgressMoviesFragment.layoutManager
      (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
      setHasFixedSize(true)
    }
  }

  private fun setupOverscroll() {
    if (overscroll != null || view == null) {
      return
    }
    overscroll = VerticalOverScrollBounceEffectDecorator(
      TopOverscrollAdapter(binding.progressMoviesMainRecycler),
      1F,
      OverScrollBounceEffectDecoratorBase.DEFAULT_TOUCH_DRAG_MOVE_RATIO_BCK,
      OverScrollBounceEffectDecoratorBase.DEFAULT_DECELERATE_FACTOR,
    ).apply {
      setOverScrollUpdateListener { _, state, offset ->
        binding.progressMoviesOverscroll?.run {
          if (offset > 0) {
            val value = (offset / OVERSCROLL_OFFSET).coerceAtMost(1F)
            val valueTranslation = offset / OVERSCROLL_OFFSET_TRANSLATION
            if (value >= 1F) {
              onOverscrollReach()
            } else {
              onOverscrollCancel()
            }
            when (state) {
              STATE_DRAG_START_SIDE -> {
                alpha = value
                scaleX = value
                scaleY = value
                translationY = valueTranslation
                overscrollEnabled = true
              }
              STATE_BOUNCE_BACK -> {
                alpha = value
                scaleX = value
                scaleY = value
                translationY = valueTranslation
                if (offset >= OVERSCROLL_OFFSET &&
                  overscrollEnabled &&
                  binding.progressMoviesOverscrollProgress.progress >= 100
                ) {
                  overscrollEnabled = false
                  viewModel.startTraktSync()
                }
              }
            }
          } else {
            alpha = 0F
            scaleX = 0F
            scaleY = 0F
            translationY = 0F
            onOverscrollCancel()
          }
        }
      }
    }
  }

  private fun onOverscrollReach() {
    if (overscrollJob != null) return
    overscrollJob = viewLifecycleOwner.lifecycleScope.launch {
      repeat(100) {
        val progress = it + 1
        binding.progressMoviesOverscrollProgress.progress = progress
        if (progress >= 100) {
          binding.progressMoviesOverscroll.bump(200)
        }
        delay(5)
      }
    }
  }

  private fun onOverscrollCancel() {
    overscrollJob?.cancel()
    overscrollJob = null
    binding.progressMoviesOverscrollProgress.progress = 0
  }

  private fun setupInsets() {
    with(binding) {
      root.doOnApplyWindowInsets { _, insets, padding, _ ->
        val tabletOffset = if (isTablet) dimenToPx(R.dimen.spaceMedium) else 0
        val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())

        statusBarHeight = systemInsets.top + tabletOffset
        progressMoviesMainRecycler.updatePadding(
          top = statusBarHeight + dimenToPx(R.dimen.progressMoviesTabsViewPadding),
          bottom = systemInsets.bottom + dimenToPx(R.dimen.bottomNavigationHeightPadded),
        )
        (progressMoviesEmptyView.rootLayout.layoutParams as ViewGroup.MarginLayoutParams)
          .updateMargins(top = statusBarHeight + dimenToPx(R.dimen.spaceBig))
        (progressMoviesOverscroll.layoutParams as ViewGroup.MarginLayoutParams)
          .updateMargins(top = statusBarHeight + dimenToPx(R.dimen.progressMoviesOverscrollPadding))
      }
    }
  }

  private fun openSortOrderDialog(
    order: SortOrder,
    type: SortType,
  ) {
    val options = listOf(NAME, RATING, USER_RATING, RUNTIME, NEWEST, DATE_ADDED, RANDOM)
    val args = SortOrderBottomSheet.createBundle(options, order, type)

    requireParentFragment().setFragmentResultListener(REQUEST_SORT_ORDER) { _, bundle ->
      val sortOrder = bundle.getSerializable(ARG_SELECTED_SORT_ORDER) as SortOrder
      val sortType = bundle.getSerializable(ARG_SELECTED_SORT_TYPE) as SortType
      viewModel.setSortOrder(sortOrder, sortType)
    }

    navigateTo(R.id.actionProgressMoviesFragmentToSortOrder, args)
  }

  override fun onEnterSearch() {
    isSearching = true

    binding.progressMoviesMainRecycler.translationY = dimenToPx(R.dimen.progressMoviesSearchLocalOffset).toFloat()
    binding.progressMoviesMainRecycler.smoothScrollToPosition(0)

    overscroll?.detach()
    overscroll = null
  }

  override fun onExitSearch() {
    isSearching = false

    binding.progressMoviesMainRecycler.translationY = 0F
    binding.progressMoviesMainRecycler.smoothScrollToPosition(0)

    setupOverscroll()
  }

  override fun onScrollReset() = binding.progressMoviesMainRecycler.smoothScrollToPosition(0)

  private fun handleEvent(event: Event<*>) {
    when (event) {
      is MovieCheckActionUiEvent -> {
        when (event.dateSelectionType) {
          ALWAYS_ASK -> requireMainFragment().openDateSelectionDialog(event.movie)
          NOW -> parentViewModel.setWatchedMovie(event.movie)
        }
      }
      is RequestWidgetsUpdate -> {
        (requireAppContext() as WidgetsProvider).requestMoviesWidgetsUpdate()
      }
    }
  }

  private fun render(uiState: ProgressMoviesUiState) {
    uiState.run {
      items?.let {
        val resetScroll = scrollReset?.consume() == true
        adapter?.setItems(it, resetScroll)
        renderFiltersEmpty(uiState)
        binding.progressMoviesEmptyView.rootLayout.fadeIf(items.isEmpty() && !isSearching)
        binding.progressMoviesMainRecycler
          .fadeIn(
            duration = 200,
            withHardware = true,
          ).add(animations)
      }
      isOverScrollEnabled.let {
        if (it) {
          setupOverscroll()
        } else {
          overscroll?.detach()
          overscroll = null
        }
      }
      sortOrder?.let { event -> event.consume()?.let { openSortOrderDialog(it.first, it.second) } }
    }
  }

  private fun renderFiltersEmpty(uiState: ProgressMoviesUiState) {
    val items = uiState.items ?: emptyList()
    if (isSearching) {
      binding.progressMoviesEmptyFilterView.fadeIf(items.isEmpty(), duration = 200)
      return
    }
    binding.progressMoviesEmptyFilterView.gone()
  }

  private fun requireMainFragment() = (requireParentFragment() as ProgressMoviesMainFragment)

  override fun setupBackPressed() = Unit

  override fun onDestroyView() {
    overscrollJob?.cancel()
    overscrollJob = null
    overscroll = null
    adapter = null
    layoutManager = null
    super.onDestroyView()
  }
}
