package com.michaldrabik.ui_backup.features.export

import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.common.extensions.toLocalZone
import java.time.format.DateTimeFormatter

object BackupFileName {

  fun create(): String {
    val dateFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    val currentDate = nowUtc().toLocalZone()
    return "showly_export_${dateFormat.format(currentDate)}.json"
  }

}
