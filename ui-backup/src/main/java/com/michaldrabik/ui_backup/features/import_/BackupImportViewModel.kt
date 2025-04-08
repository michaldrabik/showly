package com.michaldrabik.ui_backup.features.import_

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.ui_backup.BackupConfig.SCHEME_VERSION
import com.michaldrabik.ui_backup.features.import_.model.BackupImportStatus.Idle
import com.michaldrabik.ui_backup.features.import_.model.BackupImportStatus.Initializing
import com.michaldrabik.ui_backup.features.import_.workers.BackupImportWorker
import com.michaldrabik.ui_backup.migrations.BackupMigrationV1
import com.michaldrabik.ui_backup.model.BackupScheme
import com.michaldrabik.ui_backup.model.v1.BackupScheme1
import com.michaldrabik.ui_base.Logger
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class BackupImportViewModel @Inject constructor(
  private val backupImportWorker: BackupImportWorker,
) : ViewModel() {

  private val initialState = BackupImportUiState()

  private val importingState = MutableStateFlow(initialState.isImporting)
  private val successState = MutableStateFlow(initialState.isSuccess)
  private val errorState = MutableStateFlow(initialState.isError)

  init {
    backupImportWorker.statusListener = { status ->
      importingState.update { status }
      Timber.d("Importing state: $status")
    }
  }

  fun runImport(jsonInput: String) {
    if (importingState.value != Idle) return
    viewModelScope.launch {
      try {
        importingState.update { Initializing }
        delay(1.seconds)
        val importScheme = createImportData(jsonInput)
        if (importScheme != null) {
          backupImportWorker.run(importScheme)
          successState.update { true }
        }
      } catch (error: Throwable) {
        rethrowCancellation(error) {
          errorState.update { error }
        }
      } finally {
        importingState.update { Idle }
      }
    }
  }

  private fun createImportData(jsonInput: String): BackupScheme? {
    val moshi = Moshi
      .Builder()
      .add(KotlinJsonAdapterFactory())
      .build()

    try {
      val version = jsonInput
        .substringAfter("version\":")
        .substringBefore(",")
        .trim()
        .toInt()

      if (version < SCHEME_VERSION) {
        val jsonAdapter = moshi.adapter(BackupScheme1::class.java)
        val migrationScheme = jsonAdapter.fromJson(jsonInput)!!
        return BackupMigrationV1.migrate(migrationScheme)
      }

      val jsonAdapter = moshi.adapter(BackupScheme::class.java)
      return jsonAdapter.fromJson(jsonInput)
    } catch (error: Throwable) {
      rethrowCancellation(error) {
        errorState.update { Error("Invalid Showly backup file.\n${error.localizedMessage}") }
        Logger.record(error, "BackupImportViewModel::createImportData()")
      }
      return null
    }
  }

  fun clearState() {
    importingState.update { Idle }
    successState.update { false }
    errorState.update { null }
  }

  val uiState = combine(
    importingState,
    successState,
    errorState,
  ) { s1, s2, s3 ->
    BackupImportUiState(
      isImporting = s1,
      isSuccess = s2,
      isError = s3,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = BackupImportUiState(),
  )
}
