package com.simplemobiletools.smsmessenger.dialogs

import android.annotation.SuppressLint
import android.telephony.SubscriptionInfo
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.getTimeFormat
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.extensions.subscriptionManagerCompat
import com.simplemobiletools.smsmessenger.models.Message
import kotlinx.android.synthetic.main.dialog_message_details.view.dialog_message_details_text_value
import org.joda.time.DateTime

class MessageDetailsDialog(val activity: BaseSimpleActivity, val message: Message) {
    init {
        @SuppressLint("MissingPermission")
        val availableSIMs = activity.subscriptionManagerCompat().activeSubscriptionInfoList

        @SuppressLint("SetTextI18n")
        val view = activity.layoutInflater.inflate(R.layout.dialog_message_details, null).apply {
            dialog_message_details_text_value.text = mutableListOf<String>().apply {
                add("${message.getReceiverOrSenderLabel()}: ${message.getReceiverOrSenderPhoneNumbers()}")
                if (availableSIMs.count() > 1) {
                    add("SIM: ${message.getSIM(availableSIMs)}")
                }
                add("${message.getSentOrReceivedAtLabel()}: ${message.getSentOrReceivedAt()}")
            }.joinToString(separator = System.lineSeparator())
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok) { _, _ -> }
            .apply {
                activity.setupDialogStuff(view, this, R.string.message_details)
            }
    }

    private fun Message.getReceiverOrSenderLabel(): String {
        return if (isReceivedMessage()) {
            activity.getString(R.string.message_details_sender)
        } else {
            activity.getString(R.string.message_details_receiver)
        }
    }

    private fun Message.getReceiverOrSenderPhoneNumbers(): String {
        return if (isReceivedMessage()) {
            formatContactInfo(senderName, senderPhoneNumber)
        } else {
            participants.joinToString(", ") {
                formatContactInfo(it.name, it.phoneNumbers.first().value)
            }
        }
    }

    private fun formatContactInfo(name: String, phoneNumber: String): String {
        return if (name != phoneNumber) {
            "$name ($phoneNumber)"
        } else {
            phoneNumber
        }
    }

    private fun Message.getSIM(availableSIMs: List<SubscriptionInfo>): String {
        return availableSIMs.firstOrNull { it.subscriptionId == subscriptionId }?.displayName?.toString() ?: activity.getString(R.string.unknown)
    }

    private fun Message.getSentOrReceivedAtLabel(): String {
        return if (isReceivedMessage()) {
            activity.getString(R.string.message_details_received_at)
        } else {
            activity.getString(R.string.message_details_sent_at)
        }
    }

    private fun Message.getSentOrReceivedAt(): String {
        return DateTime(date * 1000L).toString("${activity.config.dateFormat} ${activity.getTimeFormat()}")
    }
}
