package com.michaldrabik.ui_backup.features.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.ui_backup.features.export.workers.BackupExportWorker
import com.michaldrabik.ui_backup.model.BackupScheme
import com.michaldrabik.ui_base.Logger
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupExportViewModel @Inject constructor(
  private val backupExportWorker: BackupExportWorker,
) : ViewModel() {

  private val initialState = BackupExportUiState()

  private val exportContentState = MutableStateFlow(initialState.exportContent)
  private val loadingState = MutableStateFlow(initialState.isLoading)
  private val errorState = MutableStateFlow(initialState.error)

  fun runExport(uri: Uri) {
    if (loadingState.value) return
    viewModelScope.launch {
      try {
        loadingState.update { true }
        val exportResult = backupExportWorker.run()
        createExportJsonFile(exportResult, uri)
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

  private fun createExportJsonFile(
    exportContent: BackupScheme,
    uri: Uri,
  ) {
    val moshi = Moshi
      .Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
    val jsonAdapter = moshi.adapter(BackupScheme::class.java)
    exportContentState.update {
      ExportContentState(
        exportContent = jsonAdapter.toJson(exportContent),
        exportUri = uri,
      )
    }
  }

  fun validateExportData(jsonInput: String): Result<BackupScheme> {
    val moshi = Moshi
      .Builder()
      .add(KotlinJsonAdapterFactory())
      .build()

    val jsonAdapter = moshi.adapter(BackupScheme::class.java)
    val jsonError = Error("Failed to validate export file. Please try again or contact us if this keeps happening.")

    try {
      val result = jsonAdapter.fromJson(jsonInput)
      result?.let {
        return Result.success(it)
      }
      return Result.failure(jsonError)
    } catch (error: Throwable) {
      rethrowCancellation(error) {
        errorState.update { jsonError }
        Logger.record(error, "BackupExportViewModel::validateExportData()")
      }
      return Result.failure(jsonError)
    }
  }

  fun clearState() {
    loadingState.update { false }
    exportContentState.update { null }
    errorState.update { null }
  }

  val uiState = combine(
    loadingState,
    exportContentState,
    errorState,
  ) { s1, s2, s3 ->
    BackupExportUiState(
      isLoading = s1,
      exportContent = s2,
      error = s3,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = BackupExportUiState(),
  )
}
