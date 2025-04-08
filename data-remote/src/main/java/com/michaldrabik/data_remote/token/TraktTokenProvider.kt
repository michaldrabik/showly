package com.michaldrabik.data_remote.token

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.michaldrabik.data_remote.Config
import com.michaldrabik.data_remote.trakt.model.OAuthResponse
import com.squareup.moshi.Moshi
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.internal.closeQuietly
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

@SuppressLint("ApplySharedPref")
@Singleton
internal class TraktTokenProvider(
  private val sharedPreferences: SharedPreferences,
  private val moshi: Moshi,
  @Named("okHttpBase") private val okHttpClient: OkHttpClient,
) : TokenProvider {

  companion object {
    private const val KEY_ACCESS_TOKEN = "TRAKT_ACCESS_TOKEN"
    private const val KEY_REFRESH_TOKEN = "TRAKT_REFRESH_TOKEN"
    private const val KEY_TOKEN_CREATED_AT = "TRAKT_ACCESS_TOKEN_TIMESTAMP"
    private const val KEY_TOKEN_EXPIRES_AT = "TRAKT_ACCESS_TOKEN_EXPIRES_TIMESTAMP"
  }

  private var token: String? = null

  override fun getToken(): String? {
    if (token == null) {
      token = sharedPreferences.getString(KEY_ACCESS_TOKEN, null)
    }
    return token
  }

  override fun saveTokens(
    accessToken: String,
    refreshToken: String,
    expiresIn: Long,
    createdAt: Long,
  ) {
    val createdAtMillis = createdAt.seconds.inWholeMilliseconds
    val expiresAtMillis = createdAtMillis + expiresIn.seconds.inWholeMilliseconds

    sharedPreferences
      .edit()
      .putString(KEY_ACCESS_TOKEN, accessToken)
      .putString(KEY_REFRESH_TOKEN, refreshToken)
      .putLong(KEY_TOKEN_CREATED_AT, createdAtMillis)
      .putLong(KEY_TOKEN_EXPIRES_AT, expiresAtMillis)
      .commit()

    token = null
  }

  override fun revokeToken() {
    sharedPreferences
      .edit()
      .clear()
      .remove(KEY_ACCESS_TOKEN)
      .remove(KEY_REFRESH_TOKEN)
      .remove(KEY_TOKEN_CREATED_AT)
      .remove(KEY_TOKEN_EXPIRES_AT)
      .commit()

    token = null
  }

  override fun shouldRefresh(): Boolean {
    Timber.d("Checking if token should be refreshed...")
    val nowMillis = System.currentTimeMillis()

    val tokenCreatedAt = sharedPreferences.getLong(KEY_TOKEN_CREATED_AT, 0L)
    val tokenExpiresAt = sharedPreferences.getLong(KEY_TOKEN_EXPIRES_AT, 0L)

    if (tokenCreatedAt == 0L) {
      Timber.d("No timestamp available.")
      return false
    }

    if (nowMillis - tokenCreatedAt > Config.TRAKT_TOKEN_REFRESH_DURATION.toMillis()) {
      Timber.d("Token should be refreshed.")
      return true
    }

    if (tokenExpiresAt in 1..nowMillis) {
      Timber.d("Token expired. Should be refreshed.")
      return true
    }

    Timber.d("Token should not be refreshed.")
    return false
  }

  override suspend fun refreshToken(): OAuthResponse {
    val refreshToken = sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
      ?: throw Error("Refresh token is not available")

    val body = JSONObject()
      .put("refresh_token", refreshToken)
      .put("client_id", Config.TRAKT_CLIENT_ID)
      .put("client_secret", Config.TRAKT_CLIENT_SECRET)
      .put("redirect_uri", Config.TRAKT_REDIRECT_URL)
      .put("grant_type", "refresh_token")
      .toString()

    val request = Request
      .Builder()
      .url("${Config.TRAKT_BASE_URL}oauth/token")
      .addHeader("Content-Type", "application/json")
      .post(body.toRequestBody("application/json".toMediaType()))
      .build()

    Timber.d("Making refresh token call...")

    return suspendCancellableCoroutine {
      val callback = object : Callback {
        override fun onFailure(
          call: Call,
          e: IOException,
        ) {
          Timber.d("Refresh token call failed. $e")
          it.resumeWithException(Error("Refresh token call failed. $e"))
        }

        override fun onResponse(
          call: Call,
          response: Response,
        ) {
          if (response.isSuccessful) {
            Timber.d("Refresh token success!")
            val responseSource = response.body!!.source()
            val result = moshi.adapter(OAuthResponse::class.java).fromJson(responseSource)!!
            it.resume(result)
          } else {
            it.resumeWithException(Error("Refresh token call failed. ${response.code}"))
          }
          response.closeQuietly()
        }
      }
      val call = okHttpClient.newCall(request)
      it.invokeOnCancellation { call.cancel() }
      call.enqueue(callback)
    }
  }
}
