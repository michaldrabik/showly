package com.michaldrabik.ui_gallery.fanart

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.content.res.Configuration
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.addCallback
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.utilities.extensions.dimenToPx
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.ui_base.utilities.extensions.gone
import com.michaldrabik.ui_base.utilities.extensions.nextPage
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.openWebUrl
import com.michaldrabik.ui_base.utilities.extensions.updateTopMargin
import com.michaldrabik.ui_base.utilities.extensions.visible
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_gallery.R
import com.michaldrabik.ui_gallery.databinding.FragmentArtGalleryBinding
import com.michaldrabik.ui_gallery.fanart.recycler.ArtGalleryAdapter
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.ImageFamily
import com.michaldrabik.ui_model.ImageFamily.SHOW
import com.michaldrabik.ui_model.ImageType
import com.michaldrabik.ui_model.ImageType.POSTER
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_FAMILY
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_MOVIE_ID
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_SHOW_ID
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_TYPE
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@SuppressLint("SetTextI18n", "DefaultLocale", "SourceLockedOrientationActivity")
@AndroidEntryPoint
class ArtGalleryFragment : BaseFragment<ArtGalleryViewModel>(R.layout.fragment_art_gallery) {

  override val viewModel by viewModels<ArtGalleryViewModel>()
  private val binding by viewBinding(FragmentArtGalleryBinding::bind)

  private val showId by lazy { IdTrakt(arguments?.getLong(ARG_SHOW_ID, -1) ?: -1) }
  private val movieId by lazy { IdTrakt(arguments?.getLong(ARG_MOVIE_ID, -1) ?: -1) }
  private val family by lazy { arguments?.getSerializable(ARG_FAMILY) as ImageFamily }
  private val type by lazy { arguments?.getSerializable(ARG_TYPE) as ImageType }

  private var galleryAdapter: ArtGalleryAdapter? = null

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)
    if (type != POSTER) {
      requireActivity().requestedOrientation = SCREEN_ORIENTATION_FULL_USER
    }
    setupView()
    setupInsets()

    viewLifecycleOwner.lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        with(viewModel) {
          launch { uiState.collect { render(it) } }
          val id = if (family == SHOW) showId else movieId
          loadImages(id, family, type)
        }
      }
    }
  }

  override fun onDestroyView() {
    galleryAdapter = null
    requireActivity().requestedOrientation = SCREEN_ORIENTATION_PORTRAIT
    super.onDestroyView()
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    with(binding) {
      when (newConfig.orientation) {
        ORIENTATION_LANDSCAPE -> {
          artGalleryPagerIndicatorWhite.visible()
          artGalleryPagerIndicator.gone()
          artGalleryPagerIndicatorWhite.setViewPager(artGalleryPager)
        }
        ORIENTATION_PORTRAIT -> {
          artGalleryPagerIndicatorWhite.gone()
          artGalleryPagerIndicator.visible()
          artGalleryPagerIndicator.setViewPager(artGalleryPager)
        }
        else -> Timber.d("Unused orientation")
      }
    }
  }

  private fun setupView() {
    with(binding) {
      artGalleryBackArrow.onClick {
        requireActivity().onBackPressed()
      }
      artGalleryBrowserIcon.onClick {
        val currentIndex = artGalleryPager.currentItem
        val image = galleryAdapter?.getItem(currentIndex)
        image?.fullFileUrl?.let { openWebUrl(it) }
      }
      galleryAdapter = ArtGalleryAdapter(
        onItemClickListener = { artGalleryPager.nextPage() },
      )
      artGalleryPager.run {
        adapter = galleryAdapter
        offscreenPageLimit = 2
        artGalleryPagerIndicator.setViewPager(this)
        adapter?.registerAdapterDataObserver(artGalleryPagerIndicator.adapterDataObserver)
      }
    }
  }

  private fun setupInsets() {
    requireView().doOnApplyWindowInsets { view, insets, _, _ ->
      val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      view.updatePadding(bottom = inset.bottom)
      with(binding) {
        artGalleryBackArrow.updateTopMargin(inset.top)
        artGalleryBrowserIcon.updateTopMargin(inset.top)
        artGalleryPagerIndicator.updateLayoutParams<MarginLayoutParams> {
          updateMargins(bottom = inset.bottom + dimenToPx(R.dimen.spaceNormal))
        }
        artGalleryPagerIndicatorWhite.updateLayoutParams<MarginLayoutParams> {
          updateMargins(bottom = inset.bottom + dimenToPx(R.dimen.spaceNormal))
        }
        artGalleryImagesProgress.updateLayoutParams<MarginLayoutParams> {
          updateMargins(bottom = inset.bottom + dimenToPx(R.dimen.spaceNormal))
        }
      }
    }
  }

  private fun render(uiState: ArtGalleryUiState) {
    uiState.run {
      with(binding) {
        images?.let {
          galleryAdapter?.setItems(it, type)
          artGalleryEmptyView.visibleIf(it.isEmpty())
          artGalleryBrowserIcon.visibleIf(it.isNotEmpty())
        }
        artGalleryImagesProgress.visibleIf(isLoading)
      }
    }
  }

  override fun setupBackPressed() {
    val dispatcher = requireActivity().onBackPressedDispatcher
    dispatcher.addCallback(viewLifecycleOwner) {
      isEnabled = false
      findNavControl()?.popBackStack()
    }
  }
}
