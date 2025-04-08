package com.michaldrabik.ui_trakt_sync

import android.content.Context
import android.content.SharedPreferences
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.michaldrabik.common.errors.ErrorHelper
import com.michaldrabik.common.errors.ShowlyError.AccountLockedError
import com.michaldrabik.common.errors.ShowlyError.CoroutineCancellation
import com.michaldrabik.repository.UserTraktManager
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_base.Logger
import com.michaldrabik.ui_base.dates.DateFormatProvider
import com.michaldrabik.ui_base.events.Event
import com.michaldrabik.ui_base.events.EventsManager
import com.michaldrabik.ui_base.events.TraktSyncAuthError
import com.michaldrabik.ui_base.events.TraktSyncError
import com.michaldrabik.ui_base.events.TraktSyncProgress
import com.michaldrabik.ui_base.events.TraktSyncStart
import com.michaldrabik.ui_base.events.TraktSyncSuccess
import com.michaldrabik.ui_base.trakt.TraktSyncWorker
import com.michaldrabik.ui_base.utilities.events.MessageEvent
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.utilities.extensions.combine
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import com.michaldrabik.ui_base.utilities.extensions.withApiAtLeast
import com.michaldrabik.ui_base.viewmodel.ChannelsDelegate
import com.michaldrabik.ui_base.viewmodel.DefaultChannelsDelegate
import com.michaldrabik.ui_model.TraktSyncSchedule
import com.michaldrabik.ui_trakt_sync.TraktSyncUiEvent.RequestNotificationsPermission
import com.michaldrabik.ui_trakt_sync.TraktSyncUiEvent.StartAuthorization
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class TraktSyncViewModel @Inject constructor(
  @Named("miscPreferences") private var miscPreferences: SharedPreferences,
  private val userManager: UserTraktManager,
  private val workManager: WorkManager,
  private val settingsRepository: SettingsRepository,
  private val dateFormatProvider: DateFormatProvider,
  private val eventsManager: EventsManager,
) : ViewModel(),
  ChannelsDelegate by DefaultChannelsDelegate(),
  Observer<MutableList<WorkInfo>> {

  private val progressState = MutableStateFlow(false)
  private val progressStatusState = MutableStateFlow("")
  private val authorizedState = MutableStateFlow(false)
  private val traktSyncScheduleState = MutableStateFlow(TraktSyncSchedule.OFF)
  private val quickSyncEnabledState = MutableStateFlow(false)
  private val dateFormatState = MutableStateFlow<DateTimeFormatter?>(null)
  private val traktSyncTimestampState = MutableStateFlow(0L)

  init {
    viewModelScope.launch {
      eventsManager.events.collect { handleEvent(it) }
    }
    workManager.getWorkInfosByTagLiveData(TraktSyncWorker.TAG_ID).observeForever { work ->
      progressState.value = work.any { it.state == WorkInfo.State.RUNNING }
    }
  }

  fun invalidate() {
    viewModelScope.launch {
      val settings = settingsRepository.load()

      authorizedState.value = userManager.isAuthorized()
      traktSyncScheduleState.value = settings.traktSyncSchedule
      quickSyncEnabledState.value = settings.traktQuickSyncEnabled
      dateFormatState.value = dateFormatProvider.loadFullHourFormat()
      traktSyncTimestampState.value = miscPreferences.getLong(TraktSyncWorker.KEY_LAST_SYNC_TIMESTAMP, 0)
    }
  }

  fun checkNotificationsPermission(context: Context) {
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

  fun authorizeTrakt(code: String?) {
    viewModelScope.launch {
      try {
        if (code.isNullOrBlank()) {
          throw IllegalStateException("Invalid Trakt authorization code.")
        }
        userManager.authorize(code)
        messageChannel.send(MessageEvent.Info(R.string.textTraktLoginSuccess))
        invalidate()
        saveTraktQuickRemove()
      } catch (error: Throwable) {
        when (ErrorHelper.parse(error)) {
          is CoroutineCancellation -> rethrowCancellation(error)
          is AccountLockedError -> messageChannel.send(MessageEvent.Error(R.string.errorTraktLocked))
          else -> messageChannel.send(MessageEvent.Error(R.string.errorAuthorization))
        }
        Logger.record(error, "TraktSyncViewModel::authorizeTrakt()")
      }
    }
  }

  fun saveTraktSyncSchedule(schedule: TraktSyncSchedule) {
    viewModelScope.launch {
      val settings = settingsRepository.load()
      settings.let {
        val new = it.copy(traktSyncSchedule = schedule)
        settingsRepository.update(new)
      }
      TraktSyncWorker.schedulePeriodic(workManager, schedule, cancelExisting = true)
      traktSyncScheduleState.value = schedule
    }
  }

  private fun saveTraktQuickRemove() {
    viewModelScope.launch {
      val settings = settingsRepository.load()
      settings.let {
        val new = it.copy(traktQuickRemoveEnabled = true)
        settingsRepository.update(new)
      }
    }
  }

  fun handleEvent(event: Event) {
    viewModelScope.launch {
      when (event) {
        is TraktSyncStart -> {
          progressState.value = true
          progressStatusState.value = ""
          messageChannel.send(MessageEvent.Info(R.string.textTraktSyncStarted))
        }
        is TraktSyncProgress -> {
          progressState.value = true
          progressStatusState.value = event.status
        }
        is TraktSyncSuccess -> {
          progressState.value = false
          progressStatusState.value = ""
          traktSyncTimestampState.value = miscPreferences.getLong(TraktSyncWorker.KEY_LAST_SYNC_TIMESTAMP, 0)
          messageChannel.send(MessageEvent.Info(R.string.textTraktSyncComplete))
        }
        is TraktSyncError -> {
          progressState.value = false
          progressStatusState.value = ""
          messageChannel.send(MessageEvent.Info(R.string.textTraktSyncError))
        }
        is TraktSyncAuthError -> {
          progressState.value = false
          progressStatusState.value = ""
          messageChannel.send(MessageEvent.Error(R.string.errorTraktAuthorization))
          eventChannel.send(TraktSyncUiEvent.Finish)
        }
        else -> Timber.d("Unsupported sync event")
      }
    }
  }

  fun startImport(
    isImport: Boolean,
    isExport: Boolean,
  ) {
    TraktSyncWorker.scheduleOneOff(workManager, isImport, isExport, false)
  }

  override fun onChanged(value: MutableList<WorkInfo>) {
    val isAnyRunning = value.any { it.state == WorkInfo.State.RUNNING }
    progressState.value = isAnyRunning
  }

  val uiState = combine(
    progressState,
    progressStatusState,
    authorizedState,
    traktSyncScheduleState,
    quickSyncEnabledState,
    dateFormatState,
    traktSyncTimestampState,
  ) { s1, s2, s3, s4, s5, s6, s7 ->
    TraktSyncUiState(
      isProgress = s1,
      progressStatus = s2,
      isAuthorized = s3,
      traktSyncSchedule = s4,
      quickSyncEnabled = s5,
      dateFormat = s6,
      lastTraktSyncTimestamp = s7,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = TraktSyncUiState(),
  )
}
