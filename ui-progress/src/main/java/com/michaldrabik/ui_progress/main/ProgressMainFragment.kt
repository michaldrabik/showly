package com.michaldrabik.ui_progress.main

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateMargins
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.clearFragmentResultListener
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.viewpager.widget.ViewPager
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.common.OnScrollResetListener
import com.michaldrabik.ui_base.common.OnSearchClickListener
import com.michaldrabik.ui_base.common.OnShowsMoviesSyncedListener
import com.michaldrabik.ui_base.common.OnTabReselectedListener
import com.michaldrabik.ui_base.common.sheets.context_menu.ContextMenuBottomSheet
import com.michaldrabik.ui_base.common.sheets.date_selection.DateSelectionBottomSheet
import com.michaldrabik.ui_base.common.sheets.date_selection.DateSelectionBottomSheet.Companion.REQUEST_DATE_SELECTION
import com.michaldrabik.ui_base.common.sheets.date_selection.DateSelectionBottomSheet.Companion.RESULT_DATE_SELECTION
import com.michaldrabik.ui_base.common.sheets.date_selection.DateSelectionBottomSheet.Result
import com.michaldrabik.ui_base.utilities.events.Event
import com.michaldrabik.ui_base.utilities.extensions.add
import com.michaldrabik.ui_base.utilities.extensions.dimenToPx
import com.michaldrabik.ui_base.utilities.extensions.disableUi
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.ui_base.utilities.extensions.enableUi
import com.michaldrabik.ui_base.utilities.extensions.fadeIn
import com.michaldrabik.ui_base.utilities.extensions.fadeOut
import com.michaldrabik.ui_base.utilities.extensions.gone
import com.michaldrabik.ui_base.utilities.extensions.hideKeyboard
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.navigateToSafe
import com.michaldrabik.ui_base.utilities.extensions.nextPage
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.requireParcelable
import com.michaldrabik.ui_base.utilities.extensions.showKeyboard
import com.michaldrabik.ui_base.utilities.extensions.visible
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_episodes.details.EpisodeDetailsBottomSheet
import com.michaldrabik.ui_model.Episode
import com.michaldrabik.ui_model.EpisodeBundle
import com.michaldrabik.ui_model.Season
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_navigation.java.NavigationArgs.ACTION_EPISODE_TAB_SELECTED
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_SHOW_ID
import com.michaldrabik.ui_navigation.java.NavigationArgs.REQUEST_EPISODE_DETAILS
import com.michaldrabik.ui_navigation.java.NavigationArgs.REQUEST_ITEM_MENU
import com.michaldrabik.ui_progress.R
import com.michaldrabik.ui_progress.databinding.FragmentProgressMainBinding
import com.michaldrabik.ui_progress.main.adapters.ProgressMainAdapter
import dagger.hilt.android.AndroidEntryPoint
import java.time.ZonedDateTime

