package com.michaldrabik.ui_show.episodes

import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.michaldrabik.common.Config
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.common.WidgetsProvider
import com.michaldrabik.ui_base.common.sheets.date_selection.DateSelectionBottomSheet
import com.michaldrabik.ui_base.common.sheets.date_selection.DateSelectionBottomSheet.Companion.REQUEST_DATE_SELECTION
import com.michaldrabik.ui_base.common.sheets.date_selection.DateSelectionBottomSheet.Companion.RESULT_DATE_SELECTION
import com.michaldrabik.ui_base.common.sheets.date_selection.DateSelectionBottomSheet.Result
import com.michaldrabik.ui_base.common.sheets.ratings.RatingsBottomSheet
import com.michaldrabik.ui_base.common.sheets.ratings.RatingsBottomSheet.Options.Operation
import com.michaldrabik.ui_base.common.sheets.ratings.RatingsBottomSheet.Options.Type
import com.michaldrabik.ui_base.common.sheets.remove_trakt.RemoveTraktBottomSheet
import com.michaldrabik.ui_base.utilities.SnackbarHost
import com.michaldrabik.ui_base.utilities.events.MessageEvent
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.navigateToSafe
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.optionalParcelable
import com.michaldrabik.ui_base.utilities.extensions.requireParcelable
import com.michaldrabik.ui_base.utilities.extensions.showInfoSnackbar
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_episodes.details.EpisodeDetailsBottomSheet
import com.michaldrabik.ui_model.Episode
import com.michaldrabik.ui_model.EpisodeBundle
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Season
import com.michaldrabik.ui_navigation.java.NavigationArgs
import com.michaldrabik.ui_show.R
import com.michaldrabik.ui_show.databinding.FragmentShowDetailsEpisodesBinding
import com.michaldrabik.ui_show.episodes.ShowDetailsEpisodesEvent.Finish
import com.michaldrabik.ui_show.episodes.ShowDetailsEpisodesEvent.OpenEpisodeDateSelection
import com.michaldrabik.ui_show.episodes.ShowDetailsEpisodesEvent.OpenEpisodeDetails
import com.michaldrabik.ui_show.episodes.ShowDetailsEpisodesEvent.OpenRateSeason
import com.michaldrabik.ui_show.episodes.ShowDetailsEpisodesEvent.OpenSeasonDateSelection
import com.michaldrabik.ui_show.episodes.ShowDetailsEpisodesEvent.RemoveFromTrakt
import com.michaldrabik.ui_show.episodes.ShowDetailsEpisodesEvent.RequestWidgetsUpdate
import com.michaldrabik.ui_show.episodes.recycler.EpisodesAdapter
import com.michaldrabik.ui_show.sections.seasons.recycler.SeasonListItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.util.Locale

