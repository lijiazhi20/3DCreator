package com.tdcreator.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.tdcreator.app.R
import com.tdcreator.app.navigation.Routes
import com.tdcreator.core.network.dto.JobType
import com.tdcreator.core.ui.components.PrimaryButton
import com.tdcreator.core.ui.components.SecondaryButton
import com.tdcreator.feature.upload.UploadViewModel

/**
 * CameraX capture screen — ALL capture is driven by [CameraViewModel.capture]
 * ([CameraCaptureHelper]), the single source of truth for the camera engine.
 *
 * UI adapts to the shared [mode] (from [UploadViewModel]):
 *  - MULTI_IMAGE: 360° ring guide + manual frame counter; tap to add frames (20–50),
 *    bundled into ONE high-precision job on "Next".
 *  - SINGLE_IMAGE: one shutter -> preview -> enqueue.
 *  - VIDEO: record -> preview -> enqueue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    nav: NavHostController,
    vm: CameraViewModel = viewModel(),
    uploadVm: UploadViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mode by uploadVm.mode.collectAsStateWithLifecycle()
    val frames by vm.capturedFrames.collectAsStateWithLifecycle()
    val photo by vm.capturedPhoto.collectAsStateWithLifecycle()
    val video by vm.capturedVideo.collectAsStateWithLifecycle()
    val isRecording by vm.isRecording.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    var hasPermission by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    fun bindCamera() {
        val pv = previewView ?: return
        vm.capture.bind(lifecycleOwner, pv, vm.capture.lensFacing)
    }

    fun flip() {
        val pv = previewView ?: return
        vm.capture.flipLens(lifecycleOwner, pv)
    }

    fun proceed() {
        when (mode) {
            JobType.MULTI_IMAGE -> uploadVm.enqueueMulti(frames.toList())
            else -> { val uri = (video ?: photo); if (uri != null) uploadVm.enqueue(uri) }
        }
        nav.navigate(Routes.UPLOAD) { launchSingleTop = true }
    }

    val showPreview = photo != null || video != null
    val canProceedMulti = frames.size >= 2

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) hasPermission = true else permLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.capture_title)) },
                navigationIcon = {
                    IconButton(onClick = nav::popBackStack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { flip() }) {
                        Icon(Icons.Default.Cameraswitch, contentDescription = stringResource(R.string.capture_flip))
                    }
                    IconButton(onClick = { /* flash toggle (engine supports; UI hook) */ }) {
                        Icon(Icons.Default.FlashOn, contentDescription = stringResource(R.string.capture_flash))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                if (hasPermission) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PreviewView(ctx).also { pv ->
                                previewView = pv
                                val future = ProcessCameraProvider.getInstance(ctx)
                                future.addListener({ bindCamera() }, ContextCompat.getMainExecutor(ctx))
                            }
                        },
                    )
                    // 360° ring guide + frame counter for the high-precision multi-photo path.
                    if (mode == JobType.MULTI_IMAGE && !showPreview) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.size(240.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                                Text(stringResource(R.string.camera_shots, frames.size, 50), style = MaterialTheme.typography.labelLarge)
                                Text(stringResource(R.string.camera_guide_360), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                } else {
                    Text(stringResource(R.string.permission_camera_rationale))
                }
            }

            // MULTI_IMAGE: thumbnail strip of captured frames.
            if (mode == JobType.MULTI_IMAGE && frames.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(frames, key = { it.toString() }) { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = stringResource(R.string.camera_shots, frames.indexOf(uri) + 1, 50),
                            modifier = Modifier.size(56.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                if (frames.size < 20) {
                    Text(stringResource(R.string.camera_shots_recommend), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            error?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }

            if (showPreview) {
                Text(if (video != null) stringResource(R.string.capture_use_video) else stringResource(R.string.capture_use))
                SecondaryButton(stringResource(R.string.capture_retake)) { vm.reset() }
                PrimaryButton(stringResource(R.string.common_next)) { proceed() }
            } else {
                when (mode) {
                    JobType.MULTI_IMAGE -> {
                        PrimaryButton(stringResource(R.string.capture_photo)) { vm.captureFrame(context) }
                        if (canProceedMulti) {
                            PrimaryButton(stringResource(R.string.common_next)) { proceed() }
                        }
                    }
                    JobType.VIDEO -> {
                        PrimaryButton(
                            if (isRecording) stringResource(R.string.capture_stop) else stringResource(R.string.capture_video),
                        ) { if (isRecording) vm.stopVideoCapture() else vm.startVideoCapture(context) }
                    }
                    else -> {
                        PrimaryButton(stringResource(R.string.capture_photo)) { vm.captureSingle(context) }
                    }
                }
            }
        }
    }
}
