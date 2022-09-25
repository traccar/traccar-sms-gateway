package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import com.klinker.android.send_message.Utils
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.receivers.SmsStatusDeliveredReceiver
import com.simplemobiletools.smsmessenger.receivers.SmsStatusSentReceiver

fun Context.getSendMessageSettings(): Settings {
    val settings = Settings()
    settings.useSystemSending = true
    settings.deliveryReports = config.enableDeliveryReports
    settings.sendLongAsMms = config.sendLongMessageMMS
    settings.sendLongAsMmsAfter = 1
    settings.group = config.sendGroupMessageMMS
    return settings
}

fun Context.sendTransactionMessage(text: String, addresses: List<String>, subscriptionId: Int?, attachments: List<Uri>) {
    val settings = getSendMessageSettings()
    if (subscriptionId != null) {
        settings.subscriptionId = subscriptionId
    }

    val transaction = Transaction(this, settings)
    val message = com.klinker.android.send_message.Message(text, addresses.toTypedArray())

    if (attachments.isNotEmpty()) {
        for (uri in attachments) {
            try {
                val byteArray = contentResolver.openInputStream(uri)?.readBytes() ?: continue
                val mimeType = contentResolver.getType(uri) ?: continue
                message.addMedia(byteArray, mimeType)
            } catch (e: Exception) {
                showErrorToast(e)
            } catch (e: Error) {
                showErrorToast(e.localizedMessage ?: getString(R.string.unknown_error_occurred))
            }
        }
    }

    val smsSentIntent = Intent(this, SmsStatusSentReceiver::class.java)
    val deliveredIntent = Intent(this, SmsStatusDeliveredReceiver::class.java)

    transaction.setExplicitBroadcastForSentSms(smsSentIntent)
    transaction.setExplicitBroadcastForDeliveredSms(deliveredIntent)
    transaction.sendNewMessage(message)
}

fun Context.isLongMmsMessage(text: String): Boolean {
    val settings = getSendMessageSettings()
    return Utils.getNumPages(settings, text) > settings.sendLongAsMmsAfter
}
