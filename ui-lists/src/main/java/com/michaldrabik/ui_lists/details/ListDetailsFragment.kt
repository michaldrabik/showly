package com.michaldrabik.ui_lists.details

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michaldrabik.common.Mode
import com.michaldrabik.repository.settings.SettingsViewModeRepository
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.common.ListViewMode.LIST_NORMAL
import com.michaldrabik.ui_base.common.sheets.sort_order.SortOrderBottomSheet
import com.michaldrabik.ui_base.utilities.extensions.add
import com.michaldrabik.ui_base.utilities.extensions.dimenToPx
import com.michaldrabik.ui_base.utilities.extensions.disableUi
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.ui_base.utilities.extensions.enableUi
import com.michaldrabik.ui_base.utilities.extensions.fadeIf
import com.michaldrabik.ui_base.utilities.extensions.fadeOut
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.navigateToSafe
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.requireParcelable
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.extensions.withSpanSizeLookup
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_lists.R
import com.michaldrabik.ui_lists.databinding.FragmentListDetailsBinding
import com.michaldrabik.ui_lists.details.helpers.ListItemDragListener
import com.michaldrabik.ui_lists.details.helpers.ListItemSwipeListener
import com.michaldrabik.ui_lists.details.helpers.ReorderListCallback
import com.michaldrabik.ui_lists.details.helpers.ReorderListCallbackAdapter
import com.michaldrabik.ui_lists.details.recycler.ListDetailsAdapter
import com.michaldrabik.ui_lists.details.recycler.ListDetailsItem
import com.michaldrabik.ui_lists.details.recycler.helpers.ListDetailsLayoutManagerProvider
import com.michaldrabik.ui_lists.details.recycler.helpers.ListDetailsListItemDecoration
import com.michaldrabik.ui_lists.details.views.ListDetailsDeleteConfirmView
import com.michaldrabik.ui_model.CustomList
import com.michaldrabik.ui_model.PremiumFeature
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortOrder.DATE_ADDED
import com.michaldrabik.ui_model.SortOrder.NAME
import com.michaldrabik.ui_model.SortOrder.NEWEST
import com.michaldrabik.ui_model.SortOrder.RANDOM
import com.michaldrabik.ui_model.SortOrder.RANK
import com.michaldrabik.ui_model.SortOrder.RATING
import com.michaldrabik.ui_model.SortOrder.USER_RATING
import com.michaldrabik.ui_model.SortType
import com.michaldrabik.ui_navigation.java.NavigationArgs
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_ITEM
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_LIST
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_MOVIE_ID
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_SELECTED_SORT_ORDER
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_SELECTED_SORT_TYPE
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_SHOW_ID
import com.michaldrabik.ui_navigation.java.NavigationArgs.REQUEST_SORT_ORDER
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ListDetailsFragment :
  BaseFragment<ListDetailsViewModel>(R.layout.fragment_list_details),
  ListItemDragListener,
  ListItemSwipeListener {

  companion object {
    private const val ARG_HEADER_TRANSLATION = "ARG_HEADER_TRANSLATION"
  }

  @Inject lateinit var settings: SettingsViewModeRepository

  override val navigationId = R.id.listDetailsFragment
  override val viewModel by viewModels<ListDetailsViewModel>()
  private val binding by viewBinding(FragmentListDetailsBinding::bind)

  private val list by lazy { requireParcelable<CustomList>(ARG_LIST) }

  private val recyclerPaddingBottom by lazy { requireContext().dimenToPx(R.dimen.spaceNormal) }
  private val recyclerPaddingTop by lazy { requireContext().dimenToPx(R.dimen.listDetailsRecyclerTopPadding) }
  private val recyclerPaddingGridTop by lazy { requireContext().dimenToPx(R.dimen.listDetailsRecyclerTopGridPadding) }
  private val tabletGridSpanSize by lazy { settings.tabletGridSpanSize }

  private var adapter: ListDetailsAdapter? = null
  private var touchHelper: ItemTouchHelper? = null
  private var layoutManager: LayoutManager? = null

  private var headerTranslation = 0F
  private var isReorderMode = false

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
    setupView()
    setupInsets()
    setupRecycler()

    launchAndRepeatStarted(
      { viewModel.uiState.collect { render(it) } },
      { viewModel.messageFlow.collect { showSnack(it) } },
      doAfterLaunch = { viewModel.loadDetails(list.id) },
    )
  }

  override fun onResume() {
    super.onResume()
    hideNavigation()
  }

  override fun onPause() {
    enableUi()
    headerTranslation = binding.fragmentListDetailsFiltersView.translationY
    super.onPause()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putFloat(ARG_HEADER_TRANSLATION, headerTranslation)
  }

  private fun setupView() {
    with(binding) {
      with(fragmentListDetailsToolbar) {
        title = list.name
        subtitle = list.description
        setNavigationOnClickListener {
          if (isReorderMode) {
            toggleReorderMode()
          } else {
            activity?.onBackPressed()
          }
        }
      }
      with(fragmentListDetailsFiltersView) {
        onTypesChangeListener = { viewModel.setFilterTypes(list.id, it) }
        onSortClickListener = { order, type -> openSortOrderDialog(order, type) }
        translationY = headerTranslation
      }
      fragmentListDetailsManageButton.onClick { toggleReorderMode() }
      fragmentListDetailsViewModeButton.onClick {
        val args = bundleOf(ARG_ITEM to PremiumFeature.VIEW_TYPES)
        navigateToSafe(R.id.actionListDetailsFragmentToPremium, args)
      }
    }
  }

  private fun setupInsets() {
    with(binding) {
      fragmentListDetailsRoot.doOnApplyWindowInsets { view, insets, padding, _ ->
        val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(top = padding.top + inset.top)
      }
    }
  }

  private fun setupRecycler() {
    layoutManager = ListDetailsLayoutManagerProvider
      .provideLayoutManger(requireContext(), LIST_NORMAL, tabletGridSpanSize)
    adapter = ListDetailsAdapter(
      itemClickListener = { openItemDetails(it) },
      missingImageListener = { item: ListDetailsItem, force: Boolean ->
        viewModel.loadMissingImage(item, force)
      },
      missingTranslationListener = {
        viewModel.loadMissingTranslation(it)
      },
      itemsChangedListener = {
        with(binding) {
          fragmentListDetailsRecycler.scrollToPosition(0)
          fragmentListDetailsFiltersView.translationY = 0F
        }
      },
      itemsClearedListener = {
        if (isReorderMode) viewModel.updateRanks(list.id, it)
      },
      itemsSwipedListener = {
        viewModel.deleteListItem(list.id, it)
      },
      itemDragStartListener = this,
      itemSwipeStartListener = this,
    ).apply {
      stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }
    binding.fragmentListDetailsRecycler.apply {
      adapter = this@ListDetailsFragment.adapter
      layoutManager = this@ListDetailsFragment.layoutManager
      (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
      setHasFixedSize(true)
      addItemDecoration(ListDetailsListItemDecoration(requireContext(), R.dimen.spaceSmall))
    }

    val touchCallback = ReorderListCallback(adapter as ReorderListCallbackAdapter)
    touchHelper = ItemTouchHelper(touchCallback)
    touchHelper?.attachToRecyclerView(binding.fragmentListDetailsRecycler)
  }

  override fun setupBackPressed() {
    val dispatcher = requireActivity().onBackPressedDispatcher
    dispatcher.addCallback(viewLifecycleOwner) {
      if (isReorderMode) {
        toggleReorderMode()
      } else {
        isEnabled = false
        findNavControl()?.popBackStack()
      }
    }
  }

  private fun openSortOrderDialog(
    order: SortOrder,
    type: SortType,
  ) {
    val options = listOf(RANK, NAME, RATING, USER_RATING, NEWEST, DATE_ADDED, RANDOM)
    val args = SortOrderBottomSheet.createBundle(options, order, type)

    setFragmentResultListener(REQUEST_SORT_ORDER) { _, bundle ->
      val sortOrder = bundle.getSerializable(ARG_SELECTED_SORT_ORDER) as SortOrder
      val sortType = bundle.getSerializable(ARG_SELECTED_SORT_TYPE) as SortType
      viewModel.setSortOrder(list.id, sortOrder, sortType)
    }

    navigateTo(R.id.actionListDetailsFragmentToSortOrder, args)
  }

  private fun openDeleteDialog(quickRemoveEnabled: Boolean) {
    val view = ListDetailsDeleteConfirmView(requireContext())
    MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialog)
      .apply { if (quickRemoveEnabled) setView(view) }
      .setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_dialog))
      .setTitle(R.string.textConfirmDeleteListTitle)
      .setMessage(R.string.textConfirmDeleteListSubtitle)
      .setPositiveButton(R.string.textYes) { _, _ ->
        val removeFromTrakt = view.binding.viewListDeleteConfirmCheckbox?.isChecked
        viewModel.deleteList(list.id, removeFromTrakt == true)
      }.setNegativeButton(R.string.textNo) { _, _ -> }
      .show()
  }

  private fun openEditDialog() {
    setFragmentResultListener(NavigationArgs.REQUEST_CREATE_LIST) { _, _ ->
      viewModel.loadDetails(list.id)
    }
    val bundle = bundleOf(ARG_LIST to list)
    navigateTo(R.id.actionListDetailsFragmentToEditListDialog, bundle)
  }

  private fun openItemDetails(listItem: ListDetailsItem) {
    disableUi()
    binding.fragmentListDetailsRoot
      .fadeOut(150) {
        val bundle = bundleOf(
          ARG_SHOW_ID to listItem.show?.traktId,
          ARG_MOVIE_ID to listItem.movie?.traktId,
        )
        val destination =
          when {
            listItem.isShow() -> R.id.actionListDetailsFragmentToShowDetailsFragment
            listItem.isMovie() -> R.id.actionListDetailsFragmentToMovieDetailsFragment
            else -> throw IllegalStateException()
          }
        navigateTo(destination, bundle)
      }.add(animations)
  }

  private fun openPopupMenu(quickRemoveEnabled: Boolean) {
    PopupMenu(requireContext(), binding.fragmentListDetailsMoreButton, Gravity.CENTER).apply {
      inflate(R.menu.menu_list_details)
      setOnMenuItemClickListener { menuItem ->
        when (menuItem.itemId) {
          R.id.menuListDetailsEdit -> openEditDialog()
          R.id.menuListDetailsDelete -> openDeleteDialog(quickRemoveEnabled)
        }
        true
      }
      show()
    }
  }

  private fun toggleReorderMode() {
    isReorderMode = !isReorderMode
    viewModel.setReorderMode(list.id, isReorderMode)
  }

  private fun render(uiState: ListDetailsUiState) {
    fun renderTitle(
      name: String?,
      itemsCount: Int? = null,
    ) {
      if (name.isNullOrBlank()) return
      binding.fragmentListDetailsToolbar.title = when {
        itemsCount != null && itemsCount > 0 -> "$name ($itemsCount)"
        else -> name
      }
    }

    uiState.run {
      renderTitle(listDetails?.name, listItems?.size)
      with(binding) {
        viewMode.let {
          if (adapter?.listViewMode != it) {
            layoutManager = ListDetailsLayoutManagerProvider
              .provideLayoutManger(requireContext(), it, tabletGridSpanSize)
            adapter?.listViewMode = it
            fragmentListDetailsRecycler?.let { recycler ->
              recycler.layoutManager = layoutManager
              recycler.adapter = adapter
            }
            fragmentListDetailsViewModeButton.setImageResource(
              when (it) {
                LIST_NORMAL -> R.drawable.ic_view_list
              },
            )
          }
        }
        listDetails?.let { details ->
          val isQuickRemoveEnabled = isQuickRemoveEnabled
          fragmentListDetailsToolbar.subtitle = details.description
          fragmentListDetailsMoreButton.onClick { openPopupMenu(isQuickRemoveEnabled) }
          fragmentListDetailsFiltersView.setFilters(details.filterTypeLocal, details.sortByLocal, details.sortHowLocal)
        }
        listItems?.let {
          val isRealEmpty = it.isEmpty() && listDetails?.filterTypeLocal?.containsAll(Mode.getAll()) == true
          fragmentListDetailsEmptyView.root.fadeIf(it.isEmpty())
          fragmentListDetailsManageButton.visibleIf(!isRealEmpty)
          fragmentListDetailsViewModeButton.visibleIf(!isRealEmpty)

          val scrollTop = resetScroll?.consume() == true
          view?.post {
            adapter?.setItems(it, scrollTop)
          }
          (layoutManager as? GridLayoutManager)?.withSpanSizeLookup { pos ->
            adapter
              ?.items
              ?.get(pos)
              ?.image
              ?.type
              ?.getSpan(isTablet)!!
          }
        }
        isManageMode.let { isManageMode ->
          if (listItems?.isEmpty() == true && listDetails?.filterTypeLocal?.containsAll(Mode.getAll()) == true) {
            return@let
          }

          fragmentListDetailsManageButton.visibleIf(!isManageMode)
          fragmentListDetailsMoreButton.visibleIf(!isManageMode)
          fragmentListDetailsViewModeButton.visibleIf(!isManageMode)

          if (isManageMode) {
            fragmentListDetailsToolbar.title = getString(R.string.textChangeRanks)
            fragmentListDetailsToolbar.subtitle = getString(R.string.textChangeRanksSubtitle)
            fragmentListDetailsRecycler.doOnApplyWindowInsets { view, insets, _, _ ->
              val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
              view.updatePadding(
                top = if (layoutManager is GridLayoutManager) dimenToPx(R.dimen.spaceTiny) else 0,
                bottom = inset.bottom + recyclerPaddingBottom,
              )
            }
          } else {
            renderTitle(listDetails?.name ?: list.name, listItems?.size)
            fragmentListDetailsToolbar.subtitle = listDetails?.description
            fragmentListDetailsRecycler.doOnApplyWindowInsets { view, insets, _, _ ->
              val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
              view.updatePadding(
                top = if (layoutManager is GridLayoutManager) recyclerPaddingGridTop else recyclerPaddingTop,
                bottom = inset.bottom + recyclerPaddingBottom,
              )
            }
          }

          if (resetScroll?.consume() == true) {
            fragmentListDetailsRecycler.scrollToPosition(0)
            fragmentListDetailsFiltersView.translationY = 0F
          }
        }
        isFiltersVisible.let {
          fragmentListDetailsFiltersView.visibleIf(it)
        }
        isLoading.let {
          fragmentListDetailsLoadingView.visibleIf(it)
          if (it) disableUi() else enableUi()
        }
        deleteEvent?.let { event ->
          event.consume()?.let { activity?.onBackPressed() }
        }
      }
    }
  }

  override fun onListItemDragStarted(viewHolder: RecyclerView.ViewHolder) {
    touchHelper?.startDrag(viewHolder)
  }

  override fun onListItemSwipeStarted(viewHolder: RecyclerView.ViewHolder) {
    touchHelper?.startSwipe(viewHolder)
  }

  override fun onDestroyView() {
    adapter = null
    touchHelper = null
    layoutManager = null
    super.onDestroyView()
  }
}
