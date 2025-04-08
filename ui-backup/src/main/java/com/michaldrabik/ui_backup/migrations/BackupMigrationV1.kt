package com.michaldrabik.ui_backup.migrations

import com.michaldrabik.common.extensions.dateIsoStringFromMillis
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.ui_backup.BackupConfig.SCHEME_PLATFORM
import com.michaldrabik.ui_backup.BackupConfig.SCHEME_VERSION
import com.michaldrabik.ui_backup.model.BackupEpisode
import com.michaldrabik.ui_backup.model.BackupList
import com.michaldrabik.ui_backup.model.BackupListItem
import com.michaldrabik.ui_backup.model.BackupLists
import com.michaldrabik.ui_backup.model.BackupMovie
import com.michaldrabik.ui_backup.model.BackupMovies
import com.michaldrabik.ui_backup.model.BackupScheme
import com.michaldrabik.ui_backup.model.BackupSeason
import com.michaldrabik.ui_backup.model.BackupShow
import com.michaldrabik.ui_backup.model.BackupShows
import com.michaldrabik.ui_backup.model.v1.BackupScheme1

internal object BackupMigrationV1 {

  fun migrate(scheme: BackupScheme1): BackupScheme =
    BackupScheme(
      version = SCHEME_VERSION,
      platform = SCHEME_PLATFORM,
      createdAt = dateIsoStringFromMillis(nowUtcMillis()),
      shows = BackupShows(
        collectionHistory = scheme.shows.collectionHistory.map {
          BackupShow(
            traktId = it.traktId,
            tmdbId = it.tmdbId,
            title = it.title,
            addedAt = it.addedAt,
            updatedAt = it.updatedAt,
          )
        },
        collectionWatchlist = scheme.shows.collectionWatchlist.map {
          BackupShow(
            traktId = it.traktId,
            tmdbId = it.tmdbId,
            title = it.title,
            addedAt = it.addedAt,
            updatedAt = it.updatedAt,
          )
        },
        collectionHidden = scheme.shows.collectionHidden.map {
          BackupShow(
            traktId = it.traktId,
            tmdbId = it.tmdbId,
            title = it.title,
            addedAt = it.addedAt,
            updatedAt = it.updatedAt,
          )
        },
        progressEpisodes = scheme.shows.progressEpisodes.map {
          BackupEpisode(
            traktId = it.traktId,
            showTraktId = it.showTraktId,
            showTmdbId = -1,
            episodeNumber = it.episodeNumber,
            seasonNumber = it.seasonNumber,
            addedAt = it.addedAt,
          )
        },
        progressSeasons = scheme.shows.progressSeasons.map {
          BackupSeason(
            traktId = it.traktId,
            showTraktId = it.showTraktId,
            showTmdbId = -1,
            seasonNumber = it.seasonNumber,
          )
        },
        progressPinned = scheme.shows.progressPinned,
        progressOnHold = scheme.shows.progressOnHold,
        ratingsShows = emptyList(),
        ratingsSeasons = emptyList(),
        ratingsEpisodes = emptyList(),
      ),
      movies = BackupMovies(
        collectionHistory = scheme.movies.collectionHistory.map {
          BackupMovie(
            traktId = it.traktId,
            tmdbId = it.tmdbId,
            title = it.title,
            addedAt = it.addedAt,
          )
        },
        collectionWatchlist = scheme.movies.collectionWatchlist.map {
          BackupMovie(
            traktId = it.traktId,
            tmdbId = it.tmdbId,
            title = it.title,
            addedAt = it.addedAt,
          )
        },
        collectionHidden = scheme.movies.collectionHidden.map {
          BackupMovie(
            traktId = it.traktId,
            tmdbId = it.tmdbId,
            title = it.title,
            addedAt = it.addedAt,
          )
        },
        progressPinned = scheme.movies.progressPinned,
      ),
      lists = BackupLists(
        lists = scheme.lists.lists.map {
          BackupList(
            id = it.id,
            traktId = it.traktId,
            slugId = it.slugId,
            name = it.name,
            description = it.description,
            privacy = it.privacy,
            itemCount = it.itemCount,
            createdAt = it.createdAt,
            updatedAt = it.updatedAt,
            items = it.items.map { item ->
              BackupListItem(
                id = item.id,
                listId = item.listId,
                traktId = item.traktId,
                tmdbId = -1,
                type = item.type,
                rank = item.rank,
                listedAt = item.listedAt,
                createdAt = item.createdAt,
                updatedAt = item.updatedAt,
              )
            },
          )
        },
      ),
    )
}
