package com.tdcreator.core.network

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer
import java.io.IOException

/**
 * Wraps an existing [RequestBody] and reports upload progress (0–100) as bytes are written to
 * the network sink. Used by [com.tdcreator.core.data.repository.UploadRepository.localUpload]
 * so the WorkManager upload chain can persist real progress into Room and surface it in the UI.
 */
class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (percent: Int) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun isOneShot(): Boolean = delegate.isOneShot()

    override fun writeTo(sink: BufferedSink) {
        val countingSink = CountingSink(sink)
        val bufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }

    private inner class CountingSink(delegate: Sink) : ForwardingSink(delegate) {
        private var sent = 0L

        @Throws(IOException::class)
        override fun write(source: Buffer, byteCount: Long) {
            super.write(source, byteCount)
            sent += byteCount
            val total = contentLength()
            if (total > 0) {
                onProgress(((sent * 100) / total).toInt().coerceIn(0, 100))
            }
        }
    }
}
