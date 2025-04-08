package com.michaldrabik.data_remote.trakt.interceptors

import com.michaldrabik.data_remote.token.TokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class TraktAuthenticator @Inject constructor(
  private val tokenProvider: TokenProvider,
) : Authenticator {

  @Synchronized
  override fun authenticate(
    route: Route?,
    response: Response,
  ): Request? {
    val token = tokenProvider.getToken()
    if (isAlreadyRefreshed(response, token)) {
      return response.request
        .newBuilder()
        .header("Authorization", "Bearer $token")
        .build()
    }
    return runBlocking(Dispatchers.IO) {
      try {
        Timber.d("Refreshing tokens...")
        val newToken = tokenProvider.refreshToken()
        tokenProvider.saveTokens(
          accessToken = newToken.access_token,
          refreshToken = newToken.refresh_token,
          expiresIn = newToken.expires_in,
          createdAt = newToken.created_at,
        )
        response.request
          .newBuilder()
          .header("Authorization", "Bearer ${newToken.access_token}")
          .build()
      } catch (error: Throwable) {
        if (error !is CancellationException && error.message != "Canceled") {
          tokenProvider.revokeToken()
          null
        } else {
          null
        }
      }
    }
  }

  private fun isAlreadyRefreshed(
    response: Response,
    token: String?,
  ): Boolean {
    val authHeader = response.request.header("Authorization")
    return authHeader != null && !authHeader.contains(token.toString(), true)
  }
}
