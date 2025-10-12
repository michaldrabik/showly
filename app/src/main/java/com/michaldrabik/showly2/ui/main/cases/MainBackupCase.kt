package com.michaldrabik.showly2.ui.main.cases

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.work.WorkManager
import com.michaldrabik.ui_backup.features.export.model.BackupExportSchedule
import com.michaldrabik.ui_backup.features.export.workers.BackupExportScheduleWorker
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import javax.inject.Named

@ViewModelScoped
class MainBackupCase @Inject constructor(
  @Named("miscPreferences") private var miscPreferences: SharedPreferences,
  private val workManager: WorkManager,
) {

  /**
   * Get latest stored information about backup export schedule and reschedule to make sure the schedule is still
   * running properly.
   */
  fun refreshBackupExportSchedule() {
    val schedule = BackupExportSchedule.createFromName(
      miscPreferences.getString(BackupExportScheduleWorker.KEY_BACKUP_EXPORT_SCHEDULE, null),
    )
    val uri = miscPreferences.getString(BackupExportScheduleWorker.KEY_BACKUP_EXPORT_DIRECTORY_URI, null)?.toUri()
    if (uri != null) {
      BackupExportScheduleWorker.schedulePeriodic(
        workManager = workManager,
        directoryUri = uri,
        schedule = schedule,
        cancelExisting = false,
      )
    } else {
      miscPreferences.edit {
        putString(
          BackupExportScheduleWorker.KEY_BACKUP_EXPORT_SCHEDULE,
          BackupExportSchedule.DEFAULT_OFF.name,
        )
      }
      BackupExportScheduleWorker.cancelAllPeriodic(workManager)
    }
  }
}
