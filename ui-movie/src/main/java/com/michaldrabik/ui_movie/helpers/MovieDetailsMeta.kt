package com.michaldrabik.ui_movie.helpers

import java.time.format.DateTimeFormatter

data class MovieDetailsMeta(
  val dateFormat: DateTimeFormatter,
  val commentsDateFormat: DateTimeFormatter,
  val watchedAtDateFormat: DateTimeFormatter,
  val isSignedIn: Boolean,
)
