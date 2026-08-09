package com.tdcreator.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.tdcreator.app.R
import com.tdcreator.app.BuildConfig
import com.tdcreator.core.data.prefs.PreferencesRepository
import com.tdcreator.core.ui.components.SectionHeader
import com.tdcreator.core.ui.components.SecondaryButton
import kotlinx.coroutines.launch

/**
 * Settings: language (English / 中文 / Follow system), theme, and sign-out. Changing the
 * language persists to DataStore and recreates the Activity so every string reloads in the new
 * locale (see [com.tdcreator.core.i18n.LocaleManager]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController, vm: SettingsViewModel = hiltViewModel()) {
    val language by vm.language.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? android.app.Activity

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = nav::popBackStack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.settings_language), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)

            LanguageOption(stringResource(R.string.settings_language_system), PreferencesRepository.LANGUAGE_SYSTEM, language) {
                scope.launch { vm.setLanguage(it); activity?.recreate() }
            }
            LanguageOption(stringResource(R.string.settings_language_english), PreferencesRepository.LANGUAGE_EN, language) {
                scope.launch { vm.setLanguage(it); activity?.recreate() }
            }
            LanguageOption(stringResource(R.string.settings_language_chinese), PreferencesRepository.LANGUAGE_ZH, language) {
                scope.launch { vm.setLanguage(it); activity?.recreate() }
            }

            Text(stringResource(R.string.settings_theme), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            LanguageOption(stringResource(R.string.settings_theme_system), PreferencesRepository.THEME_SYSTEM, theme) {
                scope.launch { vm.setTheme(it); activity?.recreate() }
            }
            LanguageOption(stringResource(R.string.settings_theme_light), PreferencesRepository.THEME_LIGHT, theme) {
                scope.launch { vm.setTheme(it); activity?.recreate() }
            }
            LanguageOption(stringResource(R.string.settings_theme_dark), PreferencesRepository.THEME_DARK, theme) {
                scope.launch { vm.setTheme(it); activity?.recreate() }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
            }

            SectionHeader(stringResource(R.string.settings_about))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_about_text), modifier = Modifier.padding(14.dp))
            }

            SectionHeader(stringResource(R.string.settings_clear_cache))
            SecondaryButton(stringResource(R.string.settings_clear_cache)) {
                Toast.makeText(context, context.getString(R.string.common_coming_soon), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun LanguageOption(label: String, value: String, selected: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected == value, onClick = { onSelect(value) })
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
