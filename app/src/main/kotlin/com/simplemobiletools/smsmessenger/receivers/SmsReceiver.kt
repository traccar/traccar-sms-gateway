package com.simplemobiletools.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.simplemobiletools.smsmessenger.extensions.getThreadId
import com.simplemobiletools.smsmessenger.extensions.insertNewSMS
import com.simplemobiletools.smsmessenger.extensions.isNumberBlocked
import com.simplemobiletools.smsmessenger.extensions.showReceivedMessageNotification
import com.simplemobiletools.smsmessenger.helpers.refreshMessages

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        var address = ""
        var body = ""
        var subject = ""
        var date = 0L
        var threadId = 0L
        val type = Telephony.Sms.MESSAGE_TYPE_INBOX
        val read = 0
        val subscriptionId = intent.getIntExtra("subscription", -1)

        messages.forEach {
            address = it.originatingAddress ?: ""
            subject = it.pseudoSubject
            body += it.messageBody
            date = Math.min(it.timestampMillis, System.currentTimeMillis())
            threadId = context.getThreadId(address)
        }

        if (!context.isNumberBlocked(address)) {
            context.insertNewSMS(address, subject, body, date, read, threadId, type, subscriptionId)
            context.showReceivedMessageNotification(address, body, threadId.toInt())
            refreshMessages()
        }
    }
}