@AndroidEntryPoint
class ShowDetailsEpisodesFragment :
  BaseFragment<ShowDetailsEpisodesViewModel>(
    R.layout.fragment_show_details_episodes,
  ) {

  companion object {
    fun createBundle(
      showId: IdTrakt,
      seasonId: IdTrakt,
    ): Bundle =
      bundleOf(
        NavigationArgs.ARG_OPTIONS to Options(showId, seasonId),
      )
  }

  override val navigationId = R.id.showDetailsEpisodesFragment
  private val binding by viewBinding(FragmentShowDetailsEpisodesBinding::bind)

  override val viewModel by viewModels<ShowDetailsEpisodesViewModel>()

  private var episodesAdapter: EpisodesAdapter? = null
  private var isLocked = true

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
      { viewModel.eventFlow.collect { handleEvent(it as ShowDetailsEpisodesEvent<*>) } },
    )
  }

  override fun onResume() {
    super.onResume()
    viewModel.launchRefreshWatchedEpisodes()
  }

  private fun setupView() {
    with(binding) {
      episodesBackArrow.onClick { findNavControl()?.popBackStack() }
      episodesTitle.onClick { findNavControl()?.popBackStack() }
      episodesUnlockButton.onClick(safe = false) { toggleEpisodesLock() }
      listOf(episodesSeasonRateButton, episodesSeasonMyStarIcon).onClick {
        viewModel.openRateSeasonDialog()
      }
    }
  }

  private fun setupInsets() {
    with(binding) {
      episodesRoot.doOnApplyWindowInsets { view, insets, padding, _ ->
        val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(top = padding.top + inset.top)
        episodesRecycler.updatePadding(bottom = episodesRecycler.paddingBottom + inset.bottom)
      }
    }
  }

  private fun setupRecycler() {
    episodesAdapter = EpisodesAdapter(
      itemClickListener = { episode: Episode, isWatched: Boolean ->
        viewModel.openEpisodeDetails(episode, isWatched)
      },
      itemCheckedListener = { episode: Episode, isChecked: Boolean ->
        viewModel.onEpisodeCheck(episode, isChecked)
      },
    )
    binding.episodesRecycler.apply {
      setHasFixedSize(true)
      adapter = episodesAdapter
      layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
      itemAnimator = null
    }
  }

  private fun render(uiState: ShowDetailsEpisodesUiState) {
    with(uiState) {
      with(binding) {
        season?.let {
          episodesTitle.text =
            if (it.season.isSpecial()) {
              getString(R.string.textSpecials)
            } else {
              String.format(Locale.ENGLISH, getString(R.string.textSeason), it.season.number)
            }
          episodesOverview.text = it.season.overview
          episodesOverview.visibleIf(it.season.overview.isNotBlank())
          episodesCheckbox.run {
            isEnabled = it.episodes.all { ep -> ep.episode.hasAired(it.season) } || !isLocked
            isChecked = it.isWatched
            setOnClickListener {
              viewModel.onSeasonChecked(season, isChecked)
              if (isChecked) {
                isChecked = false
              }
            }
          }
          episodesUnlockButton.visibleIf(
            !it.season.isSpecial() &&
              it.episodes.any { ep ->
                !ep.episode.hasAired(it.season)
              },
          )

          renderSeasonRating(season)
        }
        episodes?.let {
          episodesAdapter?.setItems(it)
          if (isInitialLoad == true) {
            episodesRecycler.scheduleLayoutAnimation()
          }
        }
      }
    }
  }

  private fun renderSeasonRating(season: SeasonListItem) {
    with(binding) {
      val seasonRating = season.season.rating
      episodesStarIcon.visibleIf(seasonRating > 0F)
      episodesSeasonRating.visibleIf(seasonRating > 0F)

      val seasonRatingString = String.format(Locale.ENGLISH, "%.1f", seasonRating)
      if (!season.isWatched && season.isRatingHidden) {
        episodesSeasonRating.tag = seasonRatingString
        episodesSeasonRating.text = Config.SPOILERS_RATINGS_HIDE_SYMBOL
        if (season.isRatingTapToReveal) {
          with(episodesSeasonRating) {
            onClick {
              tag?.let { text = it.toString() }
              isClickable = false
            }
          }
        }
      } else {
        episodesSeasonRating.text = seasonRatingString
      }

      val ratingState = season.userRating
      episodesSeasonRateButton.visibleIf(ratingState.userRating == null)
      episodesSeasonMyStarIcon.visibleIf(ratingState.userRating != null)
      episodesSeasonMyRating.visibleIf(ratingState.userRating != null)
      ratingState.userRating?.let {
        episodesSeasonMyStarIcon.isEnabled = true
        episodesSeasonMyRating.text = String.format(Locale.ENGLISH, "%d", it.rating)
      }
    }
  }

  private fun handleEvent(event: ShowDetailsEpisodesEvent<*>) {
    when (event) {
      is RemoveFromTrakt -> openRemoveTraktSheet(event)
      is OpenEpisodeDetails -> openEpisodeDetails(event.bundle, event.isWatched)
      is OpenRateSeason -> openRateSeasonDialog(event.season)
      is OpenEpisodeDateSelection -> openDateSelectionDialog(event.episode)
      is OpenSeasonDateSelection -> openDateSelectionDialog(event.season)
      is RequestWidgetsUpdate -> (requireAppContext() as WidgetsProvider).requestShowsWidgetsUpdate()
      is Finish -> findNavControl()?.popBackStack()
    }
  }

  private fun toggleEpisodesLock() {
    isLocked = !isLocked

    with(binding) {
      episodesUnlockButton.setImageResource(if (isLocked) R.drawable.ic_locked else R.drawable.ic_unlocked)
      episodesCheckbox.isEnabled = !isLocked
    }
    episodesAdapter?.toggleEpisodesLock()
  }

  private fun openEpisodeDetails(
    episodeBundle: EpisodeBundle,
    isWatched: Boolean,
  ) {
    val (episode, season, show) = episodeBundle
    setFragmentResultListener(NavigationArgs.REQUEST_EPISODE_DETAILS) { _, bundle ->
      when {
        bundle.containsKey(NavigationArgs.ACTION_EPISODE_TAB_SELECTED) -> {
          val selectedEpisode = bundle.requireParcelable<Episode>(NavigationArgs.ACTION_EPISODE_TAB_SELECTED)
          viewModel.openEpisodeDetails(selectedEpisode)
        }
        bundle.containsKey(NavigationArgs.ACTION_RATING_CHANGED) -> {
          viewModel.loadEpisodesRating()
        }
      }
    }

    val bundle = EpisodeDetailsBottomSheet.createBundle(
      ids = show.ids,
      episode = episode,
      seasonEpisodesIds = season.episodes.map { it.number },
      isWatched = isWatched,
      showTabs = true,
    )
    navigateToSafe(R.id.actionEpisodesFragmentToEpisodesDetails, bundle)
  }

  private fun openRemoveTraktSheet(event: RemoveFromTrakt) {
    setFragmentResultListener(NavigationArgs.REQUEST_REMOVE_TRAKT) { _, bundle ->
      if (bundle.getBoolean(NavigationArgs.RESULT, false)) {
        val text = resources.getString(R.string.textTraktSyncRemovedFromTrakt)
        (requireActivity() as SnackbarHost).provideSnackbarLayout().showInfoSnackbar(text)
      }
    }
    val args = RemoveTraktBottomSheet.createBundle(event.traktIds, event.mode)
    navigateToSafe(event.actionId, args)
  }

  private fun openRateSeasonDialog(season: Season) {
    setFragmentResultListener(NavigationArgs.REQUEST_RATING) { _, bundle ->
      when (bundle.optionalParcelable<Operation>(NavigationArgs.RESULT)) {
        Operation.SAVE -> showSnack(MessageEvent.Info(R.string.textRateSaved))
        Operation.REMOVE -> showSnack(MessageEvent.Info(R.string.textRateRemoved))
        else -> Timber.w("Unknown result")
      }
      viewModel.loadSeasonRating()
    }

    val bundle = RatingsBottomSheet.createBundle(
      id = season.ids.trakt,
      type = Type.SEASON,
      seasonNumber = season.number,
    )
    navigateToSafe(R.id.actionEpisodesFragmentToRating, bundle)
  }

  private fun openDateSelectionDialog(episode: Episode) {
    setFragmentResultListener(REQUEST_DATE_SELECTION) { _, bundle ->
      when (val result = bundle.requireParcelable<Result>(RESULT_DATE_SELECTION)) {
        is Result.Now -> viewModel.setEpisodeWatched(episode, true)
        is Result.CustomDate -> viewModel.setEpisodeWatched(episode, true, result.date)
        is Result.ReleaseDate -> viewModel.setEpisodeWatched(episode, true, result.date)
      }
    }
    val options = DateSelectionBottomSheet.createBundle(episode.firstAired)
    navigateToSafe(R.id.actionEpisodesFragmentToDateSelection, options)
  }

  private fun openDateSelectionDialog(season: SeasonListItem) {
    setFragmentResultListener(REQUEST_DATE_SELECTION) { _, bundle ->
      when (val result = bundle.requireParcelable<Result>(RESULT_DATE_SELECTION)) {
        is Result.Now -> viewModel.setSeasonWatched(season, true)
        is Result.CustomDate -> viewModel.setSeasonWatched(season, true, result.date)
        is Result.ReleaseDate -> viewModel.setSeasonWatched(season, true, result.date)
      }
    }
    val options = DateSelectionBottomSheet.createBundle(season.season.firstAired)
    navigateToSafe(R.id.actionEpisodesFragmentToDateSelection, options)
  }

  override fun onDestroyView() {
    episodesAdapter = null
    super.onDestroyView()
  }

  @Parcelize
  data class Options(
    val showId: IdTrakt,
    val seasonId: IdTrakt,
  ) : Parcelable
}
