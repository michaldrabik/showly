package com.michaldrabik.ui_backup.features.export.runners

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.common.extensions.dateIsoStringFromMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.ui_backup.model.BackupList
import com.michaldrabik.ui_backup.model.BackupListItem
import com.michaldrabik.ui_backup.model.BackupLists
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

internal class BackupExportListsRunner @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val localSource: LocalDataSource,
) : BackupExportRunner<BackupLists>() {

  override suspend fun run(): BackupLists {
    Timber.d("Initialized.")
    return runExport()
      .also {
        Timber.d("Success.")
      }
  }

  private suspend fun runExport(): BackupLists =
    withContext(dispatchers.IO) {
      val exportLists = mutableListOf<BackupList>()

      val localLists = localSource.customLists.getAll()
      localLists.forEach { list ->
        val localItems = localSource.customListsItems.getItemsById(list.id)

        val backupItems = localItems.map {
          BackupListItem(
            id = it.id,
            listId = list.id,
            traktId = it.idTrakt,
            type = it.type,
            rank = it.rank,
            listedAt = dateIsoStringFromMillis(it.listedAt),
            createdAt = dateIsoStringFromMillis(it.createdAt),
            updatedAt = dateIsoStringFromMillis(it.updatedAt),
          )
        }

        val backupList = BackupList(
          id = list.id,
          traktId = list.idTrakt,
          slugId = list.idSlug,
          name = list.name,
          description = list.description,
          privacy = list.privacy,
          itemCount = list.itemCount,
          createdAt = dateIsoStringFromMillis(list.createdAt),
          updatedAt = dateIsoStringFromMillis(list.updatedAt),
          items = backupItems,
        )

        exportLists.add(backupList)
      }

      BackupLists(
        lists = exportLists,
      )
    }
}