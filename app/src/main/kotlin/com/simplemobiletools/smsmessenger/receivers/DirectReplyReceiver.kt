package com.simplemobiletools.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import com.simplemobiletools.commons.extensions.notificationManager
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.extensions.conversationsDB
import com.simplemobiletools.smsmessenger.extensions.markThreadMessagesRead
import com.simplemobiletools.smsmessenger.helpers.NOTIFICATION_CHANNEL
import com.simplemobiletools.smsmessenger.helpers.REPLY
import com.simplemobiletools.smsmessenger.helpers.THREAD_ID
import com.simplemobiletools.smsmessenger.helpers.THREAD_NUMBER

class DirectReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra(THREAD_NUMBER)
        val threadId = intent.getIntExtra(THREAD_ID, 0)
        val msg = RemoteInput.getResultsFromIntent(intent).getCharSequence(REPLY).toString()

        val settings = Settings()
        settings.useSystemSending = true

        val transaction = Transaction(context, settings)
        val message = com.klinker.android.send_message.Message(msg, address)

        try {
            transaction.sendNewMessage(message, threadId.toLong())
        } catch (e: Exception) {
            context.showErrorToast(e)
        }

        val repliedNotification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_messenger)
            .setContentText(msg)
            .build()

        context.notificationManager.notify(threadId, repliedNotification)

        ensureBackgroundThread {
            context.markThreadMessagesRead(threadId)
            context.conversationsDB.markRead(threadId.toLong())
        }
    }
}
