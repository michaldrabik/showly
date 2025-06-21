package com.michaldrabik.ui_progress.progress

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
import com.michaldrabik.ui_base.utilities.extensions.navigateToSafe
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.extensions.withSpanSizeLookup
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_model.ProgressDateSelectionType.ALWAYS_ASK
import com.michaldrabik.ui_model.ProgressDateSelectionType.NOW
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortOrder.EPISODES_LEFT
import com.michaldrabik.ui_model.SortOrder.NAME
import com.michaldrabik.ui_model.SortOrder.NEWEST
import com.michaldrabik.ui_model.SortOrder.RANDOM
import com.michaldrabik.ui_model.SortOrder.RATING
import com.michaldrabik.ui_model.SortOrder.RECENTLY_WATCHED
import com.michaldrabik.ui_model.SortOrder.USER_RATING
import com.michaldrabik.ui_model.SortType
import com.michaldrabik.ui_model.Tip
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_SELECTED_NEW_AT_TOP
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_SELECTED_SORT_ORDER
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_SELECTED_SORT_TYPE
import com.michaldrabik.ui_navigation.java.NavigationArgs.REQUEST_SORT_ORDER
import com.michaldrabik.ui_progress.R
import com.michaldrabik.ui_progress.databinding.FragmentProgressBinding
import com.michaldrabik.ui_progress.helpers.ProgressLayoutManagerProvider
import com.michaldrabik.ui_progress.helpers.TopOverscrollAdapter
import com.michaldrabik.ui_progress.main.EpisodeCheckActionUiEvent
import com.michaldrabik.ui_progress.main.ProgressMainFragment
import com.michaldrabik.ui_progress.main.ProgressMainViewModel
import com.michaldrabik.ui_progress.main.RequestWidgetsUpdate
import com.michaldrabik.ui_progress.progress.recycler.ProgressAdapter
import com.michaldrabik.ui_progress.progress.recycler.ProgressListItem
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
class ProgressFragment :
  BaseFragment<ProgressViewModel>(R.layout.fragment_progress),
  OnSearchClickListener,
  OnScrollResetListener {

  private companion object {
    const val OVERSCROLL_OFFSET = 225F
    const val OVERSCROLL_OFFSET_TRANSLATION = 4.5F
  }

  @Inject lateinit var settings: SettingsViewModeRepository

  override val navigationId = R.id.progressMainFragment
  private val binding by viewBinding(FragmentProgressBinding::bind)

  private val parentViewModel by viewModels<ProgressMainViewModel>({ requireParentFragment() })
  override val viewModel by viewModels<ProgressViewModel>()

  private var adapter: ProgressAdapter? = null
  private var layoutManager: LayoutManager? = null
  private var overscroll: IOverScrollDecor? = null
  private var overscrollJob: Job? = null
  private var statusBarHeight = 0
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
      progressEmptyView.progressEmptyTraktButton.onClick { requireMainFragment().openTraktSync() }
      progressEmptyView.progressEmptyDiscoverButton.onClick {
        (requireActivity() as NavigationHost).navigateToDiscover()
      }
      progressTipItem.onClick {
        it.gone()
        showTip(Tip.WATCHLIST_ITEM_PIN)
      }
    }
  }

  private fun setupRecycler() {
    val gridSpanSize = settings.tabletGridSpanSize
    layoutManager = ProgressLayoutManagerProvider.provideLayoutManger(requireContext(), gridSpanSize)
    (layoutManager as? GridLayoutManager)?.run {
      withSpanSizeLookup { position ->
        when (adapter?.getItems()?.get(position)) {
          is ProgressListItem.Header -> gridSpanSize
          is ProgressListItem.Filters -> gridSpanSize
          is ProgressListItem.Episode -> 1
          else -> throw IllegalStateException()
        }
      }
    }
    adapter = ProgressAdapter(
      itemClickListener = { requireMainFragment().openShowDetails(it.show) },
      itemLongClickListener = { requireMainFragment().openShowMenu(it.show) },
      headerClickListener = { viewModel.toggleHeaderCollapsed(it.type) },
      detailsClickListener = {
        requireMainFragment().openEpisodeDetails(
          show = it.show,
          episode = it.requireEpisode(),
          season = it.requireSeason(),
        )
      },
      checkClickListener = viewModel::onEpisodeChecked,
      sortChipClickListener = viewModel::loadSortOrder,
      upcomingChipClickListener = viewModel::setUpcomingFilter,
      onHoldChipClickListener = viewModel::setOnHoldFilter,
      missingTranslationListener = viewModel::findMissingTranslation,
      missingImageListener = { item: ProgressListItem, force -> viewModel.findMissingImage(item, force) },
      listChangeListener = {
        requireMainFragment().resetTranslations()
        layoutManager?.scrollToPosition(0)
      },
    )
    binding.progressRecycler.apply {
      adapter = this@ProgressFragment.adapter
      layoutManager = this@ProgressFragment.layoutManager
      (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
      setHasFixedSize(true)
    }
  }

  private fun setupInsets() {
    with(binding) {
      val recyclerPadding =
        if (moviesEnabled) R.dimen.progressTabsViewPadding else R.dimen.progressTabsViewPaddingNoModes

      val overscrollPadding =
        if (moviesEnabled) R.dimen.progressOverscrollPadding else R.dimen.progressOverscrollPaddingNoModes

      if (statusBarHeight != 0) {
        (progressOverscroll.layoutParams as ViewGroup.MarginLayoutParams)
          .updateMargins(top = statusBarHeight + dimenToPx(overscrollPadding))
      }

      root.doOnApplyWindowInsets { _, insets, padding, _ ->
        val tabletOffset = if (isTablet) dimenToPx(R.dimen.spaceMedium) else 0
        val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        statusBarHeight = systemInsets.top + tabletOffset

        progressRecycler.updatePadding(
          top = statusBarHeight + dimenToPx(recyclerPadding),
          bottom = systemInsets.bottom + dimenToPx(R.dimen.bottomNavigationHeightPadded),
        )

        (progressEmptyView.root.layoutParams as ViewGroup.MarginLayoutParams)
          .updateMargins(top = statusBarHeight + dimenToPx(R.dimen.spaceBig))

        (progressOverscroll.layoutParams as ViewGroup.MarginLayoutParams)
          .updateMargins(top = statusBarHeight + dimenToPx(overscrollPadding))
      }
    }
  }

  private fun setupOverscroll() {
    if (overscroll != null || view == null) {
      return
    }
    val adapt = TopOverscrollAdapter(binding.progressRecycler)
    overscroll = VerticalOverScrollBounceEffectDecorator(
      adapt,
      1F,
      OverScrollBounceEffectDecoratorBase.DEFAULT_TOUCH_DRAG_MOVE_RATIO_BCK,
      OverScrollBounceEffectDecoratorBase.DEFAULT_DECELERATE_FACTOR,
    ).apply {
      setOverScrollUpdateListener { _, state, offset ->
        binding.progressOverscroll.run {
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
                  binding.progressOverscrollProgress.progress >= 100
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
        binding.progressOverscrollProgress.progress = progress
        if (progress >= 100) {
          binding.progressOverscroll.bump(200)
        }
        delay(5)
      }
    }
  }

  private fun onOverscrollCancel() {
    overscrollJob?.cancel()
    overscrollJob = null
    binding.progressOverscrollProgress.progress = 0
  }

  private fun openSortOrderDialog(
    order: SortOrder,
    type: SortType,
    newAtTop: Boolean,
  ) {
    val options = listOf(NAME, RATING, USER_RATING, NEWEST, RECENTLY_WATCHED, EPISODES_LEFT, RANDOM)
    val args = SortOrderBottomSheet.createBundle(options, order, type, newAtTop = Pair(true, newAtTop))

    requireParentFragment().setFragmentResultListener(REQUEST_SORT_ORDER) { _, bundle ->
      val sortOrder = bundle.getSerializable(ARG_SELECTED_SORT_ORDER) as SortOrder
      val sortType = bundle.getSerializable(ARG_SELECTED_SORT_TYPE) as SortType
      val newTop = bundle.getBoolean(ARG_SELECTED_NEW_AT_TOP)
      viewModel.setSortOrder(sortOrder, sortType, newTop)
    }

    navigateToSafe(R.id.actionProgressFragmentToSortOrder, args)
  }

  override fun onEnterSearch() {
    isSearching = true

    with(binding) {
      progressRecycler.translationY = dimenToPx(R.dimen.progressSearchLocalOffset).toFloat()
      progressRecycler.smoothScrollToPosition(0)
    }

    overscroll?.detach()
    overscroll = null
  }

  override fun onExitSearch() {
    isSearching = false

    with(binding) {
      progressRecycler.translationY = 0F
      progressRecycler.smoothScrollToPosition(0)
    }

    setupOverscroll()
  }

  private fun handleEvent(event: Event<*>) {
    when (event) {
      is EpisodeCheckActionUiEvent -> {
        when (event.dateSelectionType) {
          ALWAYS_ASK -> requireMainFragment().openDateSelectionDialog(event.episode)
          NOW -> parentViewModel.setWatchedEpisode(event.episode)
        }
      }
      is RequestWidgetsUpdate -> {
        (requireAppContext() as WidgetsProvider).requestShowsWidgetsUpdate()
      }
    }
  }

  private fun render(uiState: ProgressUiState) {
    uiState.run {
      with(binding) {
        items?.let {
          val resetScroll = scrollReset?.consume() == true
          adapter?.setItems(it, resetScroll)
          renderFiltersEmpty(uiState)
          progressEmptyView.root.visibleIf(it.isEmpty() && !isLoading && !isSearching)
          progressTipItem.visibleIf(it.count() >= 3 && !isTipShown(Tip.WATCHLIST_ITEM_PIN))
          progressRecycler
            .fadeIn(
              duration = 200,
              withHardware = true,
            ).add(animations)
        }
      }
      isOverScrollEnabled.let {
        if (it) {
          setupOverscroll()
        } else {
          overscroll?.detach()
          overscroll = null
        }
      }
      sortOrder?.let { event ->
        event.consume()?.let {
          openSortOrderDialog(it.first, it.second, it.third)
        }
      }
    }
  }

  private fun renderFiltersEmpty(uiState: ProgressUiState) {
    val items = uiState.items ?: emptyList()
    if (uiState.isLoading) {
      binding.progressEmptyFilterView.gone()
      return
    }
    if (isSearching) {
      binding.progressEmptyFilterView.fadeIf(items.isEmpty(), duration = 200)
      return
    }
    val hasActiveFilter = items.filterIsInstance<ProgressListItem.Filters>().firstOrNull()?.hasActiveFilters() == true
    val isFilterEmpty = hasActiveFilter && items.filterIsInstance<ProgressListItem.Episode>().isEmpty()
    binding.progressEmptyFilterView.fadeIf(isFilterEmpty, duration = 200)
  }

  override fun onScrollReset() {
    binding.progressRecycler.smoothScrollToPosition(0)
  }

  private fun requireMainFragment() = requireParentFragment() as ProgressMainFragment

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
