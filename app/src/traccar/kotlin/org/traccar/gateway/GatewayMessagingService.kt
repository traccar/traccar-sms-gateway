package org.traccar.gateway

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class GatewayMessagingService : FirebaseMessagingService() {

    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("MissingPermission")
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val phone = remoteMessage.data["phone"]
        val message = remoteMessage.data["message"]
        val slot = remoteMessage.data["slot"]?.toInt()
        if (phone != null && message != null) {
            try {
                GatewayServiceUtil.sendMessage(this, phone, message, slot)
            } catch (e: Exception) {
                handler.post {
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

}
