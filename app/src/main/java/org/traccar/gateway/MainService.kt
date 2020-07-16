package org.traccar.gateway

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.app.NotificationCompat

class MainService : Service(), WebServer.Handler {

    companion object {
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var webServer: WebServer

    private fun createNotification(context: Context): Notification {
        val intent = Intent(this, MainActivity::class.java)
        return NotificationCompat.Builder(context, MainApplication.PRIMARY_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(PendingIntent.getActivity(context, 0, intent, 0))
            .setContentTitle(getString(R.string.service_title))
            .setContentText(getString(R.string.service_description))
            .build()
    }

    override fun onCreate() {
        webServer = WebServer(resources.getInteger(R.integer.server_port), this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification(this))
        webServer.start()
        return START_STICKY
    }

    override fun onDestroy() {
        webServer.stop()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onSendMessage(phone: String, message: String): String? {
        return try {
            SmsManager.getDefault().sendTextMessage(phone, null, message, null, null)
            null
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            e.message
        }
    }

}
