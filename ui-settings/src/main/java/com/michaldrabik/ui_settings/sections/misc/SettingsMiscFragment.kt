package com.michaldrabik.ui_settings.sections.misc

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import androidx.fragment.app.viewModels
import com.michaldrabik.common.Config
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.utilities.events.MessageEvent
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.openWebUrl
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_settings.BuildConfig
import com.michaldrabik.ui_settings.R
import com.michaldrabik.ui_settings.databinding.FragmentSettingsMiscBinding
import com.michaldrabik.ui_settings.helpers.PlayStoreHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsMiscFragment : BaseFragment<SettingsMiscViewModel>(R.layout.fragment_settings_misc) {

  override val viewModel by viewModels<SettingsMiscViewModel>()
  private val binding by viewBinding(FragmentSettingsMiscBinding::bind)

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)
    setupView()
    launchAndRepeatStarted(
      { viewModel.uiState.collect { render(it) } },
      { viewModel.messageFlow.collect { showSnack(it) } },
      doAfterLaunch = { viewModel.loadSettings() },
    )
  }

  private fun setupView() {
    with(binding) {
      settingsContactDevs.onClick { openMailMessage() }
      settingsRateApp.onClick { PlayStoreHelper.openPlayStorePage(requireActivity()) }
      settingsDeleteCache.onClick { viewModel.deleteImagesCache(requireAppContext()) }

      settingsTwitterIcon.onClick { openWebLink(Config.TWITTER_URL) }
      settingsTraktIcon.onClick { openWebLink(Config.TRAKT_URL) }
      settingsTmdbIcon.onClick { openWebLink(Config.TMDB_URL) }
      settingsInstagramIcon.onClick { openWebLink(Config.INSTAGRAM_URL) }
      settingsJustWatchIcon.onClick { openWebLink(Config.JUST_WATCH_URL) }
    }
  }

  @SuppressLint("SetTextI18n")
  private fun render(uiState: SettingsMiscUiState) {
    uiState.run {
      with(binding) {
        userId.let { settingsUserId.text = it }
        settingsVersion.text = "v${BuildConfig.VER_NAME} (${BuildConfig.VER_CODE})"
      }
    }
  }

  private fun openWebLink(url: String) {
    openWebUrl(url) ?: showSnack(MessageEvent.Info(R.string.errorCouldNotFindApp))
  }

  private fun openMailMessage() {
    with(binding) {
      val id = "${settingsVersion.text}, ${settingsUserId.text}"
      val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:".toUri()
        putExtra(Intent.EXTRA_EMAIL, arrayOf(Config.DEVELOPER_MAIL))
        putExtra(Intent.EXTRA_SUBJECT, "Showly Message/Issue (Version: $id)")
      }
      if (intent.resolveActivity(requireActivity().packageManager) != null) {
        startActivity(intent)
      }
    }
  }
}
