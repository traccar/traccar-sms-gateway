package com.simplemobiletools.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import com.simplemobiletools.commons.extensions.getMyContactsCursor
import com.simplemobiletools.commons.extensions.isNumberBlocked
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.refreshMessages
import com.simplemobiletools.smsmessenger.models.Message

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        var address = ""
        var body = ""
        var subject = ""
        var date = 0L
        var threadId = 0L
        var status = Telephony.Sms.STATUS_NONE
        val type = Telephony.Sms.MESSAGE_TYPE_INBOX
        val read = 0
        val subscriptionId = intent.getIntExtra("subscription", -1)

        ensureBackgroundThread {
            messages.forEach {
                address = it.originatingAddress ?: ""
                subject = it.pseudoSubject
                status = it.status
                body += it.messageBody
                date = Math.min(it.timestampMillis, System.currentTimeMillis())
                threadId = context.getThreadId(address)
            }

            Handler(Looper.getMainLooper()).post {
                val privateCursor = context.getMyContactsCursor(false, true)?.loadInBackground()
                if (!context.isNumberBlocked(address)) {
                    ensureBackgroundThread {
                        val newMessageId = context.insertNewSMS(address, subject, body, date, read, threadId, type, subscriptionId)

                        val conversation = context.getConversations(threadId).firstOrNull() ?: return@ensureBackgroundThread
                        try {
                            context.conversationsDB.insertOrUpdate(conversation)
                        } catch (ignored: Exception) {
                        }

                        try {
                            context.updateUnreadCountBadge(context.conversationsDB.getUnreadConversations())
                        } catch (ignored: Exception) {
                        }

                        val senderName = context.getNameFromAddress(address, privateCursor)
                        val participant = SimpleContact(0, 0, senderName, "", arrayListOf(address), ArrayList(), ArrayList())
                        val participants = arrayListOf(participant)
                        val messageDate = (date / 1000).toInt()

                        val message =
                            Message(newMessageId, body, type, status, participants, messageDate, false, threadId, false, null, address, "", subscriptionId)
                        context.messagesDB.insertOrUpdate(message)
                        refreshMessages()
                    }

                    context.showReceivedMessageNotification(address, body, threadId, null)
                }
            }
        }
    }
}
