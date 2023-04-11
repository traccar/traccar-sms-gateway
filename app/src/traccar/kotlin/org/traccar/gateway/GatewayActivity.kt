@file:Suppress("DEPRECATION")

package org.traccar.gateway

import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.helpers.*
import kotlinx.android.synthetic.traccar.activity_gateway.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*

class GatewayActivity : SimpleActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gateway)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        updateMaterialActivityViews(gateway_coordinator, gateway_holder, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(gateway_nested_scrollview, gateway_toolbar)
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(gateway_toolbar, NavigationIcon.Arrow)

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                gateway_cloud_key.text = task.result
            }
        }

        gateway_local_key.text = getKey()
        gateway_local_endpoints.text = getAddressList().joinToString("\n")

        gateway_local_enable.isChecked = GatewayServiceUtil.isServiceRunning(this)
        gateway_local_enable_holder.setOnClickListener {
            val intent = Intent(this, GatewayService::class.java)
            val running = GatewayServiceUtil.isServiceRunning(this)
            if (running) {
                stopService(intent)
            } else {
                ContextCompat.startForegroundService(this, intent)
            }
            gateway_local_enable.isChecked = !running
        }

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager?

        gateway_cloud_key_holder.setOnClickListener {
            clipboard?.text = gateway_cloud_key.text
            Toast.makeText(this, R.string.gateway_copied_toast, Toast.LENGTH_SHORT).show()
        }
        gateway_local_key_holder.setOnClickListener {
            clipboard?.text = gateway_local_key.text
            Toast.makeText(this, R.string.gateway_copied_toast, Toast.LENGTH_SHORT).show()
        }
        gateway_local_endpoints_holder.setOnClickListener {
            clipboard?.text = gateway_local_endpoints.text
            Toast.makeText(this, R.string.gateway_copied_toast, Toast.LENGTH_SHORT).show()
        }

        updateTextColors(gateway_nested_scrollview)
    }

    private fun getKey(): String {
        var key = sharedPreferences.getString(GatewayService.PREFERENCE_KEY, null)
        if (key == null) {
            key = UUID.randomUUID().toString()
            sharedPreferences.edit().putString(GatewayService.PREFERENCE_KEY, key).apply()
        }
        return key
    }

    private fun getAddressList(): List<String> {
        val result = mutableListOf<String>()
        NetworkInterface.getNetworkInterfaces().toList().forEach { networkInterface ->
            networkInterface.inetAddresses.toList().forEach { address ->
                if (!address.isLoopbackAddress && address is Inet4Address) {
                    result.add("http:/${address}:${GatewayService.DEFAULT_PORT}")
                }
            }
        }
        return result
    }
}
