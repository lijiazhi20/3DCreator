package com.tdcreator.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * Lightweight preferences backed by DataStore. Holds the auth token, the chosen UI language,
 * and the theme. Language is one of [LANGUAGE_SYSTEM] / [LANGUAGE_EN] / [LANGUAGE_ZH].
 */
class PreferencesRepository(private val context: Context) {

    private val Context.dataStore by preferencesDataStore(name = "tdcreator_prefs")

    private val AUTH_TOKEN = stringPreferencesKey("auth_token")
    private val LANGUAGE = stringPreferencesKey("language")
    private val THEME = stringPreferencesKey("theme")
    private val USER_ID = stringPreferencesKey("user_id")

    val authToken: Flow<String> = context.dataStore.data.map { it[AUTH_TOKEN] ?: "" }
    val language: Flow<String> = context.dataStore.data.map { it[LANGUAGE] ?: LANGUAGE_SYSTEM }
    val theme: Flow<String> = context.dataStore.data.map { it[THEME] ?: THEME_SYSTEM }

    suspend fun setAuthToken(token: String) {
        context.dataStore.edit { it[AUTH_TOKEN] = token }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[LANGUAGE] = lang }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[THEME] = theme }
    }

    suspend fun setUserId(id: String) {
        context.dataStore.edit { it[USER_ID] = id }
    }

    /** Blocking read used by [attachBaseContext] before the first compose frame. */
    fun languageBlocking(): String = runBlocking { language.first() }

    companion object {
        const val LANGUAGE_SYSTEM = "system"
        const val LANGUAGE_EN = "en"
        const val LANGUAGE_ZH = "zh"

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }
}
