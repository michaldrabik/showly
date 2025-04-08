package com.michaldrabik.ui_backup.features.import_.runners

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.common.extensions.toMillis
import com.michaldrabik.common.extensions.toUtcDateTime
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.repository.ListsRepository
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.repository.movies.MoviesRepository
import com.michaldrabik.repository.shows.ShowsRepository
import com.michaldrabik.ui_backup.features.import_.model.BackupImportStatus.Importing
import com.michaldrabik.ui_backup.model.BackupList
import com.michaldrabik.ui_backup.model.BackupListItem
import com.michaldrabik.ui_backup.model.BackupLists
import com.michaldrabik.ui_model.CustomList
import com.michaldrabik.ui_model.IdTrakt
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

internal class BackupImportListsRunner @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val localSource: LocalDataSource,
  private val listsRepository: ListsRepository,
  private val showsRepository: ShowsRepository,
  private val moviesRepository: MoviesRepository,
  private val mappers: Mappers,
) : BackupImportRunner<BackupLists>() {

  override suspend fun run(backup: BackupLists) {
    Timber.d("Initialized.")
    runImport(backup)
      .also {
        Timber.d("Success.")
      }
  }

  private suspend fun runImport(backup: BackupLists) {
    withContext(dispatchers.IO) {
      val localLists = localSource.customLists.getAll()
      for (backupList in backup.lists) {
        statusListener?.invoke(Importing(backupList.name))

        val idCheck = localLists.any { it.id == backupList.id }
        val traktIdCheck = backupList.traktId != null && localLists.any { it.idTrakt == backupList.traktId }

        if (traktIdCheck || idCheck) {
          // Custom lists already exists locally
          importExistingCustomList(backupList)
        } else {
          // Custom list does not exist locally
          importNewCustomList(backupList)
        }
      }
    }
  }

  private suspend fun importNewCustomList(backupList: BackupList) {
    val list = CustomList.create().copy(
      idTrakt = backupList.traktId,
      idSlug = backupList.slugId,
      name = backupList.name,
      description = backupList.description,
    )
    val listDb = mappers.customList.toDatabase(list)
    val listId = localSource.customLists.insert(listOf(listDb)).firstOrNull() ?: return

    // Add items to the list
    backupList.items.forEach { item ->
      importDetails(item)
      listsRepository.addToList(
        listId = listId,
        itemTraktId = IdTrakt(item.traktId),
        itemType = item.type,
        listedAt = item.listedAt.toUtcDateTime()?.toMillis() ?: nowUtcMillis(),
        createdAt = item.createdAt.toUtcDateTime()?.toMillis() ?: nowUtcMillis(),
        updatedAt = item.updatedAt.toUtcDateTime()?.toMillis() ?: nowUtcMillis(),
      )
    }
  }

  private suspend fun importExistingCustomList(backupList: BackupList) {
    val localList = localSource.customLists.getById(backupList.id) ?: return
    val localListItems = listsRepository.loadListItemsForId(localList.id)

    for (backupItem in backupList.items) {
      val itemExists = localListItems.any { it.idTrakt == backupItem.traktId && it.type == backupItem.type }
      if (itemExists) {
        continue
      }

      importDetails(backupItem)

      listsRepository.addToList(
        listId = localList.id,
        itemTraktId = IdTrakt(backupItem.traktId),
        itemType = backupItem.type,
        listedAt = backupItem.listedAt.toUtcDateTime()?.toMillis() ?: nowUtcMillis(),
        createdAt = backupItem.createdAt.toUtcDateTime()?.toMillis() ?: nowUtcMillis(),
        updatedAt = backupItem.updatedAt.toUtcDateTime()?.toMillis() ?: nowUtcMillis(),
      )
    }
  }

  private suspend fun importDetails(backupItem: BackupListItem) {
    if (backupItem.type == "show") {
      showsRepository.detailsShow.load(IdTrakt(backupItem.traktId))
    } else if (backupItem.type == "movie") {
      moviesRepository.movieDetails.load(IdTrakt(backupItem.traktId))
    }
  }
}
