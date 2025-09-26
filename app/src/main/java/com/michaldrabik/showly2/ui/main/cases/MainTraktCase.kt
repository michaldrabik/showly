package com.michaldrabik.showly2.ui.main.cases

import androidx.work.WorkManager
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_base.trakt.TraktSyncWorker
import com.michaldrabik.ui_base.trakt.quicksync.QuickSyncManager
import com.michaldrabik.ui_base.trakt.quicksync.QuickSyncWorker
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject

@ViewModelScoped
class MainTraktCase @Inject constructor(
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
}
