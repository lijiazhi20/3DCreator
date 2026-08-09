package com.tdcreator.feature.jobs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.tdcreator.app.R
import com.tdcreator.app.navigation.Routes
import com.tdcreator.core.data.local.JobEntity
import com.tdcreator.core.network.dto.JobType
import com.tdcreator.core.ui.components.FullScreenLoading
import com.tdcreator.core.ui.components.JobStatusLabel
import com.tdcreator.core.ui.components.ProgressRow
import com.tdcreator.core.ui.components.ThumbnailPlaceholder
import kotlinx.coroutines.launch

/**
 * Job list with pull-to-refresh semantics (refresh button). Each row opens the detail screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsScreen(nav: NavHostController, vm: JobsViewModel = hiltViewModel()) {
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.jobs_title)) },
                navigationIcon = {
                    IconButton(onClick = nav::popBackStack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { vm.refresh() } }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.job_refresh))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading && jobs.isEmpty()) {
                FullScreenLoading(stringResource(R.string.common_loading))
            } else if (jobs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.job_empty), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(jobs, key = { it.id }) { job ->
                        JobRow(job) { nav.navigate(Routes.jobDetail(job.id)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobRow(job: JobEntity, onClick: () -> Unit) {
    val typeLabel = stringResource(
        when (job.jobType) {
            JobType.SINGLE_IMAGE, JobType.IMAGE_TO_3D -> R.string.job_type_single
            JobType.MULTI_IMAGE -> R.string.job_type_multi
            else -> R.string.job_type_video
        },
    )
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ThumbnailPlaceholder(
                contentDescription = stringResource(R.string.job_thumbnail),
                modifier = Modifier.size(56.dp),
            )
            Column(
                Modifier.fillMaxWidth().padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(job.id.take(8), style = MaterialTheme.typography.titleMedium)
                    JobStatusLabel(job.status)
                }
                Text(typeLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ProgressRow(stringResource(R.string.job_progress, job.progress), job.progress)
            }
        }
    }
}
