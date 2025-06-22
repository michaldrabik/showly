package com.michaldrabik.ui_backup.features.export.cases

import com.michaldrabik.ui_backup.model.BackupScheme
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import timber.log.Timber
import javax.inject.Inject

/**
 * Creates a [BackupScheme] from a JSON string.
 *
 * TODO: Migration can be added to here and then this use case can be reused in [com.michaldrabik.ui_backup.features.import_.BackupImportViewModel]
 */
class CreateBackupSchemeFromJsonUseCase @Inject constructor() {

  /**
   * Creates a [BackupScheme] from a JSON string.
   *
   * @param json The JSON string to parse.
   * @return A [Result] containing the [BackupScheme] on success or an error message on failure.
   */
  operator fun invoke(json: String): Result<BackupScheme?> {
    Timber.i("Creating backup scheme from JSON")
    try {
      val moshi = Moshi
        .Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
      val jsonAdapter = moshi.adapter(BackupScheme::class.java)
      return Result.success(jsonAdapter.fromJson(json))
    } catch (exception: Exception) {
      return Result.failure(exception)
    }
  }
}
