package com.michaldrabik.ui_settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.viewmodel.ChannelsDelegate
import com.michaldrabik.ui_base.viewmodel.DefaultChannelsDelegate
import com.michaldrabik.ui_settings.views.SettingsFiltersView.SettingsFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor() :
  ViewModel(),
  ChannelsDelegate by DefaultChannelsDelegate() {

    private val premiumState = MutableStateFlow(false)
    private val filterState = MutableStateFlow<SettingsFilter?>(null)

    fun setFilter(filter: SettingsFilter?) {
      filterState.value = filter
    }

    val uiState = combine(
      premiumState,
      filterState,
    ) { s1, s2 ->
      SettingsUiState(
        isPremium = s1,
        filter = s2,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
      initialValue = SettingsUiState(),
    )
  }
