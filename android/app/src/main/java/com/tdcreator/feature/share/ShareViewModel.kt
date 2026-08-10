package com.tdcreator.feature.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tdcreator.app.R
import com.tdcreator.core.network.ApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Downloads the finished GLB (the backend streams the file at `GET /jobs/{id}/download`)
 * and shares it through a FileProvider-backed ACTION_SEND chooser.
 */
@HiltViewModel
class ShareViewModel @Inject constructor(
    private val api: ApiService,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    fun share(context: Context, jobId: String) {
        viewModelScope.launch {
            _busy.value = true
            runCatching {
                val bytes = api.downloadResult(jobId).bytes()
                val file = saveBytes(context, bytes, jobId)
                val uri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    file,
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "model/gltf-binary"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_via)))
            }
            _busy.value = false
        }
    }

    private suspend fun saveBytes(context: Context, bytes: ByteArray, jobId: String): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "share").also { it.mkdirs() }
            val file = File(dir, "model_$jobId.glb")
            file.writeBytes(bytes)
            file
        }
}
