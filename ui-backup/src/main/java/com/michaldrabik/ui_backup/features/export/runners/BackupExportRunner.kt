package com.michaldrabik.ui_backup.features.export.runners

internal abstract class BackupExportRunner<T> {
  abstract suspend fun run(): T
}
