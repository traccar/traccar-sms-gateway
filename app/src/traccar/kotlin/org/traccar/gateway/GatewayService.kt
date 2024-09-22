@file:Suppress("DEPRECATION")

package org.traccar.gateway

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.simplemobiletools.smsmessenger.R

class GatewayService : Service(), GatewayServer.Handler {

    companion object {
        const val PREFERENCE_KEY = "gateway_api_key"
        const val DEFAULT_PORT = 8082
        private const val NOTIFICATION_ID = 8722227
        const val GATEWAY_CHANNEL_ID = "notifications_gateway"
    }

    private lateinit var gatewayServer: GatewayServer

    private fun createNotification(context: Context): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                GATEWAY_CHANNEL_ID,
                context.getString(R.string.gateway_channel),
                NotificationManager.IMPORTANCE_MIN
            )
            channel.lockscreenVisibility = Notification.VISIBILITY_SECRET
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, GatewayActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)

        return NotificationCompat.Builder(context, GATEWAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_messenger)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentTitle(getString(R.string.gateway_service_title))
            .setContentText(getString(R.string.gateway_service_description))
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onCreate() {
        val key = PreferenceManager
            .getDefaultSharedPreferences(this)
            .getString(PREFERENCE_KEY, null)
        gatewayServer = GatewayServer(DEFAULT_PORT, key, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } else {
            startForeground(NOTIFICATION_ID, createNotification(this))
        }
        gatewayServer.start()
        return START_STICKY
    }

    override fun onDestroy() {
        gatewayServer.stop()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onSendMessage(phone: String, message: String, slot: Int?): String? {
        return try {
            GatewayServiceUtil.sendMessage(this, phone, message, slot)
            null
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            e.message
        }
    }

}
