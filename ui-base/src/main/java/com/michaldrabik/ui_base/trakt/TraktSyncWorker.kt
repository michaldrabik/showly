package com.michaldrabik.ui_base.trakt

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.michaldrabik.common.errors.ErrorHelper
import com.michaldrabik.common.errors.ShowlyError
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.repository.UserTraktManager
import com.michaldrabik.ui_base.Logger
import com.michaldrabik.ui_base.R
import com.michaldrabik.ui_base.events.EventsManager
import com.michaldrabik.ui_base.events.TraktSyncAuthError
import com.michaldrabik.ui_base.events.TraktSyncError
import com.michaldrabik.ui_base.events.TraktSyncProgress
import com.michaldrabik.ui_base.events.TraktSyncStart
import com.michaldrabik.ui_base.events.TraktSyncSuccess
import com.michaldrabik.ui_base.trakt.exports.TraktExportListsRunner
import com.michaldrabik.ui_base.trakt.exports.TraktExportRatingsRunner
import com.michaldrabik.ui_base.trakt.exports.TraktExportWatchedRunner
import com.michaldrabik.ui_base.trakt.exports.TraktExportWatchlistRunner
import com.michaldrabik.ui_base.trakt.imports.TraktImportListsRunner
import com.michaldrabik.ui_base.trakt.imports.TraktImportRatingsRunner
import com.michaldrabik.ui_base.trakt.imports.TraktImportWatchedRunner
import com.michaldrabik.ui_base.trakt.imports.TraktImportWatchlistRunner
import com.michaldrabik.ui_base.trakt.receivers.ListLimitNotificationReceiver
import com.michaldrabik.ui_base.trakt.receivers.ListLimitNotificationReceiver.Key.CUSTOM_LIST_NOTIFICATION_SNOOZED_AT
import com.michaldrabik.ui_base.trakt.receivers.WatchlistLimitNotificationReceiver
import com.michaldrabik.ui_base.trakt.receivers.WatchlistLimitNotificationReceiver.Key.WATCHLIST_NOTIFICATION_SNOOZED_AT
import com.michaldrabik.ui_base.utilities.extensions.notificationManager
import com.michaldrabik.ui_model.TraktSyncSchedule
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import javax.inject.Named
import kotlin.time.Duration.Companion.days

