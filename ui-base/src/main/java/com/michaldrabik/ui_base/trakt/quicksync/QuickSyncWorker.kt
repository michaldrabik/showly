package com.michaldrabik.ui_base.trakt.quicksync

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
import androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.michaldrabik.common.errors.ErrorHelper
import com.michaldrabik.common.errors.ShowlyError.AccountLimitsError
import com.michaldrabik.common.errors.ShowlyError.CoroutineCancellation
import com.michaldrabik.common.errors.ShowlyError.UnauthorizedError
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.repository.UserTraktManager
import com.michaldrabik.ui_base.Logger
import com.michaldrabik.ui_base.R
import com.michaldrabik.ui_base.events.EventsManager
import com.michaldrabik.ui_base.events.TraktQuickSyncSuccess
import com.michaldrabik.ui_base.events.TraktSyncAuthError
import com.michaldrabik.ui_base.trakt.TraktNotificationWorker
import com.michaldrabik.ui_base.trakt.TraktSyncWorker.Companion.SYNC_NOTIFICATION_COMPLETE_ERROR_LISTS_ID
import com.michaldrabik.ui_base.trakt.TraktSyncWorker.Companion.TRAKT_LISTS_INFO_URL
import com.michaldrabik.ui_base.trakt.quicksync.runners.QuickSyncListsRunner
import com.michaldrabik.ui_base.trakt.quicksync.runners.QuickSyncRunner
import com.michaldrabik.ui_base.trakt.receivers.ListLimitNotificationReceiver
import com.michaldrabik.ui_base.trakt.receivers.ListLimitNotificationReceiver.Key.CUSTOM_LIST_NOTIFICATION_SNOOZED_AT
import com.michaldrabik.ui_base.utilities.extensions.notificationManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit.SECONDS
import javax.inject.Named
import kotlin.time.Duration.Companion.days

@SuppressLint("MissingPermission")
@HiltWorker
class QuickSyncWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted workerParams: WorkerParameters,
  private val quickSyncRunner: QuickSyncRunner,
  private val quickSyncListsRunner: QuickSyncListsRunner,
  private val userManager: UserTraktManager,
  private val eventsManager: EventsManager,
  @Named("syncPreferences") private val syncPreferences: SharedPreferences,
) : TraktNotificationWorker(context, workerParams) {

  companion object {
    private const val TAG = "TRAKT_QUICK_SYNC_WORK"
    private const val SYNC_NOTIFICATION_PROGRESS_ID = 916
    private const val SYNC_NOTIFICATION_ERROR_ID = 917

    fun schedule(workManager: WorkManager) {
      val request = OneTimeWorkRequestBuilder<QuickSyncWorker>()
        .setConstraints(
          Constraints
            .Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build(),
        ).setInitialDelay(3, SECONDS)
        .addTag(TAG)
        .build()

      workManager.enqueueUniqueWork(TAG, APPEND_OR_REPLACE, request)
      Timber.i("Trakt QuickSync scheduled.")
    }
  }

  override suspend fun doWork(): Result {
    Timber.d("Initialized.")
    notificationManager().notify(
      SYNC_NOTIFICATION_PROGRESS_ID,
      createProgressNotification(null),
    )

    try {
      var count = quickSyncRunner.run()
      count += quickSyncListsRunner.run()
      if (count > 0) {
        eventsManager.sendEvent(TraktQuickSyncSuccess(count))
      }
    } catch (error: Throwable) {
      handleError(error)
    } finally {
      clearRunners()
      notificationManager().cancel(SYNC_NOTIFICATION_PROGRESS_ID)
      Timber.d("Quick Sync completed.")
    }

    return Result.success()
  }

  private suspend fun handleError(error: Throwable) {
    val showlyError = ErrorHelper.parse(error)
    if (showlyError is CoroutineCancellation) {
      return
    }

    if (showlyError is AccountLimitsError) {
      handleListsError()
      return
    }

    if (showlyError is UnauthorizedError) {
      eventsManager.sendEvent(TraktSyncAuthError)
      userManager.revokeToken()
    }

    val notificationMessage = when (showlyError) {
      is UnauthorizedError -> R.string.errorTraktAuthorization
      else -> R.string.textTraktSyncErrorFull
    }
    applicationContext.notificationManager().notify(
      SYNC_NOTIFICATION_ERROR_ID,
      createErrorNotification(R.string.textTraktQuickSyncError, notificationMessage, null),
    )
    Logger.record(error, "QuickSyncWorker::handleError()")
  }

  private fun handleListsError() {
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

  private fun clearRunners() {
    arrayOf(
      quickSyncRunner,
      quickSyncListsRunner,
    ).forEach {
      it.progressListener = null
    }
  }
}
