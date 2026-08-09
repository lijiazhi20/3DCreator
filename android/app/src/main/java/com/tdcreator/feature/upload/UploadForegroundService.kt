package com.tdcreator.feature.upload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tdcreator.app.R
import com.tdcreator.core.data.local.UploadDao
import com.tdcreator.core.data.local.UploadStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thin foreground service that hosts the upload WorkManager chain. On Android 14+ the service
 * must declare `foregroundServiceType="dataSync"` (see AndroidManifest). The actual upload work
 * is performed by [UploadWorker]; this service simply keeps the process foregrounded while the
 * work queue is non-empty and stops itself once every item reaches DONE/FAILED — the mitigation
 * against aggressive OEM background-kill policies (e.g. Honor/MagicOS).
 */
@AndroidEntryPoint
class UploadForegroundService : android.app.Service() {

    @Inject lateinit var uploadDao: UploadDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        monitorQueue()
    }

    /** Stop the service automatically when no upload item is still in flight. */
    private fun monitorQueue() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            uploadDao.observeQueue().collectLatest { items ->
                val active = items.any { it.status != UploadStatus.DONE && it.status != UploadStatus.FAILED }
                if (!active) stopSelf()
            }
        }
    }

    override fun onBind(intent: android.content.Intent?): IBinder? = null

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: if the system kills us, restart and re-foreground so uploads resume.
        return android.app.Service.START_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val channelId = CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(
                NotificationChannel(channelId, getString(R.string.core_upload_channel_name), NotificationManager.IMPORTANCE_LOW),
            )
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.upload_uploading))
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val NOTIF_ID = 1002
        const val CHANNEL_ID = "upload_channel"
    }
}
