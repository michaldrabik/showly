package com.michaldrabik.ui_backup.features.export.cases

import android.content.Context
import android.net.Uri
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * Reads a backup JSON from a file.
 */
class ReadBackupJsonFromFileUseCase @Inject constructor() {

  /**
   * Reads a backup JSON from a file.
   *
   * @param context The application context.
   * @param uri The URI of the file to read from.
   * @return A [Result] containing the JSON string on success or an error message on failure.
   */
  operator fun invoke(
    context: Context,
    uri: Uri,
  ): Result<String> {
    Timber.i("Reading backup JSON from file - uri: $uri")
    return try {
      val inputStream = context.contentResolver.openInputStream(uri)
        ?: return Result.failure(IOException("Failed to open input stream"))
      // Using .use will automatically close the stream after the block is executed or an exception is thrown
      val fileContent = inputStream.bufferedReader().use { bufferedReader -> bufferedReader.readText() }
      Result.success(fileContent)
    } catch (exception: Throwable) {
      Result.failure(exception)
    }
  }
}
