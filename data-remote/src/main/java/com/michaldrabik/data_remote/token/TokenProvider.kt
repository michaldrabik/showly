package com.michaldrabik.data_remote.token

import com.michaldrabik.data_remote.trakt.model.OAuthResponse

interface TokenProvider {

  /**
   * Returns access token if available or null.
   */
  fun getToken(): String?

  /**
   * Save access and refresh tokens.
   */
  fun saveTokens(
    accessToken: String,
    refreshToken: String,
    expiresIn: Long,
    createdAt: Long,
  )

  /**
   * Revokes and deletes access and refresh tokens.
   */
  fun revokeToken()

  /**
   * Tries to refresh current access token or throws if failure.
   */
  suspend fun refreshToken(): OAuthResponse

  fun shouldRefresh(): Boolean
}
