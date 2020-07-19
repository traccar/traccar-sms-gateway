package com.moez.QKSMS.feature.gateway

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.preference.PreferenceManager
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.moez.QKSMS.R
import com.moez.QKSMS.common.util.NotificationManagerImpl

class GatewayService : Service(), GatewayServer.Handler {

    companion object {
        const val DEFAULT_PORT = 8082
        private const val NOTIFICATION_ID = 8722227
    }

    private lateinit var gatewayServer: GatewayServer

    private fun createNotification(context: Context): Notification {
        val intent = Intent(this, GatewayActivity::class.java)
        return NotificationCompat.Builder(context, NotificationManagerImpl.GATEWAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gateway_notify)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(PendingIntent.getActivity(context, 0, intent, 0))
            .setContentTitle(getString(R.string.gateway_service_title))
            .setContentText(getString(R.string.gateway_service_description))
            .build()
    }

    @Suppress("DEPRECATION")
    override fun onCreate() {
        val key = PreferenceManager
            .getDefaultSharedPreferences(this)
            .getString(GatewayViewModel.PREFERENCE_KEY, null)
        gatewayServer = GatewayServer(DEFAULT_PORT, key, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification(this))
        gatewayServer.start()
        return START_STICKY
    }

    override fun onDestroy() {
        gatewayServer.stop()
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
