package com.simplemobiletools.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import com.simplemobiletools.commons.extensions.isNumberBlocked
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.*
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

        ensureBackgroundThread {
            messages.forEach {
                address = it.originatingAddress ?: ""
                subject = it.pseudoSubject
                body += it.messageBody
                date = Math.min(it.timestampMillis, System.currentTimeMillis())
                threadId = context.getThreadId(address)
            }

            Handler(Looper.getMainLooper()).post {
                if (!context.isNumberBlocked(address)) {
                    ensureBackgroundThread {
                        context.insertNewSMS(address, subject, body, date, read, threadId, type, subscriptionId)

                        val conversation = context.getConversations(threadId).firstOrNull() ?: return@ensureBackgroundThread
                        context.conversationsDB.insertOrUpdate(conversation)
                        context.updateUnreadCountBadge(context.conversationsDB.getUnreadConversations())
                    }

                    context.showReceivedMessageNotification(address, body, threadId.toInt(), null)
                    refreshMessages()
                }
            }
        }
    }
}