@SuppressLint("MissingPermission")
@HiltWorker
class TraktSyncWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted workerParams: WorkerParameters,
  private val importWatchedRunner: TraktImportWatchedRunner,
  private val importWatchlistRunner: TraktImportWatchlistRunner,
  private val importListsRunner: TraktImportListsRunner,
  private val importRatingsRunner: TraktImportRatingsRunner,
  private val exportWatchedRunner: TraktExportWatchedRunner,
  private val exportWatchlistRunner: TraktExportWatchlistRunner,
  private val exportListsRunner: TraktExportListsRunner,
  private val exportRatingsRunner: TraktExportRatingsRunner,
  private val eventsManager: EventsManager,
  private val userManager: UserTraktManager,
  @Named("syncPreferences") private val syncPreferences: SharedPreferences,
  @Named("miscPreferences") private val miscPreferences: SharedPreferences,
) : TraktNotificationWorker(context, workerParams) {

  companion object {
    const val TAG_ID = "TRAKT_SYNC_WORK_ID"
    private const val TAG = "TRAKT_SYNC_WORK"
    private const val TAG_ONE_OFF = "TRAKT_SYNC_WORK_ONE_OFF"

    private const val SYNC_NOTIFICATION_COMPLETE_SUCCESS_ID = 827
    private const val SYNC_NOTIFICATION_COMPLETE_PROGRESS_ID = 823
    private const val SYNC_NOTIFICATION_COMPLETE_ERROR_ID = 828
    const val SYNC_NOTIFICATION_COMPLETE_ERROR_LISTS_ID = 832
    const val SYNC_NOTIFICATION_COMPLETE_ERROR_WATCHLIST_ID = 833

    const val KEY_LAST_SYNC_TIMESTAMP = "KEY_LAST_SYNC_TIMESTAMP"
    private const val ARG_IS_IMPORT = "ARG_IS_IMPORT"
    private const val ARG_IS_EXPORT = "ARG_IS_EXPORT"
    private const val ARG_IS_SILENT = "ARG_IS_SILENT"

    const val TRAKT_LISTS_INFO_URL =
      "https://releasenotes.trakt.tv/release/Y2LCE-january-21-2025"

    fun scheduleOneOff(
      workManager: WorkManager,
      isImport: Boolean,
      isExport: Boolean,
      isSilent: Boolean,
    ) {
      val inputData = workDataOf(
        ARG_IS_IMPORT to isImport,
        ARG_IS_EXPORT to isExport,
        ARG_IS_SILENT to isSilent,
      )

      val request = OneTimeWorkRequestBuilder<TraktSyncWorker>()
        .setConstraints(
          Constraints
            .Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(false)
            .setRequiresStorageNotLow(false)
            .build(),
        ).setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setInputData(inputData)
        .addTag(TAG_ID)
        .addTag(TAG_ONE_OFF)
        .build()

      workManager.enqueueUniqueWork(TAG_ONE_OFF, ExistingWorkPolicy.KEEP, request)
    }

    fun schedulePeriodic(
      workManager: WorkManager,
      schedule: TraktSyncSchedule,
      cancelExisting: Boolean,
    ) {
      if (cancelExisting) {
        workManager.cancelUniqueWork(TAG)
      }

      if (schedule == TraktSyncSchedule.OFF) {
        cancelAllPeriodic(workManager)
        Timber.i("Trakt sync scheduled: $schedule")
        return
      }

      val inputData = workDataOf(
        ARG_IS_IMPORT to true,
        ARG_IS_EXPORT to true,
        ARG_IS_SILENT to true,
      )

      val request = PeriodicWorkRequestBuilder<TraktSyncWorker>(schedule.duration, schedule.durationUnit)
        .setConstraints(
          Constraints
            .Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build(),
        ).setInputData(inputData)
        .setInitialDelay(schedule.duration, schedule.durationUnit)
        .addTag(TAG_ID)
        .addTag(TAG)
        .build()

      workManager.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.KEEP, request)
      Timber.i("Trakt sync scheduled: $schedule")
    }

    fun cancelAllPeriodic(workManager: WorkManager) {
      workManager.cancelUniqueWork(TAG)
    }
  }

  override suspend fun doWork(): Result {
    val isImport = inputData.getBoolean(ARG_IS_IMPORT, false)
    val isExport = inputData.getBoolean(ARG_IS_EXPORT, false)
    val isSilent = inputData.getBoolean(ARG_IS_SILENT, false)

    try {
      eventsManager.sendEvent(TraktSyncStart)

      if (isImport) {
        runImportWatched()
        runImportWatchlist()
        runImportLists()
        runImportRatings()
      }
      if (isExport) {
        runExportWatched()
        runExportWatchlist()
        runExportLists()
        runExportRatings()
      }

      miscPreferences.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, nowUtcMillis()).apply()

      eventsManager.sendEvent(TraktSyncSuccess)
      if (!isSilent) {
        notificationManager().notify(
          SYNC_NOTIFICATION_COMPLETE_SUCCESS_ID,
          createSuccessNotification(),
        )
      }
      return Result.success()
    } catch (error: Throwable) {
      handleError(error, isSilent)
      return Result.failure()
    } finally {
      clearRunners()
      notificationManager().cancel(SYNC_NOTIFICATION_COMPLETE_PROGRESS_ID)
    }
  }

  override suspend fun getForegroundInfo(): ForegroundInfo {
    val notification = createProgressNotification(null)
    return ForegroundInfo(SYNC_NOTIFICATION_COMPLETE_PROGRESS_ID, notification)
  }

  private suspend fun runImportWatched() {
    importWatchedRunner.progressListener = { title: String ->
      setProgressNotification("Importing progress...")
      eventsManager.sendEvent(TraktSyncProgress("Importing:\n\n\"$title\"..."))
    }
    importWatchedRunner.run()
  }

  private suspend fun runImportWatchlist() {
    importWatchlistRunner.progressListener = { title: String ->
      setProgressNotification("Importing watchlist...")
      eventsManager.sendEvent(TraktSyncProgress("Importing:\n\n\"$title\"..."))
    }
    importWatchlistRunner.run()
  }

  private suspend fun runImportLists() {
    importListsRunner.progressListener = { title: String ->
      setProgressNotification("Importing custom lists...")
      eventsManager.sendEvent(TraktSyncProgress("Importing:\n\n\"$title\"..."))
    }
    importListsRunner.run()
  }

  private suspend fun runImportRatings() {
    importRatingsRunner.progressListener = {
      setProgressNotification("Importing ratings...")
      eventsManager.sendEvent(TraktSyncProgress("Importing ratings..."))
    }
    importRatingsRunner.run()
  }

  private suspend fun runExportWatched() {
    val status = "Exporting progress..."
    setProgressNotification(status)
    eventsManager.sendEvent(TraktSyncProgress(status))
    exportWatchedRunner.run()
  }

  private suspend fun runExportWatchlist() {
    val status = "Exporting watchlist..."
    setProgressNotification(status)
    eventsManager.sendEvent(TraktSyncProgress(status))
    try {
      exportWatchlistRunner.run()
    } catch (error: Throwable) {
      handleWatchlistError(error)
    }
  }

  private suspend fun runExportLists() {
    val status = "Exporting custom lists..."
    setProgressNotification(status)
    eventsManager.sendEvent(TraktSyncProgress(status))
    try {
      exportListsRunner.run()
    } catch (error: Throwable) {
      handleListsError(error)
    }
  }

  private suspend fun runExportRatings() {
    val status = "Exporting ratings..."
    setProgressNotification(status)
    eventsManager.sendEvent(TraktSyncProgress(status))
    try {
      exportRatingsRunner.run()
    } catch (error: Throwable) {
      handleListsError(error)
    }
  }

  private fun setProgressNotification(content: String?) {
    notificationManager().notify(
      SYNC_NOTIFICATION_COMPLETE_PROGRESS_ID,
      createProgressNotification(content),
    )
  }

  private suspend fun handleError(
    error: Throwable,
    isSilent: Boolean,
  ) {
    val showlyError = ErrorHelper.parse(error)
    if (showlyError is ShowlyError.UnauthorizedError) {
      eventsManager.sendEvent(TraktSyncAuthError)
      userManager.revokeToken()
    } else {
      eventsManager.sendEvent(TraktSyncError)
    }
    if (!isSilent) {
      val message =
        if (showlyError is ShowlyError.UnauthorizedError) {
          R.string.errorTraktAuthorization
        } else {
          R.string.textTraktSyncErrorFull
        }

      notificationManager().notify(
        SYNC_NOTIFICATION_COMPLETE_ERROR_ID,
        createErrorNotification(R.string.textTraktSyncError, message, null),
      )
    }
    Logger.record(error, "TraktSyncWorker::handleError()")
  }

  private fun handleListsError(error: Throwable) {
    when (ErrorHelper.parse(error)) {
      ShowlyError.AccountLimitsError -> {
        val snoozedAt = syncPreferences.getLong(CUSTOM_LIST_NOTIFICATION_SNOOZED_AT, 0)
        if (snoozedAt > 0 && nowUtcMillis() - snoozedAt < 30.days.inWholeMilliseconds) {
          Timber.d("Custom lists limit notification snoozed")
          return
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TRAKT_LISTS_INFO_URL))
        val intent2 = Intent(context, ListLimitNotificationReceiver::class.java)

        val pendingIntent = PendingIntent.getActivity(context, 0, intent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)
        val pendingIntent2 = PendingIntent.getBroadcast(context, 1, intent2, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)

        val action = NotificationCompat.Action(R.drawable.ic_info, "More info", pendingIntent)
        val action2 = NotificationCompat.Action(R.drawable.ic_info, "Snooze for 30 days", pendingIntent2)

        notificationManager().notify(
          SYNC_NOTIFICATION_COMPLETE_ERROR_LISTS_ID,
          createErrorNotification(
            titleTextRes = R.string.textTraktSync,
            bigTextRes = R.string.errorTraktSyncListsLimitsReached,
            actions = listOf(action, action2),
          ),
        )
      }

      else -> throw error
    }
  }

  private fun handleWatchlistError(error: Throwable) {
    when (ErrorHelper.parse(error)) {
      ShowlyError.AccountLimitsError -> {
        val snoozedAt = syncPreferences.getLong(WATCHLIST_NOTIFICATION_SNOOZED_AT, 0)
        if (snoozedAt > 0 && nowUtcMillis() - snoozedAt < 30.days.inWholeMilliseconds) {
          Timber.d("Watchlist limit notification snoozed")
          return
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TRAKT_LISTS_INFO_URL))
        val intent2 = Intent(context, WatchlistLimitNotificationReceiver::class.java)

        val pendingIntent = PendingIntent.getActivity(context, 0, intent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)
        val pendingIntent2 = PendingIntent.getBroadcast(context, 1, intent2, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)

        val action = NotificationCompat.Action(R.drawable.ic_info, "More info", pendingIntent)
        val action2 = NotificationCompat.Action(R.drawable.ic_info, "Snooze for 30 days", pendingIntent2)

        notificationManager().notify(
          SYNC_NOTIFICATION_COMPLETE_ERROR_WATCHLIST_ID,
          createErrorNotification(
            titleTextRes = R.string.textTraktSync,
            bigTextRes = R.string.errorTraktSyncWatchlistLimitsReached,
            actions = listOf(action, action2),
          ),
        )
      }

      else -> throw error
    }
  }

  private fun clearRunners() {
    arrayOf(
      importWatchedRunner,
      importWatchlistRunner,
      importListsRunner,
      importRatingsRunner,
      exportWatchedRunner,
      exportWatchlistRunner,
      exportListsRunner,
      exportRatingsRunner,
    ).forEach {
      it.progressListener = null
    }
  }
}
