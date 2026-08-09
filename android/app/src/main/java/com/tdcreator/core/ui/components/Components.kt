package com.tdcreator.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tdcreator.app.R
import com.tdcreator.core.network.dto.JobStatus

/** Full-screen centered progress indicator with an optional message. */
@Composable
fun FullScreenLoading(message: String? = null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            message?.let { Text(it) }
        }
    }
}

/** Generic error panel with a retry action. */
@Composable
fun ErrorPanel(message: String, onRetry: (() -> Unit)? = null) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, style = MaterialTheme.typography.bodyLarge)
            onRetry?.let {
                Button(onClick = it) { Text(stringResource(R.string.common_retry)) }
            }
        }
    }
}

/** A horizontal progress row used by the upload queue. */
@Composable
fun ProgressRow(label: String, progress: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        LinearProgressIndicator(
            progress = { progress.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/** Localized chip colour/label for a job status. */
@Composable
fun JobStatusLabel(status: JobStatus) {
    val text = when (status) {
        JobStatus.QUEUED -> stringResource(R.string.job_status_queued)
        JobStatus.RUNNING -> stringResource(R.string.job_status_running)
        JobStatus.SUCCEEDED -> stringResource(R.string.job_status_succeeded)
        JobStatus.FAILED -> stringResource(R.string.job_status_failed)
        JobStatus.CANCELLED -> stringResource(R.string.job_status_cancelled)
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** Primary action button used across screens. */
@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(text)
    }
}

/** Secondary / outline button. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text)
    }
}

/** Small section heading used to group controls on a screen. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

/**
 * A tonal card that explains one option (used on Home to describe each reconstruction mode).
 * `onClick` makes the whole card selectable.
 */
@Composable
fun InfoCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val onContainer = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = container,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (selected) 2.dp else 0.dp,
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = onContainer)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = onContainer)
        }
    }
}

/**
 * Placeholder thumbnail for a 3D model when the repository does not (yet) expose a
 * preview image URL. Keeps the list layout stable; swap for AsyncImage once a URL is available.
 */
@Composable
fun ThumbnailPlaceholder(contentDescription: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.ViewInAr,
                contentDescription = contentDescription,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}
