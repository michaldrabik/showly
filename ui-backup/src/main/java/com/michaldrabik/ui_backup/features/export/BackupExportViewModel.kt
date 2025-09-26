package com.michaldrabik.ui_backup.features.export

import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.ui_backup.features.export.cases.CreateBackupJsonUseCase
import com.michaldrabik.ui_backup.features.export.cases.CreateBackupSchemeFromJsonUseCase
import com.michaldrabik.ui_backup.features.export.model.BackupExportSchedule
import com.michaldrabik.ui_backup.features.export.workers.BackupExportScheduleWorker
import com.michaldrabik.ui_backup.model.BackupScheme
import com.michaldrabik.ui_base.Logger
import com.michaldrabik.ui_base.dates.DateFormatProvider
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.utilities.extensions.combine
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class BackupExportViewModel @Inject constructor(
  @Named("miscPreferences") private var miscPreferences: SharedPreferences,
  private val createBackupJsonUseCase: CreateBackupJsonUseCase,
  private val createBackupSchemeFromJsonUseCase: CreateBackupSchemeFromJsonUseCase,
  private val workManager: WorkManager,
  dateFormatProvider: DateFormatProvider,
) : ViewModel() {

  private val initialState = BackupExportUiState(
    backupExportSchedule = BackupExportSchedule.createFromName(
      miscPreferences.getString(BackupExportScheduleWorker.KEY_BACKUP_EXPORT_SCHEDULE, null),
    ),
    lastBackupExportTimestamp =
      miscPreferences.getLong(BackupExportScheduleWorker.KEY_LAST_LAST_BACKUP_EXPORT_TIMESTAMP, 0),
    dateFormat = dateFormatProvider.loadFullHourFormat(),
  )

  private val exportContentState = MutableStateFlow(initialState.exportContent)
  private val loadingState = MutableStateFlow(initialState.isLoading)
  private val errorState = MutableStateFlow(initialState.error)
  private val backupExportScheduleState = MutableStateFlow(initialState.backupExportSchedule)
  private val lastBackupExportTimestampState = MutableStateFlow(initialState.lastBackupExportTimestamp)
  private val dateFormatState = MutableStateFlow(initialState.dateFormat)

  /**
   * Run on-off export.
   */
  fun runOneOffExport(uri: Uri) {
    if (loadingState.value) return
    viewModelScope.launch {
      try {
        loadingState.update { true }
        val exportJson = createBackupJsonUseCase()
        exportContentState.update {
          ExportContentState(
            exportContent = exportJson,
            exportUri = uri,
          )
        }
      } catch (error: Throwable) {
        rethrowCancellation(error) {
          errorState.update { error }
          Logger.record(error, "BackupExportViewModel::runExport()")
        }
      } finally {
        loadingState.update { false }
      }
    }
  }

  /**
   * Validates the provided JSON input string and attempts to create a [BackupScheme] from it.
   *
   * @param jsonInput The JSON string to validate.
   * @return A [Result] object containing the parsed [BackupScheme] on success, or an [Error] on failure.
   */
  fun validateExportData(jsonInput: String): Result<BackupScheme> {
    val jsonError = Error("Failed to validate export file. Please try again or contact us if this keeps happening.")
    return createBackupSchemeFromJsonUseCase(jsonInput).fold(
      onSuccess = {
        if (it != null) {
          Result.success(it)
        } else {
          Result.failure(jsonError)
        }
      },
      onFailure = { exception ->
        rethrowCancellation(exception) {
          errorState.update { jsonError }
          Logger.record(it, "BackupExportViewModel::validateExportData()")
        }
        return Result.failure(jsonError)
      },
    )
  }

  /**
   * Called when export validation is successful.
   * Updates the last backup export timestamp in shared preferences and in the UI state.
   */
  fun onExportValidationSuccess() {
    val now = nowUtcMillis()
    miscPreferences.edit { putLong(BackupExportScheduleWorker.KEY_LAST_LAST_BACKUP_EXPORT_TIMESTAMP, now) }
    lastBackupExportTimestampState.update { now }
  }

  /**
   * Set up automatic export schedule.
   */
  fun saveExportBackupSchedule(
    directoryUri: Uri,
    schedule: BackupExportSchedule,
  ) {
    viewModelScope.launch {
      miscPreferences.edit { putString(BackupExportScheduleWorker.KEY_BACKUP_EXPORT_SCHEDULE, schedule.name) }
      miscPreferences.edit {
        putString(
          BackupExportScheduleWorker.KEY_BACKUP_EXPORT_DIRECTORY_URI,
          directoryUri.toString(),
        )
      }
      BackupExportScheduleWorker.schedulePeriodic(workManager, directoryUri, schedule)
      backupExportScheduleState.value = schedule
    }
  }

  /**
   * Set schedule to OFF. This cancels ongoing schedules.
   */
  fun saveExportBackupScheduleOff() {
    val offSchedule = BackupExportSchedule.OFF
    viewModelScope.launch {
      miscPreferences.edit { putString(BackupExportScheduleWorker.KEY_BACKUP_EXPORT_SCHEDULE, offSchedule.name) }
      miscPreferences.edit { remove(BackupExportScheduleWorker.KEY_BACKUP_EXPORT_DIRECTORY_URI) }
      BackupExportScheduleWorker.cancelAllPeriodic(workManager)
      backupExportScheduleState.value = offSchedule
    }
  }

  fun clearOneOffState() {
    loadingState.update { false }
    exportContentState.update { null }
    errorState.update { null }
  }

  val uiState = combine(
    loadingState,
    exportContentState,
    errorState,
    backupExportScheduleState,
    lastBackupExportTimestampState,
    dateFormatState,
  ) { s1, s2, s3, s4, s5, s6 ->
    BackupExportUiState(
      isLoading = s1,
      exportContent = s2,
      error = s3,
      backupExportSchedule = s4,
      lastBackupExportTimestamp = s5,
      dateFormat = s6,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = BackupExportUiState(),
  )
}
