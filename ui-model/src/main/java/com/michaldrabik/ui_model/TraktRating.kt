package com.michaldrabik.ui_model

import com.michaldrabik.common.extensions.nowUtc
import java.time.ZonedDateTime

data class TraktRating(
  val idTrakt: IdTrakt,
  val rating: Int,
  val ratedAt: ZonedDateTime,
) {
  companion object {
    val EMPTY = TraktRating(IdTrakt(-1), 0, nowUtc())
  }
}
