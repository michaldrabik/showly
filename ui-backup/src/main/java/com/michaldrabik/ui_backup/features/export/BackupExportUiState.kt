package com.michaldrabik.ui_backup.features.export

import android.net.Uri

data class BackupExportUiState(
  val isLoading: Boolean = false,
  val exportContent: ExportContentState? = null,
  val error: Throwable? = null,
)

data class ExportContentState(
  val exportContent: String,
  val exportUri: Uri,
)