@AndroidEntryPoint
class ProgressMainFragment :
  BaseFragment<ProgressMainViewModel>(R.layout.fragment_progress_main),
  OnShowsMoviesSyncedListener,
  OnTabReselectedListener {

  companion object {
    private const val TRANSLATION_DURATION = 225L
  }

  override val navigationId = R.id.progressMainFragment

  override val viewModel by viewModels<ProgressMainViewModel>()
  private val binding by viewBinding(FragmentProgressMainBinding::bind)

  private var adapter: ProgressMainAdapter? = null

  private var searchViewTranslation = 0F
  private var tabsTranslation = 0F
  private var sideIconTranslation = 0F
  private var currentPage = 0
  private var isSearching = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    savedInstanceState?.let {
      searchViewTranslation = it.getFloat("ARG_SEARCH_POSITION")
      tabsTranslation = it.getFloat("ARG_TABS_POSITION")
      sideIconTranslation = it.getFloat("ARG_SIDE_ICON_POSITION")
      currentPage = it.getInt("ARG_PAGE")
    }
  }

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)
    setupView()
    setupPager()
    setupInsets()

    launchAndRepeatStarted(
      { viewModel.uiState.collect { render(it) } },
      { viewModel.messageFlow.collect { showSnack(it) } },
      { viewModel.eventFlow.collect { handleEvent(it) } },
      doAfterLaunch = { viewModel.loadProgress() },
    )
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putFloat("ARG_SEARCH_POSITION", searchViewTranslation)
    outState.putFloat("ARG_TABS_POSITION", tabsTranslation)
    outState.putFloat("ARG_SIDE_ICON_POSITION", sideIconTranslation)
    outState.putInt("ARG_PAGE", currentPage)
  }

  override fun onResume() {
    super.onResume()
    showNavigation()
  }

  override fun onPause() {
    enableUi()
    with(binding) {
      tabsTranslation = progressMainTabs.translationY
      searchViewTranslation = progressMainSearchView.translationY
      sideIconTranslation = progressMainSideIcons.translationY
    }
    super.onPause()
  }

  override fun onDestroyView() {
    with(binding) {
      progressMainPager.removeOnPageChangeListener(pageChangeListener)
      progressMainPager.adapter = null
    }
    adapter = null
    super.onDestroyView()
  }

  private fun setupView() {
    with(binding) {
      with(progressMainSearchIcon) {
        onClick { if (!isSearching) enterSearch() else exitSearch() }
      }

      with(progressMainSearchView) {
        hint = getString(R.string.textSearchFor)
        settingsIconVisible = true
        traktIconVisible = true
        isClickable = false
        onClick { openMainSearch() }
        onSettingsClickListener = { openSettings() }
        onTraktClickListener = { openTraktSync() }
      }

      with(progressMainSearchLocalView) {
        onCloseClickListener = { exitSearch() }
      }

      with(progressMainPagerModeTabs) {
        visibleIf(moviesEnabled)
        onModeSelected = { mode = it }
        selectShows()
      }

      progressMainTabs.translationY = tabsTranslation
      progressMainPagerModeTabs.translationY = tabsTranslation
      progressMainSearchView.translationY = searchViewTranslation
      progressMainSideIcons.translationY = sideIconTranslation
    }
  }

  private fun setupPager() {
    adapter = ProgressMainAdapter(childFragmentManager, requireContext())
    with(binding) {
      progressMainPager.run {
        adapter = this@ProgressMainFragment.adapter
        offscreenPageLimit = ProgressMainAdapter.PAGES_COUNT
        addOnPageChangeListener(pageChangeListener)
      }
      progressMainTabs.setupWithViewPager(progressMainPager)
    }
  }

  private fun setupInsets() {
    with(binding) {
      progressMainRoot.doOnApplyWindowInsets { _, insets, _, _ ->
        val tabletOffset = if (isTablet) dimenToPx(R.dimen.spaceMedium) else 0
        val statusBarSize = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top + tabletOffset
        val progressTabsMargin = if (moviesEnabled) {
          R.dimen.progressSearchViewPadding
        } else {
          R.dimen.progressSearchViewPaddingNoModes
        }

        val progressMainSearchLocalMargin =
          if (moviesEnabled) R.dimen.progressSearchLocalViewPadding else R.dimen.progressSearchLocalViewPaddingNoModes
        (progressMainSearchView.layoutParams as ViewGroup.MarginLayoutParams)
          .updateMargins(top = statusBarSize + dimenToPx(R.dimen.spaceMedium))
        (progressMainSearchLocalView.layoutParams as ViewGroup.MarginLayoutParams)
          .updateMargins(top = statusBarSize + dimenToPx(progressMainSearchLocalMargin))
        (progressMainPagerModeTabs.layoutParams as ViewGroup.MarginLayoutParams)
          .updateMargins(top = statusBarSize + dimenToPx(R.dimen.collectionTabsMargin))
        arrayOf(progressMainTabs, progressMainSideIcons).forEach {
          val margin = statusBarSize + dimenToPx(progressTabsMargin)
          (it.layoutParams as ViewGroup.MarginLayoutParams).updateMargins(top = margin)
        }
      }
    }
  }

  override fun setupBackPressed() {
    val dispatcher = requireActivity().onBackPressedDispatcher
    dispatcher.addCallback(viewLifecycleOwner) {
      if (isSearching) {
        exitSearch()
      } else {
        isEnabled = false
        activity?.onBackPressed()
      }
    }
  }

  private fun openMainSearch() {
    with(binding) {
      disableUi()
      hideNavigation()
      progressMainPagerModeTabs.fadeOut(duration = 200).add(animations)
      progressMainTabs.fadeOut(duration = 200).add(animations)
      progressMainSideIcons.fadeOut(duration = 200).add(animations)
      progressMainPager
        .fadeOut(duration = 200) {
          navigateToSafe(R.id.actionProgressFragmentToSearch)
        }.add(animations)
    }
  }

  fun openTraktSync() {
    hideNavigation()
    exitSearch()
    navigateToSafe(R.id.actionProgressFragmentToTraktSyncFragment)
  }

  fun openShowDetails(show: Show) {
    with(binding) {
      hideNavigation()
      progressMainRoot
        .fadeOut(150) {
          if (findNavControl()?.currentDestination?.id == R.id.progressMainFragment) {
            val bundle = Bundle().apply { putLong(ARG_SHOW_ID, show.traktId) }
            navigateToSafe(R.id.actionProgressFragmentToShowDetailsFragment, bundle)
            exitSearch()
          } else {
            showNavigation()
            progressMainRoot.fadeIn(50).add(animations)
          }
        }.add(animations)
    }
  }

  fun openShowMenu(show: Show) {
    setFragmentResultListener(REQUEST_ITEM_MENU) { requestKey, _ ->
      if (requestKey == REQUEST_ITEM_MENU) {
        viewModel.loadProgress()
      }
      clearFragmentResultListener(REQUEST_ITEM_MENU)
    }
    val bundle = ContextMenuBottomSheet.createBundle(show.ids.trakt, showPinButtons = true)
    navigateToSafe(R.id.actionProgressFragmentToItemMenu, bundle)
  }

  fun openEpisodeDetails(
    show: Show,
    episode: Episode,
    season: Season,
  ) {
    setFragmentResultListener(REQUEST_EPISODE_DETAILS) { _, bundle ->
      when {
        bundle.containsKey(ACTION_EPISODE_TAB_SELECTED) -> {
          val selectedEpisode = bundle.requireParcelable<Episode>(ACTION_EPISODE_TAB_SELECTED)
          openEpisodeDetails(show, selectedEpisode, season)
        }
      }
    }
    viewModel.onEpisodeDetails(show, episode)
  }

  fun openDateSelectionDialog(episodeBundle: EpisodeBundle) {
    fun openRateDialogIfNeeded(customDate: ZonedDateTime? = null) {
      viewModel.setWatchedEpisode(episodeBundle, customDate)
    }

    setFragmentResultListener(REQUEST_DATE_SELECTION) { _, bundle ->
      when (val result = bundle.requireParcelable<Result>(RESULT_DATE_SELECTION)) {
        is Result.Now -> openRateDialogIfNeeded()
        is Result.CustomDate -> openRateDialogIfNeeded(result.date)
        is Result.ReleaseDate -> openRateDialogIfNeeded(result.date)
      }
    }
    val options = DateSelectionBottomSheet.createBundle(episodeBundle.episode.firstAired)
    navigateToSafe(R.id.actionProgressFragmentToDateSelection, options)
  }

  private fun openSettings() {
    hideNavigation()
    exitSearch()
    navigateToSafe(R.id.actionProgressFragmentToSettingsFragment)
  }

  private fun enterSearch() {
    resetTranslations()
    with(binding) {
      progressMainSearchLocalView.fadeIn(150)
      with(progressMainSearchLocalView.binding.searchViewLocalInput) {
        setText("")
        doAfterTextChanged { viewModel.onSearchQuery(it?.toString()) }
        visible()
        showKeyboard()
        requestFocus()
      }
    }
    isSearching = true
    childFragmentManager.fragments.forEach { (it as? OnSearchClickListener)?.onEnterSearch() }
  }

  private fun exitSearch() {
    isSearching = false
    childFragmentManager.fragments.forEach { (it as? OnSearchClickListener)?.onExitSearch() }
    resetTranslations()
    with(binding) {
      progressMainSearchLocalView.gone()
      with(progressMainSearchLocalView.binding.searchViewLocalInput) {
        setText("")
        gone()
        hideKeyboard()
        clearFocus()
      }
    }
  }

  fun toggleCalendarMode() {
    exitSearch()
    onScrollReset()
    resetTranslations()
    viewModel.toggleCalendarMode()
  }

  override fun onShowsMoviesSyncFinished() = viewModel.loadProgress()

  override fun onTabReselected() {
    if (view == null) return
    resetTranslations(duration = 0)
    binding.progressMainPager.nextPage()
    onScrollReset()
  }

  fun resetTranslations(duration: Long = TRANSLATION_DURATION) {
    if (view == null) return
    with(binding) {
      arrayOf(
        progressMainSearchView,
        progressMainTabs,
        progressMainPagerModeTabs,
        progressMainSideIcons,
        progressMainSearchLocalView,
      ).forEach {
        it
          .animate()
          .translationY(0F)
          .setDuration(duration)
          .add(animations)
          ?.start()
      }
    }
  }

  private fun onScrollReset() =
    childFragmentManager.fragments.forEach { (it as? OnScrollResetListener)?.onScrollReset() }

  private fun render(uiState: ProgressMainUiState) {
    with(binding) {
      progressMainSearchView.setTraktProgress(uiState.isSyncing, withIcon = true)
      progressMainSearchView.isEnabled = !uiState.isSyncing
    }
  }

  private fun handleEvent(event: Event<*>) {
    when (event) {
      is OpenEpisodeDetails -> {
        val bundle = EpisodeDetailsBottomSheet.createBundle(
          showIds = event.show.ids,
          episode = event.episode,
          seasonEpisodesIds = null,
          isWatched = event.isWatched,
          showTabs = true,
        )
        navigateToSafe(R.id.actionProgressFragmentToEpisodeDetails, bundle)
      }
    }
  }

  private val pageChangeListener = object : ViewPager.OnPageChangeListener {
    override fun onPageSelected(position: Int) {
      if (currentPage == position) return

      if (binding.progressMainTabs.translationY != 0F) {
        resetTranslations()
        requireView().postDelayed({ onScrollReset() }, TRANSLATION_DURATION)
      }

      currentPage = position
    }

    override fun onPageScrolled(
      position: Int,
      positionOffset: Float,
      positionOffsetPixels: Int,
    ) = Unit

    override fun onPageScrollStateChanged(state: Int) = Unit
  }
}
