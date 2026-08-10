package com.tdcreator.feature.gallery

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.tdcreator.app.R
import com.tdcreator.app.navigation.Routes
import com.tdcreator.core.network.dto.JobType
import com.tdcreator.core.ui.components.PrimaryButton
import com.tdcreator.core.ui.components.SectionHeader
import com.tdcreator.feature.upload.UploadViewModel

/**
 * Photo Picker (Android 13+) multi-select. Avoids READ_EXTERNAL_STORAGE. Selected items are
 * enqueued into the upload queue and the user is sent to the upload progress screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(nav: NavHostController, vm: UploadViewModel = hiltViewModel()) {
    var selected by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val queueSummary by vm.queueSummary.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult<PickVisualMediaRequest, List<Uri>>(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> selected = uris ?: emptyList() }

    val multiMin = 2
    val canProceed = if (mode == JobType.MULTI_IMAGE) selected.size >= multiMin else selected.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gallery_title)) },
                navigationIcon = {
                    IconButton(onClick = nav::popBackStack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(stringResource(R.string.gallery_pick)) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            }

            SectionHeader(stringResource(R.string.gallery_selected, selected.size))

            if (mode == JobType.MULTI_IMAGE) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.gallery_multi_hint),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (selected.isEmpty()) {
                Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.gallery_empty), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(selected, key = { it.toString() }) { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = stringResource(R.string.gallery_selected, selected.indexOf(uri) + 1),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = selected - uri },
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                Text(
                    stringResource(R.string.gallery_remove),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PrimaryButton(
                text = stringResource(R.string.gallery_next),
                enabled = canProceed,
            ) {
                if (mode == JobType.MULTI_IMAGE) {
                    // HIGH-PRECISION 360° path: bundle all photos into ONE multi_image job.
                    vm.enqueueMulti(selected)
                } else {
                    // Single photo / video: each selection becomes its own job.
                    selected.forEach { vm.enqueue(it) }
                }
                nav.navigate(Routes.UPLOAD) { launchSingleTop = true }
            }
        }
    }
}
