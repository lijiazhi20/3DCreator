package com.tdcreator.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.tdcreator.app.R
import com.tdcreator.app.navigation.Routes
import com.tdcreator.core.network.dto.JobType
import com.tdcreator.feature.upload.UploadViewModel
import com.tdcreator.core.ui.components.InfoCard
import com.tdcreator.core.ui.components.PrimaryButton
import com.tdcreator.core.ui.components.SecondaryButton
import com.tdcreator.core.ui.components.SectionHeader

/**
 * Landing screen: entry points to capture, gallery, jobs, and settings.
 * Also lets the user pick the reconstruction mode (single photo / multi-photo 360° / video).
 * Multi-photo 360° is the HIGH-PRECISION path; single photo is a fast generative preview.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(nav: NavHostController, vm: UploadViewModel = hiltViewModel()) {
    val mode by vm.mode.collectAsStateWithLifecycle()

    // Short explainer for the currently selected mode.
    val (selectedTitle, selectedDesc) = when (mode) {
        JobType.SINGLE_IMAGE -> R.string.capture_mode_single to R.string.home_mode_single_desc
        JobType.MULTI_IMAGE -> R.string.capture_mode_multi to R.string.home_mode_multi_desc
        else -> R.string.capture_mode_video to R.string.home_mode_video_desc
    }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Hero
            Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyLarge)

            // Mode chips
            SectionHeader(stringResource(R.string.home_mode_hint))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val options = listOf(
                    Triple(JobType.SINGLE_IMAGE, R.string.capture_mode_single, Icons.Filled.Photo),
                    Triple(JobType.MULTI_IMAGE, R.string.capture_mode_multi, Icons.Filled.PhotoLibrary),
                    Triple(JobType.VIDEO, R.string.capture_mode_video, Icons.Filled.Videocam),
                )
                options.forEach { (jt, label, icon) ->
                    FilterChip(
                        selected = mode == jt,
                        onClick = { vm.setMode(jt) },
                        label = { Text(stringResource(label)) },
                        leadingIcon = {
                            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                    )
                }
            }

            // Hero / explainer for the selected mode
            InfoCard(
                title = stringResource(selectedTitle),
                description = stringResource(selectedDesc),
                selected = true,
            )

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(stringResource(R.string.home_capture)) { nav.navigate(Routes.CAPTURE) }
                SecondaryButton(stringResource(R.string.home_gallery)) { nav.navigate(Routes.GALLERY) }
                SecondaryButton(stringResource(R.string.jobs_title)) { nav.navigate(Routes.JOBS) }
                SecondaryButton(stringResource(R.string.settings_title)) { nav.navigate(Routes.SETTINGS) }
            }
        }
    }
}
