package com.michaldrabik.ui_settings.sections.trakt.cases

import android.net.Uri
import androidx.work.WorkManager
import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.data_local.sources.TraktSyncLogLocalDataSource
import com.michaldrabik.repository.RatingsRepository
import com.michaldrabik.repository.UserTraktManager
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_base.trakt.TraktSyncWorker
import com.michaldrabik.ui_model.Settings
import com.michaldrabik.ui_model.TraktSyncSchedule
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
class SettingsTraktCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val settingsRepository: SettingsRepository,
  private val ratingsRepository: RatingsRepository,
  private val syncLogLocalSource: TraktSyncLogLocalDataSource,
  private val userManager: UserTraktManager,
  private val workManager: WorkManager,
) {

  suspend fun enableTraktQuickSync(enable: Boolean) {
    val settings = settingsRepository.load()
    settings.let {
      val new = it.copy(traktQuickSyncEnabled = enable)
      settingsRepository.update(new)
    }
  }

  suspend fun enableTraktQuickRemove(enable: Boolean) {
    val settings = settingsRepository.load()
    settings.let {
      val new = it.copy(traktQuickRemoveEnabled = enable)
      settingsRepository.update(new)
    }
  }

  suspend fun setTraktSyncSchedule(schedule: TraktSyncSchedule) {
    val settings = settingsRepository.load()
    settings.let {
      val new = it.copy(traktSyncSchedule = schedule)
      settingsRepository.update(new)
    }
    TraktSyncWorker.schedulePeriodic(workManager, schedule, cancelExisting = true)
  }

  suspend fun authorizeTrakt(authData: Uri) {
    val code = authData.getQueryParameter("code")
    if (code.isNullOrBlank()) {
      throw IllegalStateException("Invalid Trakt authorization code.")
    }
    withContext(dispatchers.IO) {
      userManager.authorize(code)
    }
  }

  suspend fun logoutTrakt() {
    suspend fun disableTraktFeatures() {
      val settings = settingsRepository.load()
      settings.let {
        val defaults = Settings.createInitial()
        val new = it.copy(
          traktQuickSyncEnabled = defaults.traktQuickSyncEnabled,
          traktQuickRemoveEnabled = defaults.traktQuickRemoveEnabled,
          traktQuickRateEnabled = defaults.traktQuickRateEnabled,
        )
        settingsRepository.update(new)
      }
      setTraktSyncSchedule(TraktSyncSchedule.OFF)
    }

    userManager.revokeToken()
    syncLogLocalSource.deleteAll()
    disableTraktFeatures()
    TraktSyncWorker.cancelAllPeriodic(workManager)
  }

  fun isTraktAuthorized() = userManager.isAuthorized()

  suspend fun getTraktUsername() =
    withContext(dispatchers.IO) {
      userManager.getUsername()
    }
}
