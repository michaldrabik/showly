package com.michaldrabik.ui_search

import android.graphics.drawable.Animatable
import android.os.Bundle
import android.text.Editable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.clearFragmentResultListener
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import com.michaldrabik.common.Mode
import com.michaldrabik.repository.settings.SettingsViewModeRepository
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.common.sheets.context_menu.ContextMenuBottomSheet
import com.michaldrabik.ui_base.common.sheets.sort_order.SortOrderBottomSheet
import com.michaldrabik.ui_base.utilities.extensions.add
import com.michaldrabik.ui_base.utilities.extensions.colorFromAttr
import com.michaldrabik.ui_base.utilities.extensions.dimenToPx
import com.michaldrabik.ui_base.utilities.extensions.disableUi
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.ui_base.utilities.extensions.enableUi
import com.michaldrabik.ui_base.utilities.extensions.fadeIf
import com.michaldrabik.ui_base.utilities.extensions.fadeIn
import com.michaldrabik.ui_base.utilities.extensions.fadeOut
import com.michaldrabik.ui_base.utilities.extensions.gone
import com.michaldrabik.ui_base.utilities.extensions.hideKeyboard
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.navigateToSafe
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.shake
import com.michaldrabik.ui_base.utilities.extensions.showKeyboard
import com.michaldrabik.ui_base.utilities.extensions.visible
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_model.RecentSearch
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortType
import com.michaldrabik.ui_navigation.java.NavigationArgs
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_MOVIE_ID
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_SELECTED_SORT_ORDER
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_SELECTED_SORT_TYPE
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_SHOW_ID
import com.michaldrabik.ui_navigation.java.NavigationArgs.REQUEST_SORT_ORDER
import com.michaldrabik.ui_search.databinding.FragmentSearchBinding
import com.michaldrabik.ui_search.recycler.SearchAdapter
import com.michaldrabik.ui_search.recycler.SearchListItem
import com.michaldrabik.ui_search.recycler.suggestions.SuggestionAdapter
import com.michaldrabik.ui_search.utilities.SearchLayoutManagerProvider
import com.michaldrabik.ui_search.utilities.TextWatcherAdapter
import com.michaldrabik.ui_search.views.RecentSearchView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SearchFragment :
  BaseFragment<SearchViewModel>(R.layout.fragment_search),
  TextWatcherAdapter {

  companion object {
    private const val ARG_HEADER_TRANSLATION = "ARG_HEADER_TRANSLATION"
  }

  @Inject lateinit var settings: SettingsViewModeRepository

  override val navigationId = R.id.searchFragment

  override val viewModel by viewModels<SearchViewModel>()
  private val binding by viewBinding(FragmentSearchBinding::bind)

  private var adapter: SearchAdapter? = null
  private var suggestionsAdapter: SuggestionAdapter? = null
  private var layoutManager: LayoutManager? = null
  private var suggestionsLayoutManager: LayoutManager? = null

  private val swipeRefreshEndOffset by lazy { requireContext().dimenToPx(R.dimen.swipeRefreshEndOffset) }
  private val swipeRefreshStartOffset by lazy { requireContext().dimenToPx(R.dimen.swipeRefreshStartOffset) }

  private var headerTranslation = 0F

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View? {
    savedInstanceState?.let {
      headerTranslation = it.getFloat(ARG_HEADER_TRANSLATION)
    }
    return super.onCreateView(inflater, container, savedInstanceState)
  }

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)
    enableUi()
    setupView()
    setupRecycler()
    setupSuggestionsRecycler()
    setupInsets()

    if (savedInstanceState == null && !isInitialized) {
      isInitialized = true
    }

    launchAndRepeatStarted(
      { viewModel.uiState.collect { render(it) } },
      { viewModel.messageFlow.collect { showSnack(it) } },
    )
  }

  override fun onPause() {
    enableUi()
    headerTranslation = binding.searchFiltersView.translationY
    super.onPause()
  }

  override fun onStop() {
    viewModel.clearSuggestions()
    with(binding) {
      searchViewLayout.binding.searchViewInput.removeTextChangedListener(this@SearchFragment)
    }
    super.onStop()
  }

  override fun onDestroyView() {
    adapter = null
    layoutManager = null
    super.onDestroyView()
  }

  private fun setupView() {
    with(binding) {
      searchViewLayout.binding.searchViewInput.visible()
      searchViewLayout.binding.searchViewText.gone()
      (searchViewLayout.binding.searchViewIcon.drawable as Animatable).start()
      searchViewLayout.settingsIconVisible = false

      viewModel.preloadSuggestions()

      if (!isInitialized) {
        searchViewLayout.binding.searchViewInput.showKeyboard()
        searchViewLayout.binding.searchViewInput.requestFocus()
        viewModel.loadRecentSearches()
      }

      searchViewLayout.binding.searchViewInput.run {
        addTextChangedListener(this@SearchFragment)
        setOnEditorActionListener { textView, id, _ ->
          if (id == EditorInfo.IME_ACTION_SEARCH) {
            val query = textView.text.toString()
            return@setOnEditorActionListener onSearchQuery(query)
          }
          true
        }
        setOnKeyListener { _, keyCode, keyEvent ->
          if (keyEvent.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
            val query = text.toString()
            return@setOnKeyListener onSearchQuery(query)
          }
          false
        }
      }

      searchViewLayout.binding.searchViewIcon.onClick {
        searchViewLayout.binding.searchViewInput.hideKeyboard()
        requireActivity().onBackPressed()
      }

      with(searchFiltersView) {
        onChipsChangeListener = viewModel::setFilters
        onSortClickListener = ::openSortingDialog
        translationY = headerTranslation
      }
    }
  }

  private fun setupRecycler() {
    with(binding) {
      layoutManager = SearchLayoutManagerProvider.provideLayoutManger(requireContext(), settings.tabletGridSpanSize)
      adapter = SearchAdapter(
        itemClickListener = { openShowDetails(it) },
        itemLongClickListener = { openContextMenu(it) },
        missingImageListener = { ids, force -> viewModel.loadMissingImage(ids, force) },
        listChangeListener = { searchRecycler.scrollToPosition(0) },
      )
      searchRecycler.apply {
        setHasFixedSize(true)
        adapter = this@SearchFragment.adapter
        layoutManager = this@SearchFragment.layoutManager
        itemAnimator = null
        clearOnScrollListeners()
        addOnScrollListener(object : RecyclerView.OnScrollListener() {
          override fun onScrolled(
            recyclerView: RecyclerView,
            dx: Int,
            dy: Int,
          ) {
            val value = searchFiltersView.translationY - dy
            searchFiltersView.translationY = value.coerceAtMost(0F)
          }
        })
      }

      searchSwipeRefresh.apply {
        isEnabled = false
        val color = requireContext().colorFromAttr(R.attr.colorAccent)
        setProgressBackgroundColorSchemeColor(requireContext().colorFromAttr(R.attr.colorSearchViewBackground))
        setColorSchemeColors(color, color, color)
        setProgressViewOffset(false, swipeRefreshStartOffset, swipeRefreshEndOffset)
      }
    }
  }

  private fun setupSuggestionsRecycler() {
    suggestionsLayoutManager = SearchLayoutManagerProvider
      .provideLayoutManger(requireContext(), settings.tabletGridSpanSize)
    suggestionsAdapter = SuggestionAdapter(
      itemClickListener = {
        val query =
          if (it.translation?.title?.isNotBlank() == true) {
            it.translation.title
          } else {
            it.title
          }
        viewModel.saveRecentSearch(query)
        openDetails(it)
      },
      missingImageListener = { ids, force -> viewModel.loadMissingSuggestionImage(ids, force) },
      missingTranslationListener = { viewModel.loadMissingSuggestionTranslation(it) },
    )
    binding.suggestionsRecycler.apply {
      adapter = this@SearchFragment.suggestionsAdapter
      layoutManager = this@SearchFragment.suggestionsLayoutManager
      itemAnimator = null
    }
  }

  private fun setupInsets() {
    with(binding) {
      searchRoot.doOnApplyWindowInsets { view, insets, padding, _ ->
        val tabletOffset = if (isTablet) dimenToPx(R.dimen.spaceMedium) else 0
        val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(top = inset.top + tabletOffset)
        searchRecycler.updatePadding(bottom = inset.bottom + padding.bottom)
      }
    }
  }

  private fun onSearchQuery(query: String): Boolean {
    with(binding) {
      if (query.trim().isBlank()) {
        searchViewLayout.shake()
        return true
      }
      viewModel.search(query)
      searchViewLayout.binding.searchViewInput.hideKeyboard()
      searchViewLayout.binding.searchViewInput.clearFocus()
      return true
    }
  }

  private fun openSortingDialog(
    order: SortOrder,
    type: SortType,
  ) {
    val options = listOf(SortOrder.RANK, SortOrder.NAME, SortOrder.NEWEST)
    val args = SortOrderBottomSheet.createBundle(options, order, type)

    setFragmentResultListener(REQUEST_SORT_ORDER) { _, bundle ->
      val sortOrder = bundle.getSerializable(ARG_SELECTED_SORT_ORDER) as SortOrder
      val sortType = bundle.getSerializable(ARG_SELECTED_SORT_TYPE) as SortType
      viewModel.setSortOrder(sortOrder, sortType)
    }

    navigateToSafe(R.id.actionSearchFragmentToSortOrder, args)
  }

  private fun openShowDetails(item: SearchListItem) {
    disableUi()
    binding.searchRoot
      .fadeOut(150) {
        openDetails(item)
      }.add(animations)
  }

  private fun openDetails(item: SearchListItem) {
    if (item.isShow) {
      val bundle = Bundle().apply { putLong(ARG_SHOW_ID, item.show.traktId) }
      navigateToSafe(R.id.actionSearchFragmentToShowDetailsFragment, bundle)
    } else if (item.isMovie) {
      val bundle = Bundle().apply { putLong(ARG_MOVIE_ID, item.movie.traktId) }
      navigateToSafe(R.id.actionSearchFragmentToMovieDetailsFragment, bundle)
    }
  }

  private fun openContextMenu(item: SearchListItem) {
    setFragmentResultListener(NavigationArgs.REQUEST_ITEM_MENU) { requestKey, _ ->
      if (requestKey == NavigationArgs.REQUEST_ITEM_MENU) {
        viewModel.refreshFollowState(item)
      }
      clearFragmentResultListener(NavigationArgs.REQUEST_ITEM_MENU)
    }
    if (item.isShow) {
      val bundle = ContextMenuBottomSheet.createBundle(item.show.ids.trakt)
      navigateToSafe(R.id.actionSearchFragmentToShowItemMenu, bundle)
    } else if (item.isMovie) {
      val bundle = ContextMenuBottomSheet.createBundle(item.movie.ids.trakt)
      navigateToSafe(R.id.actionSearchFragmentToMovieItemMenu, bundle)
    }
  }

  private fun render(uiState: SearchUiState) {
    uiState.run {
      with(binding) {
        searchItems?.let {
          val resetScroll = resetScroll?.consume() == true
          adapter?.setItems(it, resetScroll)
          if (searchItemsAnimate?.consume() == true) {
            searchRecycler.scheduleLayoutAnimation()
          }
          if (resetScroll) {
            searchFiltersView.translationY = 0F
          }
        }
        recentSearchItems?.let { renderRecentSearches(it) }
        suggestionsItems?.let {
          suggestionsAdapter?.setItems(it)
          suggestionsRecycler.visibleIf(it.isNotEmpty())
        }
        searchOptions?.let {
          searchFiltersView.setTypes(it.filters)
          searchFiltersView.setSorting(it.sortOrder, it.sortType)
        }
        isSearching.let {
          searchSwipeRefresh.isRefreshing = it
          searchViewLayout.isEnabled = !it
        }
        sortOrder?.let { event ->
          event.consume()?.let { openSortingDialog(it.first, it.second) }
        }
        isMoviesEnabled.let { isEnabled ->
          val types = mutableListOf(Mode.SHOWS).apply {
            if (isEnabled) add(Mode.MOVIES)
          }
          searchFiltersView.setEnabledTypes(types)
        }
        searchEmptyView.fadeIf(isEmpty)
        searchInitialView.fadeIf(isInitial)
        searchFiltersView.visibleIf(isFiltersVisible)
      }
    }
  }

  private fun renderRecentSearches(it: List<RecentSearch>) {
    with(binding) {
      if (it.isEmpty()) {
        searchRecentsClearButton.gone()
        searchRecentsLayout.removeAllViews()
        searchRecentsLayout.gone()
        return
      }

      searchRecentsLayout.fadeIn()
      searchRecentsClearButton.fadeIn()
      searchRecentsClearButton.onClick { viewModel.clearRecentSearches() }

      val paddingH = requireContext().dimenToPx(R.dimen.screenMarginHorizontal)
      val paddingV = requireContext().dimenToPx(R.dimen.spaceMedium)

      searchRecentsLayout.removeAllViews()
      it.forEach { item ->
        val view = RecentSearchView(requireContext()).apply {
          setPadding(paddingH, paddingV, paddingH, paddingV)
          bind(item)
          onClick {
            viewModel.search(item.text)
            searchViewLayout.binding.searchViewInput.setText(item.text)
          }
        }
        searchRecentsLayout.addView(view)
      }
    }
  }

  override fun afterTextChanged(text: Editable?) {
    viewModel.loadSuggestions(text.toString())
  }
}
