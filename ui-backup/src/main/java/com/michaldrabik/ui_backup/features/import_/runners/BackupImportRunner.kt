package com.michaldrabik.ui_backup.features.import_.runners

import com.michaldrabik.ui_backup.features.import_.model.BackupImportStatus

internal abstract class BackupImportRunner<T> {
  var statusListener: ((BackupImportStatus) -> Unit)? = null

  abstract suspend fun run(backup: T)
}
