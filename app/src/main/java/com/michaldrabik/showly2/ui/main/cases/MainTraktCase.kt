package com.michaldrabik.showly2.ui.main.cases

import android.content.SharedPreferences
import androidx.core.net.toUri
import androidx.work.WorkManager
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_backup.features.export.model.BackupExportSchedule
import com.michaldrabik.ui_backup.features.export.workers.BackupExportScheduleWorker
import com.michaldrabik.ui_base.trakt.TraktSyncWorker
import com.michaldrabik.ui_base.trakt.quicksync.QuickSyncManager
import com.michaldrabik.ui_base.trakt.quicksync.QuickSyncWorker
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import javax.inject.Named
import androidx.core.content.edit

@ViewModelScoped
class MainTraktCase @Inject constructor(
  @Named("miscPreferences") private var miscPreferences: SharedPreferences,
  private val settingsRepository: SettingsRepository,
  private val quickSyncManager: QuickSyncManager,
  private val workManager: WorkManager,
) {

  suspend fun refreshTraktSyncSchedule() {
    if (!settingsRepository.isInitialized()) return
    val schedule = settingsRepository.load().traktSyncSchedule
    TraktSyncWorker.schedulePeriodic(workManager, schedule, cancelExisting = false)
  }

  suspend fun refreshTraktQuickSync() {
    if (quickSyncManager.isAnyScheduled()) {
      QuickSyncWorker.schedule(workManager)
    }
  }

  /**
   * Get latest stored information about backup export schedule and reschedule to make sure the schedule is still
   * running properly.
   */
  fun refreshBackupExportSchedule() {
    val schedule = BackupExportSchedule.createFromName(
      miscPreferences.getString(BackupExportScheduleWorker.KEY_BACKUP_EXPORT_SCHEDULE, null)
    )
    val uri = miscPreferences.getString(BackupExportScheduleWorker.KEY_BACKUP_EXPORT_DIRECTORY_URI, null)?.toUri()
    if (uri != null) {
      BackupExportScheduleWorker.schedulePeriodic(workManager, uri, schedule)
    } else {
      miscPreferences.edit {
        putString(
          BackupExportScheduleWorker.KEY_BACKUP_EXPORT_SCHEDULE,
          BackupExportSchedule.DEFAULT_OFF.name
        )
      }
      BackupExportScheduleWorker.cancelAllPeriodic(workManager)
    }
  }
}
