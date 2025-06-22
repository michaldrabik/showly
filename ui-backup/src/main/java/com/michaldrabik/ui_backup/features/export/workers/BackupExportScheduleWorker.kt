package com.michaldrabik.ui_backup.features.export.workers

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.michaldrabik.common.extensions.nowUtcMillis
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.michaldrabik.ui_backup.features.export.cases.CreateBackupJsonUseCase
import com.michaldrabik.ui_backup.features.export.cases.ReadBackupJsonFromFileUseCase
import com.michaldrabik.ui_backup.features.export.cases.CreateBackupSchemeFromJsonUseCase
import com.michaldrabik.ui_backup.features.export.cases.WriteBackupJsonToFileUseCase
import com.michaldrabik.ui_backup.features.export.model.BackupExportSchedule
import com.michaldrabik.ui_base.Logger
import timber.log.Timber
import javax.inject.Named
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.work.Data
import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.common.extensions.toLocalZone
import com.michaldrabik.ui_backup.features.export.BackupFileName
import java.time.format.DateTimeFormatter

/**
 * Worker that creates a backup JSON file automatically on behalf of the user.
 */
@HiltWorker
class BackupExportScheduleWorker @AssistedInject constructor(
  @Assisted private val appContext: Context,
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

    /**
     * Schedules a periodic backup export.
     * This will cancel any previously scheduled periodic work with the same tag.
     * If the provided [schedule] is [BackupExportSchedule.OFF], no new work will be scheduled.
     *
     * @param workManager The [WorkManager] instance to use for scheduling.
     * @param directoryUri The [Uri] of the directory where backups should be saved.
     * @param schedule The [BackupExportSchedule] defining the frequency of backups.
     */
    fun schedulePeriodic(workManager: WorkManager, directoryUri: Uri, schedule: BackupExportSchedule) {
      cancelAllPeriodic(workManager)

      if (schedule == BackupExportSchedule.OFF) {
        Timber.i("Backup export scheduled: $schedule")
        return
      }

      val data = Data.Builder()
        .putString(ARG_DIRECTORY_URI, directoryUri.toString())
        .build()

      val request = PeriodicWorkRequestBuilder<BackupExportScheduleWorker>(schedule.duration, schedule.durationUnit)
        .setInputData(data)
//        .setInitialDelay(schedule.duration, schedule.durationUnit) // TODO Backup: Add back when ready to make a pull request
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
   *
   * This involves generating a filename, creating a file, writing backup JSON,
   * verifying the JSON, and updating the last backup timestamp on success.
   *
   * @return [Result.success] if the backup export was successful, [Result.failure] otherwise.
   */
  override suspend fun doWork(): Result {
    Timber.i("Exporting automatic backup")

    val onFailure = { error: Throwable ->
      Timber.w("Exporting automatic backup failed")
      error.log()
      Result.failure()
    }

    val fileName = BackupFileName.create()

    return try {
      val directoryUriString = inputData.getString(ARG_DIRECTORY_URI)
      if (directoryUriString == null) {
        Timber.e("Directory URI is null")
        return Result.failure()
      }
      val fileUri = createFile(appContext, directoryUriString.toUri(), fileName, "application/json")
      if (fileUri == null) {
        Timber.e("File URI is null")
        return Result.failure()
      }

      val json = createBackupJsonUseCase()
      writeBackupJsonToFileUseCase(appContext, fileUri, json).fold(
        onFailure = onFailure,
        onSuccess = {
          readBackupJsonFromFileUseCase(appContext, fileUri).fold(
            onFailure = onFailure,
            onSuccess = { json ->
              // Validate that the JSON was saved correctly
              createBackupSchemeFromJsonUseCase(json).fold(
                onFailure = onFailure,
                onSuccess = {
                  miscPreferences.edit { putLong(KEY_LAST_LAST_BACKUP_EXPORT_TIMESTAMP, nowUtcMillis()) }
                  Timber.i("Exporting automatic backup successful")
                  Result.success()
                }
              )
            }
          )
        },
      )
    } catch (exception: Exception) {
      onFailure.invoke(exception)
    }
  }

  /**
   * Creates a new file in the specified directory.
   *
   * @param context The application context.
   * @param directoryUri The URI of the directory where the file should be created.
   * @param fileName The name of the file to be created.
   * @param mimeType The MIME type of the file.
   * @return The URI of the newly created file, or null if creation failed.
   */
  private fun createFile(context: Context, directoryUri: Uri, fileName: String, mimeType: String): Uri? {
    // Get the document ID from the tree URI
    val treeDocumentId = DocumentsContract.getTreeDocumentId(directoryUri)

    // Build a URI representing the directory's contents
    val childDocumentsUri = DocumentsContract.buildChildDocumentsUriUsingTree(
      directoryUri,
      treeDocumentId
    )

    // Create the new file
    return DocumentsContract.createDocument(
      context.contentResolver,
      childDocumentsUri, // Note: some older examples use directoryUri here, but childDocumentsUri is more correct
      mimeType,
      fileName
    )
  }

  /**
   * Logs the error to Logger.
   */
  private fun Throwable.log() {
    Logger.record(this, "ExportBackupScheduleWorker::doWork()")
  }
}
