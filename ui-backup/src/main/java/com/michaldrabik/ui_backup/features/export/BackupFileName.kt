package com.michaldrabik.ui_backup.features.export

import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.common.extensions.toLocalZone
import java.time.format.DateTimeFormatter

object BackupFileName {

  val prefix = "showly_export_"
  val fileType = ".json"
  val dateTimePattern = "yyyyMMddHHmmss"
  val memeType = "application/json"

  fun create(): String {
    val dateFormat = DateTimeFormatter.ofPattern(dateTimePattern)
    val currentDate = nowUtc().toLocalZone()
    return prefix + dateFormat.format(currentDate) + fileType
  }
}
