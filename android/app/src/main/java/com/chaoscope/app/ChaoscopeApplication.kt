package com.chaoscope

import android.app.Application
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Application subclass used to create notification channels at startup.
 * Must be declared in AndroidManifest.xml via android:name=".ChaoscopeApplication".
 */
class ChaoscopeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        // Video export progress channel — LOW importance so it doesn't make a sound
        val exportChannel = NotificationChannelCompat
            .Builder(VideoExportService.CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("Video Export")
            .setDescription("Shows progress while exporting an animation to MP4.")
            .build()

        NotificationManagerCompat.from(this).createNotificationChannel(exportChannel)
    }
}
