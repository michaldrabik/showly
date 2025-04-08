package com.michaldrabik.ui_settings

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.common.OnTraktAuthorizeListener
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_settings.databinding.FragmentSettingsBinding
import com.michaldrabik.ui_settings.sections.spoilers.SettingsSpoilersFragment
import com.michaldrabik.ui_settings.views.SettingsFiltersView.SettingsFilter
import com.michaldrabik.ui_settings.views.SettingsFiltersView.SettingsFilter.BACKUP
import com.michaldrabik.ui_settings.views.SettingsFiltersView.SettingsFilter.GENERAL
import com.michaldrabik.ui_settings.views.SettingsFiltersView.SettingsFilter.MISC
import com.michaldrabik.ui_settings.views.SettingsFiltersView.SettingsFilter.NOTIFICATIONS
import com.michaldrabik.ui_settings.views.SettingsFiltersView.SettingsFilter.SPOILERS
import com.michaldrabik.ui_settings.views.SettingsFiltersView.SettingsFilter.TRAKT
import com.michaldrabik.ui_settings.views.SettingsFiltersView.SettingsFilter.WIDGETS
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment :
  BaseFragment<SettingsViewModel>(R.layout.fragment_settings),
  OnTraktAuthorizeListener {

  companion object {
    const val REQUEST_SETTINGS = "REQUEST_SETTINGS"
  }

  override val viewModel by viewModels<SettingsViewModel>()
  private val binding by viewBinding(FragmentSettingsBinding::bind)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setFragmentResultListener(REQUEST_SETTINGS) { _, _ ->
      childFragmentManager.fragments.forEach { fragment ->
        (fragment as? SettingsSpoilersFragment)?.refreshSettings()
      }
    }
  }

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)
    setupView()
    setupInsets()

    launchAndRepeatStarted(
      { viewModel.messageFlow.collect { showSnack(it) } },
      { viewModel.uiState.collect { render(it) } },
    )
  }

  override fun onStop() {
    binding.settingsFilters.clear()
    viewModel.setFilter(null)
    super.onStop()
  }

  private fun setupView() {
    with(binding) {
      settingsToolbar.setOnClickListener { activity?.onBackPressed() }
      settingsPremium.onClick { navigateTo(R.id.actionSettingsFragmentToPremium) }
      settingsFilters.onFilterClick = { viewModel.setFilter(it) }
    }
  }

  private fun setupInsets() {
    binding.settingsRoot.doOnApplyWindowInsets { view, insets, padding, _ ->
      val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      view.updatePadding(
        top = padding.top + inset.top,
        bottom = padding.bottom + inset.bottom,
      )
    }
  }

  private fun render(uiState: SettingsUiState) {
    uiState.run {
      renderFiltered(uiState.filter)
    }
  }

  private fun renderFiltered(filter: SettingsFilter?) {
    with(binding) {
      settingsCategoryTrakt.visibleIf(filter == TRAKT || filter == null)
      settingsCategoryGeneral.visibleIf(filter == GENERAL || filter == null)
      settingsCategoryNotifications.visibleIf(filter == NOTIFICATIONS || filter == null)
      settingsCategorySpoilers.visibleIf(filter == SPOILERS || filter == null)
      settingsCategoryWidgets.visibleIf(filter == WIDGETS || filter == null)
      settingsCategoryBackup.visibleIf(filter == BACKUP || filter == null)
      settingsCategoryMisc.visibleIf(filter == MISC || filter == null)
    }
  }

  override fun onAuthorizationResult(authData: Uri?) {
    childFragmentManager.fragments.forEach {
      (it as? OnTraktAuthorizeListener)?.onAuthorizationResult(authData)
    }
  }
}
