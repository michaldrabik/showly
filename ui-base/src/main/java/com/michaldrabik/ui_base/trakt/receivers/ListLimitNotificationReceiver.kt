package com.michaldrabik.ui_base.trakt.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.ui_base.trakt.TraktSyncWorker.Companion.SYNC_NOTIFICATION_COMPLETE_ERROR_LISTS_ID
import timber.log.Timber

class ListLimitNotificationReceiver : BroadcastReceiver() {

  companion object Key {
    private const val PREFERENCES = "PREFERENCES_SYNC"
    const val CUSTOM_LIST_NOTIFICATION_SNOOZED_AT = "CUSTOM_LIST_NOTIFICATION_SNOOZED"
  }

  override fun onReceive(
    context: Context?,
    intent: Intent?,
  ) {
    if (context == null) return

    val sharedPreferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    sharedPreferences
      .edit()
      .putLong(CUSTOM_LIST_NOTIFICATION_SNOOZED_AT, nowUtcMillis())
      .apply()

    val notificationManager = NotificationManagerCompat.from(context)
    notificationManager.cancel(SYNC_NOTIFICATION_COMPLETE_ERROR_LISTS_ID) // Use the same ID as in `notify()`

    Timber.d("Custom list limit notification dismissed")
  }
}
