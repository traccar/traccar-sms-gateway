package org.traccar.gateway

import android.app.ActivityManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_ID = 1
    }

    private var enabled = false
    private var running = false

    private lateinit var buttonView: Button
    private lateinit var labelView: TextView
    private lateinit var textView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        buttonView = findViewById(R.id.button)
        labelView = findViewById(R.id.textViewLabel)
        textView = findViewById(R.id.textView)
    }

    override fun onResume() {
        super.onResume()

        enabled = Telephony.Sms.getDefaultSmsPackage(this) == packageName
        running = isServiceRunning()

        updateViewState()
        populateLocalAddresses()

        findViewById<Button>(R.id.button).setOnClickListener {
            if (enabled) {
                val intent = Intent(this, MainService::class.java)
                when (running) {
                    true -> stopService(intent)
                    false -> ContextCompat.startForegroundService(this, intent)
                }
                running = !running
                updateViewState()
            } else {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val roleManager = getSystemService(ROLE_SERVICE) as RoleManager
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                } else {
                    Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                }
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                startActivityForResult(intent, REQUEST_ID)
            }
        }
    }

    private fun populateLocalAddresses() {
        val addresses = StringBuilder()
        NetworkInterface.getNetworkInterfaces().toList().forEach { networkInterface ->
            networkInterface.inetAddresses.toList().forEach { address ->
                if (!address.isLoopbackAddress && address is Inet4Address) {
                    addresses.append("http:/${address}:8082\n\n")
                }
            }
        }
        textView.text = addresses
    }

    private fun updateViewState() {
        val visibility = if (enabled && running) View.VISIBLE else View.INVISIBLE
        labelView.visibility = visibility
        textView.visibility = visibility

        if (enabled) {
            buttonView.setText(
                when (running) {
                    true -> R.string.button_stop
                    false -> R.string.button_start
                }
            )
        } else {
            buttonView.setText(R.string.button_enable)
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (MainService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

}
