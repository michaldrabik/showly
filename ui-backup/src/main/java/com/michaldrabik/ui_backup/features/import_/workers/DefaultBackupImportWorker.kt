package com.michaldrabik.ui_backup.features.import_.workers

import com.michaldrabik.ui_backup.features.import_.model.BackupImportStatus
import com.michaldrabik.ui_backup.features.import_.runners.BackupImportListsRunner
import com.michaldrabik.ui_backup.features.import_.runners.BackupImportMoviesRunner
import com.michaldrabik.ui_backup.features.import_.runners.BackupImportShowsRunner
import com.michaldrabik.ui_backup.model.BackupScheme
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultBackupImportWorker @Inject constructor(
  private val importShowsRunner: BackupImportShowsRunner,
  private val importMoviesRunner: BackupImportMoviesRunner,
  private val importListsRunner: BackupImportListsRunner,
) : BackupImportWorker {

  override var statusListener: ((BackupImportStatus) -> Unit)? = null
    set(value) {
      field = value
      importShowsRunner.statusListener = field
      importMoviesRunner.statusListener = field
      importListsRunner.statusListener = field
    }

  override suspend fun run(backup: BackupScheme) {
    coroutineScope {
      importShowsRunner.run(backup.shows)
      importMoviesRunner.run(backup.movies)
      importListsRunner.run(backup.lists)
    }
  }
}
