package com.michaldrabik.ui_backup.features.export.workers

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.ui_backup.features.export.BackupFileName
import com.michaldrabik.ui_backup.features.export.cases.CreateBackupJsonUseCase
import com.michaldrabik.ui_backup.features.export.cases.CreateBackupSchemeFromJsonUseCase
import com.michaldrabik.ui_backup.features.export.cases.ReadBackupJsonFromFileUseCase
import com.michaldrabik.ui_backup.features.export.cases.WriteBackupJsonToFileUseCase
import com.michaldrabik.ui_backup.features.export.model.BackupExportSchedule
import com.michaldrabik.ui_backup.features.export.workers.BackupExportScheduleWorker.Companion.MAX_BACKUPS
import com.michaldrabik.ui_base.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import javax.inject.Named

/**
 * Worker that creates a backup JSON file automatically on behalf of the user.
 */
@HiltWorker
class BackupExportScheduleWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted workerParams: WorkerParameters,
  private val createBackupJsonUseCase: CreateBackupJsonUseCase,
  private val writeBackupJsonToFileUseCase: WriteBackupJsonToFileUseCase,
  private val readBackupJsonFromFileUseCase: ReadBackupJsonFromFileUseCase,
  private val createBackupSchemeFromJsonUseCase: CreateBackupSchemeFromJsonUseCase,
  @Named("miscPreferences") private val miscPreferences: SharedPreferences,
) : CoroutineWorker(appContext, workerParams) {

  companion object {
    private const val TAG = "BACKUP_EXPORT_WORK"
    const val KEY_BACKUP_EXPORT_SCHEDULE = "KEY_BACKUP_EXPORT_SCHEDULE"
    const val KEY_BACKUP_EXPORT_DIRECTORY_URI = "KEY_BACKUP_EXPORT_DIRECTORY_URI"
    const val KEY_LAST_LAST_BACKUP_EXPORT_TIMESTAMP = "KEY_LAST_LAST_BACKUP_EXPORT_TIMESTAMP"
    private const val ARG_DIRECTORY_URI = "ARG_DIRECTORY_URI"
    private const val MAX_BACKUPS = 5

    /**
     * Schedules a periodic backup export.
     * This will cancel any previously scheduled periodic work with the same tag.
     * If the provided [schedule] is [BackupExportSchedule.OFF], no new work will be scheduled.
     *
     * @param workManager The [WorkManager] instance to use for scheduling.
     * @param directoryUri The [Uri] of the directory where backups should be saved.
     * @param schedule The [BackupExportSchedule] defining the frequency of backups.
     */
    fun schedulePeriodic(
      workManager: WorkManager,
      directoryUri: Uri,
      schedule: BackupExportSchedule,
    ) {
      cancelAllPeriodic(workManager)

      if (schedule == BackupExportSchedule.OFF) {
        Timber.i("Backup export scheduled: $schedule")
        return
      }

      val data = Data
        .Builder()
        .putString(ARG_DIRECTORY_URI, directoryUri.toString())
        .build()

      val request = PeriodicWorkRequestBuilder<BackupExportScheduleWorker>(schedule.duration, schedule.durationUnit)
        .setInputData(data)
        .setInitialDelay(schedule.duration, schedule.durationUnit)
        .addTag(TAG)
        .build()

      workManager.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.KEEP, request)
      Timber.i("Backup export scheduled: $schedule")
    }

    /**
     * Cancel all scheduled work.
     */
    fun cancelAllPeriodic(workManager: WorkManager) {
      workManager.cancelUniqueWork(TAG)
    }
  }

  /**
   * Executes the backup export process.
   * This involves two main steps:
   * 1. Exporting a new backup. If this fails, the worker returns [Result.failure].
   * 2. Cleaning up old backups. If this step fails, the error is logged, but the worker still returns [Result.success]
   *    as creating a new backup is considered more critical than cleaning up old ones.
   *
   * @return [Result.success] if the new backup was successfully exported, otherwise [Result.failure].
   */
  override suspend fun doWork(): Result {
    Timber.i("Exporting automatic backup")

    // Export the backup first
    try {
      exportNewBackup()
      Timber.i("Exporting automatic backup successful")
    } catch (exception: Exception) {
      Timber.w(exception, "Exporting automatic backup failed")
      exception.log()
      return Result.failure()
    }

    // Clean up old backups second
    try {
      cleanupOldBackups()
      Timber.i("Cleaning up old backups successful")
    } catch (exception: Exception) {
      Timber.w("Cleaning up of old backups failed")
      exception.log()
    }

    // Returning success and not checking whether cleanup of old backups failed or not as creating a backup is more important then cleaning up old backups.
    return Result.success()
  }

  /**
   * Exports a new backup.
   *
   * Creates a backup JSON file in the specified directory, writes data to it,
   * verifies the written data, and updates the last export timestamp.
   *
   * @throws IllegalArgumentException if the directory or file URI is null.
   * @throws Exception if any other error occurs during backup.
   */
  private suspend fun exportNewBackup() {
    val directoryUri = inputData.getString(ARG_DIRECTORY_URI)?.toUri()
      ?: throw IllegalArgumentException("Directory URI is null")

    val treeDocumentId = DocumentsContract.getTreeDocumentId(directoryUri)
    val childDocumentsUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, treeDocumentId)
    val fileUri = DocumentsContract.createDocument(
      applicationContext.contentResolver,
      childDocumentsUri,
      BackupFileName.memeType,
      BackupFileName.create(),
    ) ?: throw IllegalArgumentException("File URI is null")

    val backupJson = createBackupJsonUseCase()

    writeBackupJsonToFileUseCase(applicationContext, fileUri, backupJson).fold(
      onFailure = { throw it },
      onSuccess = {
        readBackupJsonFromFileUseCase(applicationContext, fileUri).fold(
          onFailure = { throw it },
          onSuccess = { json ->
            // Validate that the JSON was saved correctly
            createBackupSchemeFromJsonUseCase(json).fold(
              onFailure = { throw it },
              onSuccess = {
                miscPreferences.edit { putLong(KEY_LAST_LAST_BACKUP_EXPORT_TIMESTAMP, nowUtcMillis()) }
              },
            )
          },
        )
      },
    )
  }

  /**
   * Deletes old backup files, keeping only the [MAX_BACKUPS] newest.
   * Backups are identified by prefix/type and sorted by their last modified timestamp.
   * Deletion errors are logged but don't halt the process.
   *
   * @throws IllegalArgumentException if directory URI is null.
   */
  private fun cleanupOldBackups() {
    val contentResolver = applicationContext.contentResolver

    val directoryUri = inputData.getString(ARG_DIRECTORY_URI)?.toUri()
      ?: throw IllegalArgumentException("Directory URI is null")

    val treeDocumentId = DocumentsContract.getTreeDocumentId(directoryUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, treeDocumentId)
    val projection = arrayOf(
      DocumentsContract.Document.COLUMN_DOCUMENT_ID, // Unique document ID
      DocumentsContract.Document.COLUMN_DISPLAY_NAME, // User viewable name
      DocumentsContract.Document.COLUMN_LAST_MODIFIED, // Last modified timestamp
    )
    val cursor = contentResolver.query(childrenUri, projection, null, null, null)

    val backupFiles = mutableListOf<Triple<Uri, String, Long>>()
    cursor?.use {
      while (it.moveToNext()) {
        val documentId = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
        val documentName = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
        val lastModified = it.getLong(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED))
        if (documentName.startsWith(BackupFileName.prefix) && documentName.endsWith(BackupFileName.fileType)) {
          val documentUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, documentId)
          backupFiles.add(Triple(documentUri, documentName, lastModified))
        }
      }
    }

    if (backupFiles.size > MAX_BACKUPS) {
      backupFiles.sortBy { it.third } // Sort by last modified timestamp (oldest first)
      val filesToDelete = backupFiles.take(backupFiles.size - MAX_BACKUPS)
      filesToDelete.forEach { (uri, name, _) ->
        try {
          if (!DocumentsContract.deleteDocument(contentResolver, uri)) {
            Timber.w("Failed to delete old backup: $name")
          }
        } catch (exception: Exception) {
          Timber.e(exception, "Error deleting old backup: $name")
          exception.log()
        }
      }
    }
  }

  /**
   * Logs the error to Logger.
   */
  private fun Throwable.log() {
    Logger.record(this, "ExportBackupScheduleWorker::doWork()")
  }
}
