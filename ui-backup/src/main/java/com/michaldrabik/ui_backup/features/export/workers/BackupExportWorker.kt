package com.michaldrabik.ui_backup.features.export.workers

import com.michaldrabik.ui_backup.model.BackupScheme

interface BackupExportWorker {
  suspend fun run(): BackupScheme
}
