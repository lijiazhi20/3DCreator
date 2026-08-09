package com.tdcreator.feature.upload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.tdcreator.core.data.local.UploadStatus
import com.tdcreator.core.ui.components.ProgressRow

/**
 * Shows the live upload queue. When every item reaches DONE the user is nudged to the jobs list
 * (the 3D reconstruction is now running on the backend).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(nav: NavHostController, vm: UploadViewModel = hiltViewModel()) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    val pending = queue.count { it.status != UploadStatus.DONE && it.status != UploadStatus.FAILED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.upload_title)) },
                navigationIcon = {
                    IconButton(onClick = nav::popBackStack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (queue.isEmpty()) {
                Text(stringResource(R.string.upload_all_done))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(queue, key = { it.uid }) { item ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(item.fileName)
                                val label = when (item.status) {
                                    UploadStatus.QUEUED -> stringResource(R.string.upload_queued)
                                    UploadStatus.PRESIGNING, UploadStatus.UPLOADING -> stringResource(R.string.upload_uploading)
                                    UploadStatus.CREATING_JOB -> stringResource(R.string.upload_creating_job)
                                    UploadStatus.DONE -> stringResource(R.string.upload_done)
                                    UploadStatus.FAILED -> stringResource(R.string.upload_failed)
                                }
                                val progress = if (item.status == UploadStatus.DONE) 100 else item.progress
                                ProgressRow(label, progress)
                            }
                        }
                    }
                }
            }
            if (pending == 0 && queue.isNotEmpty()) {
                androidx.compose.material3.Button(
                    onClick = { nav.navigate(Routes.JOBS) { popUpTo(Routes.HOME) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Text(stringResource(R.string.job_open_viewer)) }
            }
        }
    }
}
