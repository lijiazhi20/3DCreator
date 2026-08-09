package com.tdcreator.app

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tdcreator.core.data.prefs.PreferencesRepository
import com.tdcreator.core.i18n.LocaleManager
import com.tdcreator.core.ui.theme.TDCreatorTheme
import com.tdcreator.feature.camera.CameraScreen
import com.tdcreator.feature.gallery.GalleryScreen
import com.tdcreator.feature.home.HomeScreen
import com.tdcreator.feature.jobs.JobDetailScreen
import com.tdcreator.feature.jobs.JobsScreen
import com.tdcreator.feature.settings.SettingsScreen
import com.tdcreator.feature.share.ShareScreen
import com.tdcreator.feature.upload.UploadScreen
import com.tdcreator.feature.viewer.ViewerScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        // Apply persisted language before any Compose content is inflated.
        val lang = PreferencesRepository(newBase).languageBlocking()
        super.attachBaseContext(LocaleManager.wrap(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = PreferencesRepository(applicationContext)
            val theme by prefs.theme.collectAsStateWithLifecycle(
                initialValue = runBlocking { prefs.theme.first() },
            )
            TDCreatorTheme(themeMode = theme) {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = com.tdcreator.app.navigation.Routes.HOME) {
                    composable(com.tdcreator.app.navigation.Routes.HOME) { HomeScreen(nav) }
                    composable(com.tdcreator.app.navigation.Routes.CAPTURE) { CameraScreen(nav) }
                    composable(com.tdcreator.app.navigation.Routes.GALLERY) { GalleryScreen(nav) }
                    composable(com.tdcreator.app.navigation.Routes.UPLOAD) { UploadScreen(nav) }
                    composable(com.tdcreator.app.navigation.Routes.JOBS) { JobsScreen(nav) }
                    composable(
                        com.tdcreator.app.navigation.Routes.JOB_DETAIL,
                        arguments = listOf(navArgument("jobId") { type = NavType.StringType }),
                    ) { back -> JobDetailScreen(nav, back.arguments?.getString("jobId") ?: "") }
                    composable(
                        com.tdcreator.app.navigation.Routes.VIEWER,
                        arguments = listOf(navArgument("jobId") { type = NavType.StringType }),
                    ) { back -> ViewerScreen(nav, back.arguments?.getString("jobId") ?: "") }
                    composable(
                        com.tdcreator.app.navigation.Routes.SHARE,
                        arguments = listOf(navArgument("jobId") { type = NavType.StringType }),
                    ) { back -> ShareScreen(nav, back.arguments?.getString("jobId") ?: "") }
                    composable(com.tdcreator.app.navigation.Routes.SETTINGS) { SettingsScreen(nav) }
                }
            }
        }
    }
}
