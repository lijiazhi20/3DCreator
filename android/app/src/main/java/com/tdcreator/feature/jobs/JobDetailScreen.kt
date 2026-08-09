package com.tdcreator.feature.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.tdcreator.app.R
import com.tdcreator.app.navigation.Routes
import com.tdcreator.core.network.dto.JobType
import com.tdcreator.core.ui.components.JobStatusLabel
import com.tdcreator.core.ui.components.PrimaryButton
import com.tdcreator.core.ui.components.ProgressRow
import com.tdcreator.core.ui.components.ThumbnailPlaceholder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Job detail: status, progress, tier/type, credits, timestamps. Polls periodically while the
 * job is still active (queued/running). On success, offers the 3D viewer and share.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(nav: NavHostController, jobId: String, vm: JobDetailViewModel = hiltViewModel()) {
    val job by vm.job.collectAsStateWithLifecycle()

    LaunchedEffect(jobId) {
        vm.load(jobId)
        vm.poll(jobId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.job_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = nav::popBackStack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.poll(jobId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.job_refresh))
                    }
                },
            )
        },
    ) { padding ->
        job?.let { j ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ThumbnailPlaceholder(
                        contentDescription = stringResource(R.string.job_thumbnail),
                        modifier = Modifier.size(72.dp),
                    )
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        androidx.compose.foundation.layout.Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) { Text(j.id); JobStatusLabel(j.status) }
                        Text(stringResource(typeLabel(j.jobType)))
                        Text(stringResource(tierLabel(j.tier)))
                    }
                }
                ProgressRow(stringResource(R.string.job_progress, j.progress), j.progress)
                Text(stringResource(R.string.job_credits, j.creditsCharged))
                Text(stringResource(R.string.job_created, format(j.createdAt)))
                Text(stringResource(R.string.job_updated, format(j.updatedAt)))
                if (j.status == com.tdcreator.core.network.dto.JobStatus.FAILED) {
                    Text(stringResource(R.string.job_status_failed))
                }
                if (j.status == com.tdcreator.core.network.dto.JobStatus.SUCCEEDED) {
                    PrimaryButton(stringResource(R.string.job_open_viewer)) { nav.navigate(Routes.viewer(j.id)) }
                    PrimaryButton(stringResource(R.string.job_open_share)) { nav.navigate(Routes.share(j.id)) }
                }
            }
        } ?: com.tdcreator.core.ui.components.FullScreenLoading()
    }
}

private fun typeLabel(jt: JobType): Int = when (jt) {
    JobType.SINGLE_IMAGE, JobType.IMAGE_TO_3D -> R.string.job_type_single
    JobType.MULTI_IMAGE -> R.string.job_type_multi
    else -> R.string.job_type_video
}

private fun tierLabel(tier: com.tdcreator.core.network.dto.JobTier): Int = when (tier) {
    com.tdcreator.core.network.dto.JobTier.PREVIEW -> R.string.job_tier_preview
    com.tdcreator.core.network.dto.JobTier.STANDARD -> R.string.job_tier_standard
    com.tdcreator.core.network.dto.JobTier.HIGH -> R.string.job_tier_high
}

private fun format(iso: String): String = runCatching {
    // Backend uses ISO-8601 with 'Z' / offset; normalize for display.
    val s = iso.replace("Z", "+0000")
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ", Locale.US)
    val d: Date = parser.parse(s) ?: return@runCatching iso
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(d)
}.getOrDefault(iso)
