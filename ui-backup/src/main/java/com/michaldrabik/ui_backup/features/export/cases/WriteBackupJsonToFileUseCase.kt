package com.michaldrabik.ui_backup.features.export.cases

import android.content.Context
import android.net.Uri
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * Writes a backup JSON to a file.
 */
class WriteBackupJsonToFileUseCase @Inject constructor() {

  /**
   * Writes a backup JSON to a file.
   *
   * @param context The application context.
   * @param uri The URI of the file to write to.
   * @param exportJson The JSON to write to the file.
   * @return A [Result] indicating the success or failure of the operation.
   */
  operator fun invoke(
    context: Context,
    uri: Uri,
    exportJson: String,
  ): Result<Boolean> {
    Timber.i("Writing backup JSON to file - uri: $uri")
    return try {
      val outputStream = context.contentResolver.openOutputStream(uri, "wt")
        ?: return Result.failure(IOException("Failed to open output stream."))
      // Using .use will automatically close the stream after the block is executed or an exception is thrown
      outputStream.use { stream -> stream.write(exportJson.trim().toByteArray()) }
      Result.success(true)
    } catch (e: Throwable) {
      Result.failure(e)
    }
  }
}
