package com.kiko.tracker.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale
import androidx.core.content.edit

/**
 * Languages the UI can be shown in. [System] follows the device language (falling back to English
 * when the device language isn't supported). To add a language: add an entry here, a
 * res/values-<tag>/strings.xml, and a <locale> line in res/xml/locales_config.xml.
 *
 * [nativeName] is deliberately not translated — each language is listed under its own name so a
 * person can still find theirs after the app ended up in a language they can't read.
 */
enum class AppLanguage(val tag: String, val nativeName: String?) {
    System("", null),
    English("en", "English"),
    Spanish("es", "Español");

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag.isNotEmpty() && it.tag == tag } ?: System
    }
}

private const val APP_LANGUAGE_KEY = "app_language"

/** The language currently selected in-app. */
fun currentAppLanguage(context: Context): AppLanguage {
    // API 33+: the system owns the per-app locale (also editable from Settings > Apps > Kiko >
    // Language), so read it back from there rather than keeping a second copy that could drift.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        return if (locales.isEmpty) AppLanguage.System else AppLanguage.fromTag(locales[0].language)
    }
    return AppLanguage.fromTag(settingsPrefs(context).getString(APP_LANGUAGE_KEY, ""))
}

/**
 * Switches the app language. The activity is recreated afterwards (by the system on API 33+, by
 * hand below that) so every string is re-resolved in the new locale.
 */
fun setAppLanguage(activity: Activity, language: AppLanguage) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        activity.getSystemService(LocaleManager::class.java).applicationLocales =
            if (language == AppLanguage.System) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
    } else {
        settingsPrefs(activity).edit { putString(APP_LANGUAGE_KEY, language.tag) }
        activity.recreate()
    }
}

/**
 * For Activity.attachBaseContext: applies the saved language below API 33, where the platform has
 * no per-app locale API. No-op on 33+ (the system already applied it) or when following the system.
 */
fun Context.withAppLanguage(): Context {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return this
    val language = AppLanguage.fromTag(settingsPrefs(this).getString(APP_LANGUAGE_KEY, ""))
    if (language == AppLanguage.System) return this
    val locale = Locale.forLanguageTag(language.tag)
    Locale.setDefault(locale)
    val config = Configuration(resources.configuration).apply { setLocale(locale) }
    return createConfigurationContext(config)
}
