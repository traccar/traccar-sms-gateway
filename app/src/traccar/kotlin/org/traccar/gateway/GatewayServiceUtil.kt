package org.traccar.gateway

import android.app.ActivityManager
import android.content.Context

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

}
