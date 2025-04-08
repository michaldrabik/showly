package com.michaldrabik.ui_settings

import com.michaldrabik.ui_settings.views.SettingsFiltersView

data class SettingsUiState(
  val isPremium: Boolean = false,
  val filter: SettingsFiltersView.SettingsFilter? = null,
)
