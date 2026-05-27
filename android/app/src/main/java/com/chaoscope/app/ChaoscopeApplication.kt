package com.chaoscope

import android.app.Application
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Application subclass used to create notification channels at startup.
 * Must be declared in AndroidManifest.xml via android:name=".ChaoscopeApplication".
 */
class ChaoscopeApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LangPrefs.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        // Video export progress channel — LOW importance so it doesn't make a sound
        val exportChannel = NotificationChannelCompat
            .Builder(VideoExportService.CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(getString(R.string.notif_channel_name))
            .setDescription(getString(R.string.notif_channel_desc))
            .build()

        NotificationManagerCompat.from(this).createNotificationChannel(exportChannel)
    }
}
