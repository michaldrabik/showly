package com.michaldrabik.ui_episodes.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.common.Config
import com.michaldrabik.common.errors.ErrorHelper
import com.michaldrabik.common.errors.ShowlyError.CoroutineCancellation
import com.michaldrabik.common.errors.ShowlyError.ResourceConflictError
import com.michaldrabik.repository.CommentsRepository
import com.michaldrabik.repository.RatingsRepository
import com.michaldrabik.repository.TranslationsRepository
import com.michaldrabik.repository.UserTraktManager
import com.michaldrabik.repository.images.EpisodeImagesProvider
import com.michaldrabik.repository.settings.SettingsSpoilersRepository
import com.michaldrabik.ui_base.dates.DateFormatProvider
import com.michaldrabik.ui_base.utilities.events.MessageEvent
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.utilities.extensions.combine
import com.michaldrabik.ui_base.utilities.extensions.findReplace
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import com.michaldrabik.ui_base.viewmodel.ChannelsDelegate
import com.michaldrabik.ui_base.viewmodel.DefaultChannelsDelegate
import com.michaldrabik.ui_episodes.R
import com.michaldrabik.ui_episodes.details.cases.EpisodeDetailsSeasonCase
import com.michaldrabik.ui_episodes.details.cases.EpisodeDetailsWatchedCase
import com.michaldrabik.ui_model.Comment
import com.michaldrabik.ui_model.Episode
import com.michaldrabik.ui_model.IdTmdb
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Image
import com.michaldrabik.ui_model.RatingState
import com.michaldrabik.ui_model.SpoilersSettings
import com.michaldrabik.ui_model.Translation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class EpisodeDetailsViewModel @Inject constructor(
  settingsSpoilersRepository: SettingsSpoilersRepository,
  private val seasonsCase: EpisodeDetailsSeasonCase,
  private val watchedCase: EpisodeDetailsWatchedCase,
  private val imagesProvider: EpisodeImagesProvider,
  private val dateFormatProvider: DateFormatProvider,
  private val ratingsRepository: RatingsRepository,
  private val translationsRepository: TranslationsRepository,
  private val commentsRepository: CommentsRepository,
  private val userTraktManager: UserTraktManager,
) : ViewModel(),
  ChannelsDelegate by DefaultChannelsDelegate() {

  private val imageState = MutableStateFlow<Image?>(null)
  private val imageLoadingState = MutableStateFlow(false)
  private val episodesState = MutableStateFlow<List<Episode>?>(null)
  private val commentsState = MutableStateFlow<List<Comment>?>(null)
  private val commentsLoadingState = MutableStateFlow(false)
  private val signedInState = MutableStateFlow(false)
  private val ratingState = MutableStateFlow<RatingState?>(null)
  private val translationState = MutableStateFlow<Translation?>(null)
  private val lastWatchedAtState = MutableStateFlow<ZonedDateTime?>(null)
  private val dateFormatState = MutableStateFlow<DateTimeFormatter?>(null)
  private val commentsDateFormatState = MutableStateFlow<DateTimeFormatter?>(null)
  private val spoilersState = MutableStateFlow<SpoilersSettings?>(null)

  init {
    dateFormatState.value = dateFormatProvider.loadFullHourFormat()
    spoilersState.value = settingsSpoilersRepository.getAll()
  }

  fun loadLastWatchedAt(
    showTraktId: IdTrakt,
    episode: Episode,
  ) {
    viewModelScope.launch {
      val lastWatchedAt = watchedCase.getLastWatchedAt(showTraktId, episode)
      lastWatchedAtState.update { lastWatchedAt }
    }
  }

  fun loadImage(
    showId: IdTmdb,
    episode: Episode,
  ) {
    viewModelScope.launch {
      try {
        imageLoadingState.value = true
        val episodeImage = imagesProvider.loadRemoteImage(showId, episode)
        imageState.value = episodeImage
        imageLoadingState.value = false
      } catch (t: Throwable) {
        imageLoadingState.value = false
      }
    }
  }

  fun loadSeason(
    showTraktId: IdTrakt,
    episode: Episode,
    seasonEpisodes: IntArray?,
  ) {
    viewModelScope.launch {
      val episodes = seasonsCase.loadSeason(showTraktId, episode, seasonEpisodes)
      if (episodes.isNotEmpty()) {
        delay(100)
      }
      episodesState.value = episodes
    }
  }

  fun loadTranslation(
    showTraktId: IdTrakt,
    episode: Episode,
  ) {
    viewModelScope.launch {
      try {
        val language = translationsRepository.getLanguage()
        if (language == Config.DEFAULT_LANGUAGE) {
          return@launch
        }
        val translation = translationsRepository.loadTranslation(episode, showTraktId, language)
        translation?.let {
          translationState.value = it
        }
      } catch (error: Throwable) {
        Timber.e(error)
      }
    }
  }

  fun loadComments(
    idTrakt: IdTrakt,
    season: Int,
    episode: Int,
  ) {
    if (!commentsState.value.isNullOrEmpty()) {
      return
    }
    viewModelScope.launch {
      try {
        commentsLoadingState.value = true

        val isSignedIn = userTraktManager.isAuthorized()
        val username = userTraktManager.getUsername()
        val comments = commentsRepository
          .loadEpisodeComments(idTrakt, season, episode)
          .map {
            it.copy(
              isMe = it.user.username == username,
              isSignedIn = isSignedIn,
            )
          }.partition { it.isMe }

        signedInState.value = isSignedIn
        commentsState.value = comments.first + comments.second
        commentsLoadingState.value = false
        commentsDateFormatState.value = dateFormatProvider.loadFullHourFormat()
      } catch (error: Throwable) {
        Timber.w("Failed to load comments. ${error.message}")
        commentsLoadingState.value = false
        rethrowCancellation(error)
      }
    }
  }

  fun loadRatings(episode: Episode) {
    viewModelScope.launch {
      try {
        ratingState.value = RatingState(rateLoading = true)
        val rating = ratingsRepository.shows.loadRating(episode)
        ratingState.value = RatingState(rateLoading = false, userRating = rating)
      } catch (error: Throwable) {
        ratingState.value = RatingState(rateLoading = false)
      }
    }
  }

  fun loadCommentReplies(comment: Comment) {
    var current = uiState.value.comments?.toMutableList() ?: mutableListOf()
    if (current.any { it.parentId == comment.id }) return

    viewModelScope.launch {
      try {
        val parent = current.find { it.id == comment.id }
        parent?.let { p ->
          val copy = p.copy(isLoading = true)
          current.findReplace(copy) { it.id == p.id }
          commentsState.value = current
        }

        val isSignedIn = userTraktManager.isAuthorized()
        val username = userTraktManager.getUsername()
        val replies = commentsRepository
          .loadReplies(comment.id)
          .map {
            it.copy(
              isSignedIn = isSignedIn,
              isMe = it.user.username == username,
            )
          }

        current = uiState.value.comments?.toMutableList() ?: mutableListOf()
        val parentIndex = current.indexOfFirst { it.id == comment.id }
        if (parentIndex > -1) current.addAll(parentIndex + 1, replies)
        parent?.let {
          current.findReplace(parent.copy(isLoading = false, replies = 0)) { it.id == comment.id }
        }
        commentsState.value = current
      } catch (t: Throwable) {
        commentsState.value = current
      }
    }
  }

  fun addNewComment(comment: Comment) {
    val current = uiState.value.comments?.toMutableList() ?: mutableListOf()
    if (!comment.isReply()) {
      current.add(0, comment)
    } else {
      val parentIndex = current.indexOfLast { it.id == comment.parentId }
      if (parentIndex > -1) {
        val parent = current[parentIndex]
        current.add(parentIndex + 1, comment)
        val repliesCount = current.count { it.parentId == parent.id }.toLong()
        current.findReplace(parent.copy(replies = repliesCount)) { it.id == comment.parentId }
      }
    }
    commentsState.value = current
  }

  fun deleteComment(comment: Comment) {
    var current = uiState.value.comments?.toMutableList() ?: mutableListOf()
    val target = current.find { it.id == comment.id } ?: return

    viewModelScope.launch {
      try {
        val copy = target.copy(isLoading = true)
        current.findReplace(copy) { it.id == target.id }
        commentsState.value = current

        commentsRepository.deleteComment(target.id)

        current = uiState.value.comments?.toMutableList() ?: mutableListOf()
        val targetIndex = current.indexOfFirst { it.id == target.id }
        if (targetIndex > -1) {
          current.removeAt(targetIndex)
          if (target.isReply()) {
            val parent = current.first { it.id == target.parentId }
            val repliesCount = current.count { it.parentId == parent.id }.toLong()
            current.findReplace(parent.copy(replies = repliesCount)) { it.id == target.parentId }
          }
        }

        commentsState.value = current
        messageChannel.send(MessageEvent.Info(R.string.textCommentDeleted))
      } catch (t: Throwable) {
        when (ErrorHelper.parse(t)) {
          is CoroutineCancellation -> rethrowCancellation(t)
          is ResourceConflictError -> messageChannel.send(MessageEvent.Error(R.string.errorCommentDelete))
          else -> messageChannel.send(MessageEvent.Error(R.string.errorGeneral))
        }
        commentsState.value = current
      }
    }
  }

  val uiState = combine(
    imageState,
    imageLoadingState,
    episodesState,
    commentsState,
    commentsLoadingState,
    signedInState,
    ratingState,
    translationState,
    dateFormatState,
    commentsDateFormatState,
    spoilersState,
    lastWatchedAtState,
  ) { s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12 ->
    EpisodeDetailsUiState(
      image = s1,
      isImageLoading = s2,
      episodes = s3,
      comments = s4,
      isCommentsLoading = s5,
      isSignedIn = s6,
      rating = s7,
      translation = s8,
      dateFormat = s9,
      commentsDateFormat = s10,
      spoilers = s11,
      lastWatchedAt = s12,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = EpisodeDetailsUiState(),
  )
}
