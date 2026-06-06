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
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ActivityGatewayBinding
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*

class GatewayActivity : SimpleActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    private val binding by viewBinding(ActivityGatewayBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.gatewayNestedScrollview))
        setupMaterialScrollListener(scrollingView = binding.gatewayNestedScrollview, topAppBar = binding.gatewayAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.gatewayAppbar, NavigationIcon.Arrow)

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            binding.gatewayCloudKey.text = task.result
        }

        binding.gatewayLocalKey.text = getKey()
        binding.gatewayLocalEndpoints.text = getAddressList().joinToString("\n")

        binding.gatewayLocalEnable.isChecked = GatewayServiceUtil.isServiceRunning(this)
        binding.gatewayLocalEnableHolder.setOnClickListener {
            val intent = Intent(this, GatewayService::class.java)
            val running = GatewayServiceUtil.isServiceRunning(this)
            if (running) {
                stopService(intent)
            } else {
                ContextCompat.startForegroundService(this, intent)
            }
            binding.gatewayLocalEnable.isChecked = !running
        }

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager?

        binding.gatewayCloudKeyHolder.setOnClickListener {
            clipboard?.text = binding.gatewayCloudKey.text
            Toast.makeText(this, R.string.gateway_copied_toast, Toast.LENGTH_SHORT).show()
        }
        binding.gatewayLocalKeyHolder.setOnClickListener {
            clipboard?.text = binding.gatewayLocalKey.text
            Toast.makeText(this, R.string.gateway_copied_toast, Toast.LENGTH_SHORT).show()
        }
        binding.gatewayLocalEndpointsHolder.setOnClickListener {
            clipboard?.text = binding.gatewayLocalEndpoints.text
            Toast.makeText(this, R.string.gateway_copied_toast, Toast.LENGTH_SHORT).show()
        }

        updateTextColors(binding.gatewayNestedScrollview)
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
