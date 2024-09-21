package org.traccar.gateway

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
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
                val smsManager = if (slot != null) {
                    val subscriptionManager = getSystemService(SubscriptionManager::class.java)
                    val subscriptionInfo = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot)
                    SmsManager.getSmsManagerForSubscriptionId(subscriptionInfo.subscriptionId)
                } else {
                    SmsManager.getDefault()
                }
                smsManager.sendTextMessage(phone, null, message, null, null)
            } catch (e: Exception) {
                handler.post {
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

}
