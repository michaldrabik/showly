package com.michaldrabik.ui_movie

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.common.errors.ErrorHelper
import com.michaldrabik.common.errors.ShowlyError.CoroutineCancellation
import com.michaldrabik.common.errors.ShowlyError.ResourceNotFoundError
import com.michaldrabik.common.extensions.dateFromMillis
import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.common.extensions.toUtcZone
import com.michaldrabik.repository.UserTraktManager
import com.michaldrabik.repository.images.MovieImagesProvider
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_base.Logger
import com.michaldrabik.ui_base.dates.DateFormatProvider
import com.michaldrabik.ui_base.notifications.AnnouncementManager
import com.michaldrabik.ui_base.utilities.events.MessageEvent
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.utilities.extensions.combine
import com.michaldrabik.ui_base.utilities.extensions.launchDelayed
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import com.michaldrabik.ui_base.viewmodel.ChannelsDelegate
import com.michaldrabik.ui_base.viewmodel.DefaultChannelsDelegate
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Image
import com.michaldrabik.ui_model.ImageType
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.ProgressDateSelectionType.ALWAYS_ASK
import com.michaldrabik.ui_model.RatingState
import com.michaldrabik.ui_model.SpoilersSettings
import com.michaldrabik.ui_model.TraktRating
import com.michaldrabik.ui_model.Translation
import com.michaldrabik.ui_movie.MovieDetailsEvent.Finish
import com.michaldrabik.ui_movie.MovieDetailsEvent.RemoveFromTrakt
import com.michaldrabik.ui_movie.MovieDetailsEvent.RequestWidgetsUpdate
import com.michaldrabik.ui_movie.MovieDetailsUiState.FollowedState
import com.michaldrabik.ui_movie.cases.MovieDetailsHiddenCase
import com.michaldrabik.ui_movie.cases.MovieDetailsListsCase
import com.michaldrabik.ui_movie.cases.MovieDetailsMainCase
import com.michaldrabik.ui_movie.cases.MovieDetailsMyMoviesCase
import com.michaldrabik.ui_movie.cases.MovieDetailsTranslationCase
import com.michaldrabik.ui_movie.cases.MovieDetailsWatchlistCase
import com.michaldrabik.ui_movie.helpers.MovieDetailsMeta
import com.michaldrabik.ui_movie.sections.ratings.cases.MovieDetailsRatingCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlin.properties.Delegates.notNull

