package com.michaldrabik.ui_backup.features.export.model

import androidx.annotation.StringRes
import com.michaldrabik.ui_backup.R
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.DAYS
import java.util.concurrent.TimeUnit.HOURS
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Enum class representing the different automatic backup export intervals.
 */
enum class BackupExportSchedule(
  val duration: Long,
  val durationUnit: TimeUnit,
  @StringRes val stringRes: Int,
  @StringRes val confirmationStringRes: Int,
  @StringRes val buttonStringRes: Int,
) {

  OFF(
    0,
    SECONDS,
    R.string.textBackupExportOptionOff,
    R.string.textBackupExportOptionOffMessage,
    R.string.textBackupExportSchedule,
  ),
  EVERY_15_MINUTES( // TODO Backup: Remove when ready to make a pull request
    15,
    MINUTES,
    R.string.textBackupExportOption1Hour,
    R.string.textBackupExportOptionConfirmMessage,
    R.string.textBackupExportOptionHourButton,
  ),
  EVERY_HOUR(
    1,
    HOURS,
    R.string.textBackupExportOption1Hour,
    R.string.textBackupExportOptionConfirmMessage,
    R.string.textBackupExportOptionHourButton,
  ),
  EVERY_3_HOURS(
    3,
    HOURS,
    R.string.textBackupExportOption3Hours,
    R.string.textBackupExportOptionConfirmMessage,
    R.string.textBackupExportOption3HoursButton,
  ),
  EVERY_6_HOURS(
    6,
    HOURS,
    R.string.textBackupExportOption6Hours,
    R.string.textBackupExportOptionConfirmMessage,
    R.string.textBackupExportOption6HoursButton,
  ),
  EVERY_DAY(
    1,
    DAYS,
    R.string.textBackupExportOptionDaily,
    R.string.textBackupExportOptionConfirmMessage,
    R.string.textBackupExportOptionDailyButton,
  ),
  EVERY_3_DAYS(
    3,
    DAYS,
    R.string.textBackupExportOption3Day,
    R.string.textBackupExportOptionConfirmMessage,
    R.string.textBackupExportOption3DayButton,
  ),
  EVERY_WEEK(
    7,
    DAYS,
    R.string.textBackupExportOptionWeekly,
    R.string.textBackupExportOptionConfirmMessage,
    R.string.textBackupExportOptionWeeklyButton,
  ),
  ;

  companion object {
    val DEFAULT_OFF = OFF

    fun createFromName(name: String?): BackupExportSchedule {
      if (name != null) {
        return entries.find { it.name == name } ?: DEFAULT_OFF
      }
      return DEFAULT_OFF
    }
  }

}
