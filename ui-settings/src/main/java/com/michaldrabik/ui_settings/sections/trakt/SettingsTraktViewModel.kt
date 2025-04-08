package com.michaldrabik.ui_settings.sections.trakt

import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.common.errors.ErrorHelper
import com.michaldrabik.common.errors.ShowlyError
import com.michaldrabik.ui_base.Logger
import com.michaldrabik.ui_base.utilities.events.MessageEvent
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import com.michaldrabik.ui_base.utilities.extensions.withApiAtLeast
import com.michaldrabik.ui_base.viewmodel.ChannelsDelegate
import com.michaldrabik.ui_base.viewmodel.DefaultChannelsDelegate
import com.michaldrabik.ui_model.Settings
import com.michaldrabik.ui_model.TraktSyncSchedule
import com.michaldrabik.ui_settings.R
import com.michaldrabik.ui_settings.sections.trakt.SettingsTraktUiEvent.RequestNotificationsPermission
import com.michaldrabik.ui_settings.sections.trakt.SettingsTraktUiEvent.StartAuthorization
import com.michaldrabik.ui_settings.sections.trakt.cases.SettingsTraktCase
import com.michaldrabik.ui_settings.sections.trakt.cases.SettingsTraktMainCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsTraktViewModel @Inject constructor(
  private val mainCase: SettingsTraktMainCase,
  private val traktCase: SettingsTraktCase,
) : ViewModel(),
  ChannelsDelegate by DefaultChannelsDelegate() {

  private val settingsState = MutableStateFlow<Settings?>(null)

  private val premiumState = MutableStateFlow(false)
  private val signedInTraktState = MutableStateFlow(false)
  private val signingInState = MutableStateFlow(false)
  private val traktNameState = MutableStateFlow("")

  fun loadSettings() {
    viewModelScope.launch {
      refreshSettings()
    }
  }

  private suspend fun refreshSettings() {
    settingsState.value = mainCase.getSettings()
    signedInTraktState.value = traktCase.isTraktAuthorized()
    traktNameState.value = traktCase.getTraktUsername()
  }

  fun startAuthorization(context: Context) {
    viewModelScope.launch {
      withApiAtLeast(33) {
        val areNotificationsEnabled = NotificationManagerCompat
          .from(context.applicationContext)
          .areNotificationsEnabled()

        if (!areNotificationsEnabled) {
          eventChannel.send(RequestNotificationsPermission)
          return@launch
        }
      }
      eventChannel.send(StartAuthorization)
    }
  }

  fun authorizeTrakt(authData: Uri?) {
    if (authData == null) {
      Logger.record(Error("authData is null"), "SettingsViewModel::authorizeTrakt()")
      return
    }
    viewModelScope.launch {
      try {
        signingInState.value = true
        traktCase.authorizeTrakt(authData)
        traktCase.enableTraktQuickRemove(true)
        refreshSettings()
        messageChannel.send(MessageEvent.Info(R.string.textTraktLoginSuccess))
      } catch (error: Throwable) {
        Logger.record(error, "SettingsViewModel::authorizeTrakt()")
        when (ErrorHelper.parse(error)) {
          is ShowlyError.CoroutineCancellation -> rethrowCancellation(error)
          is ShowlyError.AccountLockedError -> messageChannel.send(MessageEvent.Error(R.string.errorTraktLocked))
          else -> messageChannel.send(MessageEvent.Error(R.string.errorAuthorization))
        }
      } finally {
        signingInState.value = false
      }
    }
  }

  fun logoutTrakt() {
    viewModelScope.launch {
      traktCase.logoutTrakt()
      messageChannel.send(MessageEvent.Info(R.string.textTraktLogoutSuccess))
      refreshSettings()
    }
  }

  fun enableQuickRemove(enable: Boolean) {
    viewModelScope.launch {
      traktCase.enableTraktQuickRemove(enable)
      refreshSettings()
    }
  }

  fun enableQuickSync(enable: Boolean) {
    viewModelScope.launch {
      traktCase.enableTraktQuickSync(enable)
      refreshSettings()
    }
  }

  fun setTraktSyncSchedule(schedule: TraktSyncSchedule) {
    viewModelScope.launch {
      traktCase.setTraktSyncSchedule(schedule)
      refreshSettings()
    }
  }

  val uiState = combine(
    settingsState,
    signingInState,
    signedInTraktState,
    traktNameState,
    premiumState,
  ) { s1, s2, s3, s4, s5 ->
    SettingsTraktUiState(
      settings = s1,
      isSigningIn = s2,
      isSignedInTrakt = s3,
      traktUsername = s4,
      isPremium = s5,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = SettingsTraktUiState(),
  )
}
