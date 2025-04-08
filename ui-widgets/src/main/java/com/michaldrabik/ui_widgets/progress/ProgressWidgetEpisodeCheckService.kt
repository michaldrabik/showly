package com.michaldrabik.ui_widgets.progress

import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService
import com.michaldrabik.repository.EpisodesManager
import com.michaldrabik.ui_base.Logger
import com.michaldrabik.ui_base.common.WidgetsProvider
import com.michaldrabik.ui_base.trakt.quicksync.QuickSyncManager
import com.michaldrabik.ui_model.IdTrakt
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class ProgressWidgetEpisodeCheckService :
  JobIntentService(),
  CoroutineScope {

  companion object {
    private const val JOB_ID = 1001
    private const val EXTRA_EPISODE_ID = "EXTRA_EPISODE_ID"
    private const val EXTRA_SEASON_ID = "EXTRA_SEASON_ID"
    private const val EXTRA_SHOW_ID = "EXTRA_SHOW_ID"

    fun initialize(
      context: Context,
      episodeId: Long,
      seasonId: Long,
      showId: IdTrakt,
    ) {
      val intent = Intent().apply {
        putExtra(EXTRA_EPISODE_ID, episodeId)
        putExtra(EXTRA_SEASON_ID, seasonId)
        putExtra(EXTRA_SHOW_ID, showId.id)
      }
      enqueueWork(
        context,
        ProgressWidgetEpisodeCheckService::class.java,
        JOB_ID,
        intent,
      )
    }
  }

  override val coroutineContext = Job() + Dispatchers.Main

  @Inject lateinit var episodesManager: EpisodesManager
  @Inject lateinit var quickSyncManager: QuickSyncManager

  override fun onHandleWork(intent: Intent) {
    val episodeId = intent.getLongExtra(EXTRA_EPISODE_ID, -1)
    val seasonId = intent.getLongExtra(EXTRA_SEASON_ID, -1)
    val showId = intent.getLongExtra(EXTRA_SHOW_ID, -1)

    if (episodeId == -1L || seasonId == -1L || showId == -1L) {
      val error = Throwable("Invalid ID.")
      Logger.record(error, "ProgressWidgetEpisodeCheckService::onHandleWork()")
      return
    }

    runBlocking {
      episodesManager.setEpisodeWatched(episodeId, seasonId, IdTrakt(showId), null)
      quickSyncManager.scheduleEpisodes(
        showId = showId,
        episodesIds = listOf(episodeId),
        customDate = null,
      )
      (applicationContext as WidgetsProvider).requestShowsWidgetsUpdate()
    }
  }

  override fun onDestroy() {
    cancel()
    super.onDestroy()
  }
}
