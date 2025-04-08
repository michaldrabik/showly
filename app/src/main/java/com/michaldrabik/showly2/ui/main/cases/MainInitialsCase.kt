package com.michaldrabik.showly2.ui.main.cases

import android.content.Context
import android.content.SharedPreferences
import android.telephony.TelephonyManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import com.michaldrabik.common.Config
import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.showly2.BuildConfig
import com.michaldrabik.ui_base.common.AppCountry
import com.michaldrabik.ui_base.utilities.extensions.withApiAtLeast
import com.michaldrabik.ui_settings.helpers.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named

@ViewModelScoped
class MainInitialsCase @Inject constructor(
  @ApplicationContext private val context: Context,
  private val settingsRepository: SettingsRepository,
  @Named("miscPreferences") private var miscPreferences: SharedPreferences,
) {

  suspend fun setInitialRun(value: Boolean) {
    val settings = settingsRepository.load()
    settings.let {
      settingsRepository.update(it.copy(isInitialRun = value))
    }
  }

  suspend fun isInitialRun(): Boolean {
    val settings = settingsRepository.load()
    return settings.isInitialRun
  }

  fun setInitialCountry() {
    var country = (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.simCountryIso
    if (country == null) {
      val locale = LocaleListCompat.getAdjustedDefault()
      country = if (locale.size() > 1) {
        locale.get(1)?.country
      } else {
        locale.get(0)?.country
      }
    }
    if (!country.isNullOrBlank()) {
      AppCountry.values().forEach { appCountry ->
        if (appCountry.code.equals(country, ignoreCase = true)) {
          settingsRepository.country = appCountry.code
          return
        }
      }
    }
  }

  suspend fun setInitialNotifications() {
    withApiAtLeast(33) {
      val settings = settingsRepository.load()
      settings.let {
        settingsRepository.update(it.copy(episodesNotificationsEnabled = false))
      }
    }
  }

  fun setLanguage(appLanguage: AppLanguage) {
    settingsRepository.language = appLanguage.code
    val locales = LocaleListCompat.forLanguageTags(appLanguage.code)
    AppCompatDelegate.setApplicationLocales(locales)
  }

  fun checkInitialLanguage(): AppLanguage {
    val locales = LocaleListCompat.getAdjustedDefault()
    val appLanguages = AppLanguage.values()

    if (locales.size() == 1 && !locales[0]?.language.equals(Locale("en").language)) {
      appLanguages.forEach { appLanguage ->
        if (appLanguage.code.equals(locales[0]?.language, ignoreCase = true)) {
          return appLanguage
        }
      }
    }

    if (locales.size() > 1) {
      val languagesCodes = arrayOf(locales[0], locales[1])
        .filterNotNull()
        .map { it.language.lowercase() }
      if (languagesCodes.any { it != Locale(Config.DEFAULT_LANGUAGE).language }) {
        val languageCodes = appLanguages.map { it.code }
        languagesCodes.forEach { language ->
          if (language in languageCodes) {
            return appLanguages.first { it.code == language }
          }
        }
        appLanguages
          .filter { it.code != Config.DEFAULT_LANGUAGE }
          .forEach { appLanguage ->
            if (appLanguage.code in languagesCodes) {
              return appLanguage
            }
          }
      }
    }

    return AppLanguage.ENGLISH
  }

  fun showWhatsNew(isInitialRun: Boolean): Boolean {
    val keyAppVersion = "APP_VERSION"
    val keyAppVersionName = "APP_VERSION_NAME"

    val version = miscPreferences.getInt(keyAppVersion, 0)
    val name = miscPreferences.getString(keyAppVersionName, "")

    fun isPatchUpdate(): Boolean {
      if (name.isNullOrBlank()) return false

      val major = name.split(".").getOrNull(0)?.toIntOrNull()
      val minor = name.split(".").getOrNull(1)?.toIntOrNull()

      val currentMajor = BuildConfig.VERSION_NAME
        .split(".")
        .getOrNull(0)
        ?.toIntOrNull()
      val currentMinor = BuildConfig.VERSION_NAME
        .split(".")
        .getOrNull(1)
        ?.toIntOrNull()

      return (major == currentMajor) && (minor == currentMinor)
    }

    miscPreferences.edit {
      putInt(keyAppVersion, BuildConfig.VERSION_CODE).apply()
      putString(keyAppVersionName, BuildConfig.VERSION_NAME).apply()
    }

    return Config.SHOW_WHATS_NEW &&
      BuildConfig.VERSION_CODE > version &&
      BuildConfig.VERSION_NAME != name &&
      !isInitialRun &&
      !isPatchUpdate()
  }

  fun saveInstallTimestamp() {
    if (settingsRepository.installTimestamp == 0L) {
      settingsRepository.installTimestamp = nowUtcMillis()
      Timber.d("Installation timestamp saved: ${nowUtc()}")
    }
  }
}
