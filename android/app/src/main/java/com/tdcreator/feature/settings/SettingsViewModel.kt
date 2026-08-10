package com.tdcreator.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tdcreator.app.BuildConfig
import com.tdcreator.core.data.prefs.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) : ViewModel() {

    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreferencesRepository.LANGUAGE_SYSTEM)

    val theme: StateFlow<String> = prefs.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreferencesRepository.THEME_SYSTEM)

    /** App version, surfaced on the About row. */
    val versionName: String = BuildConfig.VERSION_NAME

    private val _cacheSize = MutableStateFlow(0L)
    val cacheSize: StateFlow<Long> = _cacheSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _clearing = MutableStateFlow(false)
    val clearing = _clearing.asStateFlow()

    init { refreshCacheSize() }

    fun setLanguage(lang: String) = viewModelScope.launch { prefs.setLanguage(lang) }
    fun setTheme(theme: String) = viewModelScope.launch { prefs.setTheme(theme) }
    fun signOut() = viewModelScope.launch { prefs.setAuthToken("") }

    // ---- cache management (placeholder wiring; UI button owned by the UI team) ----
    fun refreshCacheSize() {
        _cacheSize.value = computeCacheSize(context.cacheDir)
    }

    fun clearCache() {
        if (_clearing.value) return
        viewModelScope.launch {
            _clearing.value = true
            clearDir(context.cacheDir)
            _cacheSize.value = computeCacheSize(context.cacheDir)
            _clearing.value = false
        }
    }

    private fun computeCacheSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return dir.listFiles()?.sumOf { f -> if (f.isDirectory) computeCacheSize(f) else f.length() } ?: 0L
    }

    private fun clearDir(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var freed = 0L
        dir.listFiles()?.forEach { f ->
            freed += if (f.isDirectory) clearDir(f) else f.length()
            f.delete()
        }
        return freed
    }
}
