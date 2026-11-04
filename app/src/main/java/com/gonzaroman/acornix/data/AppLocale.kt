package com.gonzaroman.acornix.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Acornix's language (Settings → Language): its screens, the example shortcuts and the apps the AI writes.
 * From Android 13 the system keeps it as the app's own language, which can also be changed in the phone's
 * settings. Before that, each activity applies it itself through [wrap].
 */
object AppLocale {
    private const val PREFS = "app_locale"
    private const val KEY = "language"
    private const val MIGRATED = "migrated"
    private val phone: Locale = Locale.getDefault()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun tag(lang: AppLanguage) = when (lang) {
        AppLanguage.SPANISH -> "es"
        AppLanguage.ENGLISH -> "en"
        AppLanguage.AUTO -> ""
    }

    /** Whether [lang] means Spanish ("from the phone" looks at the phone's own language). */
    fun isSpanish(lang: AppLanguage) =
        tag(lang).ifEmpty { android.content.res.Resources.getSystem().configuration.locales[0].language } == "es"

    /** Switches every screen to [lang]. Android 13+ recreates the open screens; before, the current one is recreated. */
    fun apply(context: Context, lang: AppLanguage) {
        val tag = tag(lang)
        prefs(context).edit().putString(KEY, tag).apply()
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            Locale.setDefault(if (tag.isEmpty()) phone else Locale.forLanguageTag(tag))
            (context as? Activity)?.recreate()
        }
    }

    /** Before Android 13: [base] in the chosen language, for an activity's or the app's attachBaseContext. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = prefs(base).getString(KEY, "").orEmpty()
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(locale) })
    }

    /**
     * On start, keeps the setting and the system in step. The first time, a language picked before 2.6.1 (when
     * it only changed the apps' texts) becomes Acornix's language. After that, Android 13+ may have changed it
     * from the phone's settings, so the setting follows the system.
     */
    suspend fun sync(context: Context, settings: SettingsRepository) {
        val current = settings.current().appLanguage
        val prefs = prefs(context)
        if (!prefs.getBoolean(MIGRATED, false)) {
            prefs.edit().putBoolean(MIGRATED, true).apply()
            if (current != AppLanguage.AUTO) apply(context, current)
            return
        }
        if (Build.VERSION.SDK_INT < 33) return
        val system = context.getSystemService(LocaleManager::class.java)?.applicationLocales?.get(0)?.language.orEmpty()
        val lang = AppLanguage.entries.firstOrNull { tag(it) == system } ?: AppLanguage.AUTO
        if (lang != current) settings.setAppLanguage(lang)
        prefs.edit().putString(KEY, tag(lang)).apply()
    }
}
