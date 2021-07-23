package com.moez.QKSMS.feature.gateway

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.widget.Toast
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class GatewayMessagingService : FirebaseMessagingService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val phone = remoteMessage.data["phone"]
        val message = remoteMessage.data["message"]
        if (phone != null && message != null) {
            try {
                SmsManager.getDefault().sendTextMessage(phone, null, message, null, null)
            } catch (e: Exception) {
                handler.post {
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

}
