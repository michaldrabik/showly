package com.michaldrabik.ui_base.utilities.extensions

import kotlinx.coroutines.CancellationException
import timber.log.Timber

const val SUBSCRIBE_STOP_TIMEOUT = 5000L

/**
 * Rethrows [CancellationException] and logs other exceptions.
 */
fun rethrowCancellation(error: Throwable) {
  if (error is CancellationException) {
    Timber.d("Rethrowing CancellationException")
    throw error
  } else {
    Timber.e(error)
  }
}

/**
 * Rethrows [CancellationException] and logs other exceptions or executes [action].
 */
fun rethrowCancellation(
  error: Throwable,
  action: (Throwable) -> Unit,
) {
  if (error is CancellationException) {
    Timber.d("Rethrowing CancellationException")
    throw error
  } else {
    action(error)
  }
}
