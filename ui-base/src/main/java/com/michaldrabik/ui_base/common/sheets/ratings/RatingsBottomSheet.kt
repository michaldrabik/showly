package com.michaldrabik.ui_base.common.sheets.ratings

import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import com.michaldrabik.ui_base.BaseBottomSheetFragment
import com.michaldrabik.ui_base.R
import com.michaldrabik.ui_base.common.views.RateValueView.Direction
import com.michaldrabik.ui_base.databinding.ViewRateSheetBinding
import com.michaldrabik.ui_base.utilities.events.Event
import com.michaldrabik.ui_base.utilities.events.MessageEvent
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.requireParcelable
import com.michaldrabik.ui_base.utilities.extensions.showErrorSnackbar
import com.michaldrabik.ui_base.utilities.extensions.showInfoSnackbar
import com.michaldrabik.ui_base.utilities.extensions.visible
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.TraktRating
import com.michaldrabik.ui_navigation.java.NavigationArgs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.parcelize.Parcelize

@AndroidEntryPoint
class RatingsBottomSheet : BaseBottomSheetFragment(R.layout.view_rate_sheet) {

  companion object {
    fun createBundle(
      id: IdTrakt,
      type: Options.Type,
      seasonNumber: Int? = null,
      episodeNumber: Int? = null,
    ): Bundle {
      val options = Options(id, type, seasonNumber, episodeNumber)
      return bundleOf(NavigationArgs.ARG_OPTIONS to options)
    }

    private const val INITIAL_RATING = 5
  }

  private val viewModel by viewModels<RatingsSheetViewModel>()
  private val binding by viewBinding(ViewRateSheetBinding::bind)

  private val options by lazy { requireParcelable<Options>(NavigationArgs.ARG_OPTIONS) }
  private val id by lazy { options.id }
  private val type by lazy { options.type }

  private val starsViews by lazy {
    with(binding) { listOf(star1, star2, star3, star4, star5, star6, star7, star8, star9, star10) }
  }
  private var selectedRating = INITIAL_RATING

  override fun getTheme(): Int = R.style.CustomBottomSheetDialog

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)
    setupView()

    launchAndRepeatStarted(
      { viewModel.uiState.collect { render(it) } },
      { viewModel.messageFlow.collect { renderSnackbar(it) } },
      { viewModel.eventFlow.collect { handleEvent(it) } },
      doAfterLaunch = { viewModel.loadRating(id, type) },
    )
  }

  private fun setupView() {
    renderRating(INITIAL_RATING)
    starsViews.forEach { star -> star.onClick { renderRating(it.tag.toString().toInt(), animate = true) } }
    binding.viewRateSheetSaveButton.onClick {
      viewModel.saveRating(
        rating = selectedRating,
        id = id,
        type = type,
        seasonNumber = options.seasonNumber,
        episodeNumber = options.episodeNumber,
      )
    }
    binding.viewRateSheetRemoveButton.onClick { viewModel.removeRating(id, type) }
  }

  private fun render(uiState: RatingsUiState) {
    with(uiState) {
      with(binding) {
        isLoading?.let {
          viewRateSheetProgress.visibleIf(it)
          viewRateSheetSaveButton.visibleIf(!it, gone = false)
          viewRateSheetRemoveButton.visibleIf(!it, gone = false)
          starsViews.forEach { view -> view.isEnabled = !it }
        }
        rating?.let {
          viewRateSheetSaveButton.isEnabled = true
          if (isLoading != true) {
            viewRateSheetRemoveButton.visibleIf(it != TraktRating.EMPTY)
          }
          viewRateSheetStarsLayout.visible()
          if (it != TraktRating.EMPTY && isLoading != true) {
            renderRating(it.rating)
          }
        }
      }
    }
  }

  private fun renderRating(
    rate: Int,
    animate: Boolean = false,
  ) {
    val currentRating = selectedRating
    selectedRating = rate.coerceIn(1..10)
    starsViews.forEach { it.setImageResource(R.drawable.ic_star_empty) }
    (1..selectedRating).forEachIndexed { index, _ ->
      starsViews[index].setImageResource(R.drawable.ic_star)
    }
    if (animate && currentRating != selectedRating) {
      val direction = if (currentRating > selectedRating) Direction.RIGHT else Direction.LEFT
      binding.viewRateSheetRating.setValueAnimated(selectedRating.toString(), direction)
    } else {
      binding.viewRateSheetRating.setValue(selectedRating.toString())
    }
  }

  private fun renderSnackbar(message: MessageEvent) {
    when (message) {
      is MessageEvent.Info -> binding.viewRateSheetSnackHost.showInfoSnackbar(getString(message.textRestId))
      is MessageEvent.Error -> binding.viewRateSheetSnackHost.showErrorSnackbar(getString(message.textRestId))
    }
  }

  private fun handleEvent(event: Event<*>) {
    when (event) {
      is FinishUiEvent -> closeWithSuccess(event.operation)
    }
  }

  private fun closeWithSuccess(operation: Options.Operation) {
    val result = bundleOf(NavigationArgs.RESULT to operation)
    setFragmentResult(NavigationArgs.REQUEST_RATING, result)
    closeSheet()
  }

  @Parcelize
  data class Options(
    val id: IdTrakt,
    val type: Type,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
  ) : Parcelable {

    enum class Type {
      SHOW,
      MOVIE,
      EPISODE,
      SEASON,
    }

    @Parcelize
    enum class Operation : Parcelable {
      SAVE,
      REMOVE,
    }
  }
}
