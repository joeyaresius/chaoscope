package com.chaoscope

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.chaoscope.R

// ────────────────────────────────────────────────────────────────────────────
// Shared export status — observed by the ViewModel (updates) and the Service
// (notification).  Lives in memory; reset to Idle when the app restarts.
// ────────────────────────────────────────────────────────────────────────────

sealed class ExportStatus {
    object Idle : ExportStatus()
    data class Running(val done: Int, val total: Int) : ExportStatus()
    data class Done(val uri: String) : ExportStatus()
    data class Error(val msg: String) : ExportStatus()
}

object VideoExportState {
    val status: MutableStateFlow<ExportStatus> = MutableStateFlow(ExportStatus.Idle)

    /**
     * Set by [ChaoscopeViewModel] before each export; invoked by the service when
     * the user taps Cancel on the notification.  Cleared after use.
     *
     * @Volatile guarantees the latest write is visible across threads (the service
     * runs on the main thread; the ViewModel coroutine on Dispatchers.Default).
     */
    @Volatile var onCancelRequested: (() -> Unit)? = null
}

// ────────────────────────────────────────────────────────────────────────────
// ForegroundService — keeps the process alive during video export so Android
// does not kill it when the app is backgrounded.
//
// The actual encoding runs in viewModelScope (ChaoscopeViewModel.exportVideo).
// This service exists solely to hold a foreground notification and protect the
// process from the out-of-memory killer.
// ────────────────────────────────────────────────────────────────────────────

class VideoExportService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Invoke the ViewModel's cancel function (cancels the encode coroutine),
            // then clear the callback to avoid leaking the ViewModel reference.
            VideoExportState.onCancelRequested?.invoke()
            VideoExportState.onCancelRequested = null
            VideoExportState.status.value = ExportStatus.Idle
            stopSelf()
            return START_NOT_STICKY
        }

        // Put ourselves in the foreground immediately to avoid ANR
        startForeground(NOTIF_ID, buildNotification(0, 0))

        // Watch the shared status and keep the notification in sync; stop when done
        scope.launch {
            VideoExportState.status.collect { s ->
                when (s) {
                    is ExportStatus.Running -> updateNotification(s.done, s.total)
                    is ExportStatus.Done,
                    is ExportStatus.Error,
                    is ExportStatus.Idle   -> stopSelf()
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ── Notification helpers ──────────────────────────────────────────────────

    private fun buildNotification(done: Int, total: Int): Notification {
        val cancelIntent = Intent(this, VideoExportService::class.java).apply {
            action = ACTION_STOP
        }
        val cancelPi = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.notif_export_title))
            .setContentText(
                if (total > 0) getString(R.string.notif_export_progress, done, total)
                else getString(R.string.notif_export_preparing)
            )
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_delete, getString(R.string.notif_export_cancel), cancelPi)
            .build()
    }

    private fun updateNotification(done: Int, total: Int) {
        runCatching {
            NotificationManagerCompat.from(this)
                .notify(NOTIF_ID, buildNotification(done, total))
        }
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        const val ACTION_STOP = "com.chaoscope.STOP_VIDEO_EXPORT"
        const val NOTIF_ID    = 1001
        const val CHANNEL_ID  = "video_export"

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, VideoExportService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VideoExportService::class.java))
        }
    }
}