@HiltViewModel
class MovieDetailsViewModel @Inject constructor(
  private val mainCase: MovieDetailsMainCase,
  private val translationCase: MovieDetailsTranslationCase,
  private val myMoviesCase: MovieDetailsMyMoviesCase,
  private val ratingsCase: MovieDetailsRatingCase,
  private val watchlistCase: MovieDetailsWatchlistCase,
  private val hiddenCase: MovieDetailsHiddenCase,
  private val listsCase: MovieDetailsListsCase,
  private val settingsRepository: SettingsRepository,
  private val userManager: UserTraktManager,
  private val imagesProvider: MovieImagesProvider,
  private val dateFormatProvider: DateFormatProvider,
  private val announcementManager: AnnouncementManager,
) : ViewModel(),
  ChannelsDelegate by DefaultChannelsDelegate() {

  private var movie by notNull<Movie>()

  private val movieState = MutableStateFlow<Movie?>(null)
  private val movieLoadingState = MutableStateFlow<Boolean?>(null)
  private val imageState = MutableStateFlow<Image?>(null)
  private val followedState = MutableStateFlow<FollowedState?>(null)
  private val ratingState = MutableStateFlow<RatingState?>(null)
  private val translationState = MutableStateFlow<Translation?>(null)
  private val metaState = MutableStateFlow<MovieDetailsMeta?>(null)
  private val spoilersState = MutableStateFlow<SpoilersSettings?>(null)
  private val listsCountState = MutableStateFlow(0)

  val parentMovieState = movieState.asStateFlow()
  val parentFollowedState = followedState.asStateFlow()

  fun loadDetails(id: IdTrakt) {
    viewModelScope.launch {
      val progressJob = launchDelayed(700) {
        movieLoadingState.value = true
      }
      try {
        movie = mainCase.loadDetails(id)

        val isMyMovie = async { myMoviesCase.getMyMovie(movie) }
        val isWatchlist = async { watchlistCase.isWatchlist(movie) }
        val isHidden = async { hiddenCase.isHidden(movie) }

        val myMovie = isMyMovie.await()
        val isFollowed = FollowedState(
          isMyMovie = myMovie != null,
          isWatchlist = isWatchlist.await(),
          isHidden = isHidden.await(),
          withAnimation = false,
          watchedAt = myMovie?.updatedAt?.let { dateFromMillis(it) },
        )

        progressJob.cancel()

        movieState.value = movie
        movieLoadingState.value = false
        followedState.value = isFollowed
        ratingState.value = RatingState(rateLoading = false)
        spoilersState.value = settingsRepository.spoilers.getAll()
        metaState.value = MovieDetailsMeta(
          dateFormat = dateFormatProvider.loadShortDayFormat(),
          commentsDateFormat = dateFormatProvider.loadFullHourFormat(),
          watchedAtDateFormat = dateFormatProvider.loadFullHourFormat(),
          isSignedIn = userManager.isAuthorized(),
        )

        loadBackgroundImage(movie)
        loadListsCount(movie)
        loadUserRating()
        loadTranslation()

        eventChannel.send(RequestWidgetsUpdate)
      } catch (error: Throwable) {
        Timber.e(error)
        progressJob.cancel()
        when (ErrorHelper.parse(error)) {
          is CoroutineCancellation -> rethrowCancellation(error)
          is ResourceNotFoundError -> {
            // Malformed Trakt data or duplicate show.
            messageChannel.send(MessageEvent.Info(R.string.errorMalformedMovie))
            Logger.record(error, "MovieDetailsViewModel::loadDetails(${id.id})")
          }
          else -> {
            messageChannel.send(MessageEvent.Error(R.string.errorCouldNotLoadMovie))
            Logger.record(error, "MovieDetailsViewModel::loadDetails(${id.id})")
          }
        }
      }
    }
  }

  private fun loadBackgroundImage(movie: Movie? = null) {
    viewModelScope.launch {
      try {
        val backgroundImage = imagesProvider.loadRemoteImage(
          movie ?: this@MovieDetailsViewModel.movie,
          ImageType.FANART,
        )
        imageState.value = backgroundImage
      } catch (error: Throwable) {
        imageState.value = Image.createUnavailable(ImageType.FANART)
        Timber.e(error)
        rethrowCancellation(error)
      }
    }
  }

  private fun loadTranslation() {
    viewModelScope.launch {
      try {
        translationCase.loadTranslation(movie)?.let {
          translationState.value = it
        }
      } catch (error: Throwable) {
        rethrowCancellation(error)
      }
    }
  }

  fun loadListsCount(movie: Movie? = null) {
    viewModelScope.launch {
      val count = listsCase.countLists(movie ?: this@MovieDetailsViewModel.movie)
      listsCountState.value = count
    }
  }

  fun loadUserRating() {
    viewModelScope.launch {
      try {
        ratingState.value = RatingState(rateLoading = true)
        val rating = ratingsCase.loadRating(movie)
        ratingState.value =
          RatingState(rateLoading = false, userRating = rating ?: TraktRating.EMPTY)
      } catch (error: Throwable) {
        ratingState.value = RatingState(rateLoading = false)
        rethrowCancellation(error)
      }
    }
  }

  fun addToMyMovies(
    isCustomDateSelected: Boolean = false,
    customDate: ZonedDateTime? = null,
  ) {
    viewModelScope.launch {
      if (!isCustomDateSelected && settingsRepository.progressDateSelectionType == ALWAYS_ASK) {
        eventChannel.send(MovieDetailsEvent.OpenDateSelectionSheet(movie))
        return@launch
      }
      myMoviesCase.addToMyMovies(movie, customDate)
      followedState.value = FollowedState
        .inMyMovies()
        .copy(watchedAt = customDate?.toUtcZone() ?: nowUtc())
      eventChannel.send(RequestWidgetsUpdate)
    }
  }

  fun addToWatchlist() {
    viewModelScope.launch {
      watchlistCase.addToWatchlist(movie)
      followedState.value = FollowedState.inWatchlist()
      eventChannel.send(RequestWidgetsUpdate)
    }
  }

  fun addToHidden() {
    viewModelScope.launch {
      hiddenCase.addToHidden(movie)
      followedState.value = FollowedState.inHidden()
      eventChannel.send(RequestWidgetsUpdate)
    }
  }

  fun removeFromMyMovies() {
    viewModelScope.launch {
      val isMyMovie = myMoviesCase.getMyMovie(movie) != null
      val isWatchlist = watchlistCase.isWatchlist(movie)
      val isHidden = hiddenCase.isHidden(movie)

      when {
        isMyMovie -> myMoviesCase.removeFromMyMovies(movie)
        isWatchlist -> watchlistCase.removeFromWatchlist(movie)
        isHidden -> hiddenCase.removeFromHidden(movie)
      }

      val traktQuickRemoveEnabled = settingsRepository.load().traktQuickRemoveEnabled
      val showRemoveTrakt = userManager.isAuthorized() && traktQuickRemoveEnabled

      val state = FollowedState.idle()
      when {
        isMyMovie -> {
          followedState.value = state
          if (showRemoveTrakt) {
            eventChannel.send(RemoveFromTrakt(R.id.actionMovieDetailsFragmentToRemoveTraktProgress))
          }
        }
        isWatchlist -> {
          followedState.value = state
          if (showRemoveTrakt) {
            eventChannel.send(RemoveFromTrakt(R.id.actionMovieDetailsFragmentToRemoveTraktWatchlist))
          }
        }
        isHidden -> {
          followedState.value = state
          if (showRemoveTrakt) {
            eventChannel.send(RemoveFromTrakt(R.id.actionMovieDetailsFragmentToRemoveTraktHidden))
          }
        }
        else -> error("Unexpected movie state.")
      }
      eventChannel.send(RequestWidgetsUpdate)
      announcementManager.refreshMoviesAnnouncements()
    }
  }

  fun removeMalformedMovie(id: IdTrakt) {
    viewModelScope.launch {
      try {
        mainCase.removeMalformedMovie(id)
      } catch (error: Throwable) {
        Timber.e(error)
        rethrowCancellation(error)
      } finally {
        eventChannel.send(Finish)
      }
    }
  }

  val uiState = combine(
    movieState,
    movieLoadingState,
    imageState,
    followedState,
    ratingState,
    translationState,
    listsCountState,
    metaState,
    spoilersState,
  ) { s1, s2, s3, s4, s5, s6, s7, s8, s9 ->
    MovieDetailsUiState(
      movie = s1,
      movieLoading = s2,
      image = s3,
      followedState = s4,
      ratingState = s5,
      translation = s6,
      listsCount = s7,
      meta = s8,
      spoilers = s9,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = MovieDetailsUiState(),
  )
}
