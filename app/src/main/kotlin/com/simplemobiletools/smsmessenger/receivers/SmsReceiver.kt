package com.simplemobiletools.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.simplemobiletools.smsmessenger.extensions.getThreadId
import com.simplemobiletools.smsmessenger.extensions.insertNewSMS
import com.simplemobiletools.smsmessenger.extensions.showReceivedMessageNotification
import com.simplemobiletools.smsmessenger.helpers.refreshMessages

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        messages.forEach {
            val address = it.originatingAddress ?: ""
            val subject = it.pseudoSubject
            val body = it.messageBody
            val date = it.timestampMillis
            val threadId = context.getThreadId(address)
            val type = Telephony.Sms.MESSAGE_TYPE_INBOX
            val read = 0
            context.insertNewSMS(address, subject, body, date, read, threadId, type)
            context.showReceivedMessageNotification(address, body, threadId.toInt())
        }

        refreshMessages()
    }
}
