package com.michaldrabik.ui_backup.features.import_

import com.michaldrabik.ui_backup.features.import_.model.BackupImportStatus
import com.michaldrabik.ui_backup.features.import_.model.BackupImportStatus.Idle

data class BackupImportUiState(
  val isImporting: BackupImportStatus = Idle,
  val isSuccess: Boolean = false,
  val isError: Throwable? = null,
)
