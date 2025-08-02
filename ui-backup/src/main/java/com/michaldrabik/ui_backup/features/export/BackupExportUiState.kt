package com.michaldrabik.ui_backup.features.export

import android.net.Uri
import com.michaldrabik.ui_backup.features.export.model.BackupExportSchedule
import java.time.format.DateTimeFormatter

data class BackupExportUiState(
  val isLoading: Boolean = false,
  val exportContent: ExportContentState? = null,
  val error: Throwable? = null,
  val backupExportSchedule: BackupExportSchedule = BackupExportSchedule.DEFAULT_OFF,
  val lastBackupExportTimestamp: Long = 0L,
  val dateFormat: DateTimeFormatter? = null,
)

data class ExportContentState(
  val exportContent: String,
  val exportUri: Uri,
)
