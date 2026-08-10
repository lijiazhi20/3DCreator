package com.tdcreator.feature.viewer

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.tdcreator.app.R
import com.tdcreator.core.ui.components.ErrorPanel
import com.tdcreator.core.ui.components.FullScreenLoading
import java.net.URLEncoder

/**
 * Hosts the Three.js GLB viewer in a WebView. A Filament-native viewer can replace this later;
 * the rest of the app only depends on the `modelUrl` contract.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(nav: NavHostController, jobId: String, vm: ViewerViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val webView = remember { androidx.compose.runtime.mutableStateOf<WebView?>(null) }

    LaunchedEffect(jobId) { vm.load(jobId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.viewer_title)) },
                navigationIcon = {
                    IconButton(onClick = nav::popBackStack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load(jobId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.viewer_retry))
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            is ViewerViewModel.ViewerState.Loading -> FullScreenLoading(stringResource(R.string.viewer_loading))
            is ViewerViewModel.ViewerState.Error -> ErrorPanel(stringResource(R.string.viewer_error)) { vm.load(jobId) }
            is ViewerViewModel.ViewerState.Ready -> {
                AndroidView(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            webViewClient = WebViewClient()
                            loadUrl("file:///android_asset/glb_viewer.html?model=" + URLEncoder.encode(s.modelUrl, "UTF-8"))
                        }.also { webView.value = it }
                    },
                )
            }
        }
    }
}
