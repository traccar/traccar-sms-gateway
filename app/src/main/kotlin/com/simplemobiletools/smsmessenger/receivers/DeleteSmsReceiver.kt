package com.simplemobiletools.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simplemobiletools.commons.extensions.notificationManager
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.conversationsDB
import com.simplemobiletools.smsmessenger.extensions.deleteMessage
import com.simplemobiletools.smsmessenger.extensions.updateLastConversationMessage
import com.simplemobiletools.smsmessenger.extensions.updateUnreadCountBadge
import com.simplemobiletools.smsmessenger.helpers.IS_MMS
import com.simplemobiletools.smsmessenger.helpers.MESSAGE_ID
import com.simplemobiletools.smsmessenger.helpers.THREAD_ID
import com.simplemobiletools.smsmessenger.helpers.refreshMessages

class DeleteSmsReceiver: BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(THREAD_ID, 0L)
        val messageId = intent.getLongExtra(MESSAGE_ID, 0L)
        val isMms = intent.getBooleanExtra(IS_MMS, false)
        context.notificationManager.cancel(threadId.hashCode())
        ensureBackgroundThread {
            context.deleteMessage(messageId, isMms)
            context.updateUnreadCountBadge(context.conversationsDB.getUnreadConversations())
            context.updateLastConversationMessage(threadId)
            refreshMessages()
        }
    }
}
