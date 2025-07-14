package org.traccar.gateway

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.telephony.SmsManager
import android.telephony.SubscriptionManager

@Suppress("DEPRECATION")
object GatewayServiceUtil {

    fun isServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (GatewayService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    @SuppressLint("MissingPermission")
    fun sendMessage(context: Context, phone: String, message: String, slot: Int?) {
        val smsManager = if (slot != null) {
            val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
            val subscriptionInfo = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot)
            SmsManager.getSmsManagerForSubscriptionId(subscriptionInfo.subscriptionId)
        } else {
            SmsManager.getDefault()
        }
        val parts = smsManager.divideMessage(message)
        smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
    }

}
