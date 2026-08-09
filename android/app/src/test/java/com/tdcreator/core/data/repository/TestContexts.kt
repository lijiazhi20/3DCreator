package com.tdcreator.core.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Builds an Android [Context] suitable for unit tests: a real Robolectric
 * Application context (so [Context.getCacheDir] works) whose [ContentResolver]
 * is a Mockito mock serving a tiny PNG for any Uri. Used by the repository /
 * view-model unit tests so no real files or framework wiring are required.
 */
object TestContexts {
    private val PNG = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    fun make(): Context {
        val appCtx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val cr = mock<ContentResolver>()
        whenever(cr.getType(any())).thenReturn("image/jpeg")
        whenever(cr.openInputStream(any())).thenAnswer { ByteArrayInputStream(PNG) }
        whenever(cr.openOutputStream(any())).thenAnswer { FileOutputStream(it.arguments[0] as File) }
        return object : ContextWrapper(appCtx) {
            override fun getContentResolver(): ContentResolver = cr
        }
    }
}
