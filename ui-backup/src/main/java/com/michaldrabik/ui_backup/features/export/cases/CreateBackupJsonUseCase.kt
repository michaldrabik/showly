package com.michaldrabik.ui_backup.features.export.cases

import com.michaldrabik.ui_backup.features.export.workers.BackupExportWorker
import com.michaldrabik.ui_backup.model.BackupScheme
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject

/**
 * Creates a JSON backup string.
 */
class CreateBackupJsonUseCase @Inject constructor(
  private val backupExportWorker: BackupExportWorker
) {

  /**
   * Creates a JSON backup string.
   */
  suspend operator fun invoke(): String {
    val exportResult = backupExportWorker.run()
    return createExportJson(exportResult)
  }

  private fun createExportJson(exportContent: BackupScheme): String {
    val moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
    val jsonAdapter = moshi.adapter(BackupScheme::class.java)
    return jsonAdapter.toJson(exportContent)
  }
}
