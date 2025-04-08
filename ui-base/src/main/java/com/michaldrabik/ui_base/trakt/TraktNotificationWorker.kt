package com.michaldrabik.ui_base.trakt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.michaldrabik.ui_base.R
import com.michaldrabik.ui_base.utilities.extensions.notificationManager
import java.util.concurrent.TimeUnit

abstract class TraktNotificationWorker(
  val context: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

  private fun createBaseNotification(): NotificationCompat.Builder =
    NotificationCompat
      .Builder(applicationContext, createNotificationChannel())
      .setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)
      .setContentTitle(context.getString(R.string.textTraktSync))
      .setSmallIcon(R.drawable.ic_notification)
      .setAutoCancel(true)
      .setColor(ContextCompat.getColor(applicationContext, R.color.colorNotificationDark))

  protected fun createProgressNotification(content: String?): Notification =
    createBaseNotification()
      .setContentText(content ?: context.getString(R.string.textTraktSyncRunning))
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setOngoing(true)
      .setAutoCancel(false)
      .setProgress(0, 0, true)
      .build()

  protected fun createSuccessNotification(): Notification =
    createBaseNotification()
      .setTimeoutAfter(TimeUnit.SECONDS.toMillis(3))
      .setContentText(context.getString(R.string.textTraktSyncComplete))
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .build()

  protected fun createErrorNotification(
    @StringRes titleTextRes: Int,
    @StringRes bigTextRes: Int,
    action: NotificationCompat.Action? = null,
  ): Notification =
    createBaseNotification()
      .setContentTitle(context.getString(titleTextRes))
      .setContentText(context.getString(bigTextRes))
      .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(bigTextRes)))
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .apply { action?.let { addAction(it) } }
      .build()

  protected fun createErrorNotification(
    @StringRes titleTextRes: Int,
    @StringRes bigTextRes: Int,
    actions: List<NotificationCompat.Action> = emptyList(),
  ): Notification =
    createBaseNotification()
      .setContentTitle(context.getString(titleTextRes))
      .setContentText(context.getString(bigTextRes))
      .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(bigTextRes)))
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .apply {
        actions.forEach { addAction(it) }
      }.build()

  private fun createNotificationChannel(): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val id = "Showly Trakt Sync Service"
      val name = "Showly Trakt Sync"
      val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW).apply {
        lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        setSound(null, null)
      }
      applicationContext.notificationManager().createNotificationChannel(channel)
      return id
    }
    return ""
  }
}
