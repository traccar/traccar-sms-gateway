package org.traccar.gateway

import android.annotation.TargetApi
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class MainApplication : Application() {

    companion object {
        const val PRIMARY_CHANNEL = "default"
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerChannel()
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun registerChannel() {
        val channel = NotificationChannel(PRIMARY_CHANNEL, getString(R.string.channel_default), NotificationManager.IMPORTANCE_MIN)
        channel.lockscreenVisibility = Notification.VISIBILITY_SECRET
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

}
