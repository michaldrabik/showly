package com.michaldrabik.ui_backup.di

import com.michaldrabik.ui_backup.features.export.workers.BackupExportWorker
import com.michaldrabik.ui_backup.features.export.workers.DefaultBackupExportWorker
import com.michaldrabik.ui_backup.features.import_.workers.BackupImportWorker
import com.michaldrabik.ui_backup.features.import_.workers.DefaultBackupImportWorker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BackupBindingModule {

  @Binds
  abstract fun bindExportWorker(worker: DefaultBackupExportWorker): BackupExportWorker

  @Binds
  abstract fun bindImportWorker(worker: DefaultBackupImportWorker): BackupImportWorker
}
