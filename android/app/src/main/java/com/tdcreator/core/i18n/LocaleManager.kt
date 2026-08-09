package com.tdcreator.core.i18n

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.tdcreator.core.data.prefs.PreferencesRepository
import java.util.Locale

/**
 * Runtime locale switching.
 *
 * Mechanism:
 * 1. The chosen language is persisted in DataStore via [PreferencesRepository].
 * 2. [App] (or the base Activity) overrides `attachBaseContext` and wraps the base context
 *    with a [LocaleContextWrapper] that forces the desired [Locale].
 * 3. When the user changes language in Settings, we persist it and call `recreate()` on the
 *    Activity so every `stringResource(R.string.*)` is re-resolved against the new locale.
 *
 * "system" means: do not override — inherit whatever locale the OS reports.
 */
object LocaleManager {

    /** Build a context wrapper that applies [lang] (en/zh) or the system locale when "system". */
    fun wrap(context: Context, lang: String): Context {
        if (lang == PreferencesRepository.LANGUAGE_SYSTEM) return context
        val locale = Locale(lang)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        // Also set the layout direction so RTL (future) locales work correctly.
        config.setLayoutDirection(locale)

        val wrapped = context.createConfigurationContext(config)
        return LocaleContextWrapper(wrapped, lang)
    }

    /** Direct, in-place apply (deprecated path kept for non-compose callers / tests). */
    @Suppress("DEPRECATION")
    fun applyLocale(context: Context, lang: String) {
        if (lang == PreferencesRepository.LANGUAGE_SYSTEM) return
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val resources: Resources = context.resources
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    fun toDisplayName(lang: String): String = when (lang) {
        PreferencesRepository.LANGUAGE_EN -> "English"
        PreferencesRepository.LANGUAGE_ZH -> "中文"
        else -> "System"
    }
}

/**
 * Simple context wrapper so we can recover the active language code if needed downstream.
 */
class LocaleContextWrapper(base: Context, val lang: String) : android.content.ContextWrapper(base)
