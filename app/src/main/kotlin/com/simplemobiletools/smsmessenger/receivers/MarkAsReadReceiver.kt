package com.simplemobiletools.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simplemobiletools.commons.extensions.notificationManager
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.conversationsDB
import com.simplemobiletools.smsmessenger.extensions.markMessageRead
import com.simplemobiletools.smsmessenger.helpers.MARK_AS_READ
import com.simplemobiletools.smsmessenger.helpers.MESSAGE_ID
import com.simplemobiletools.smsmessenger.helpers.MESSAGE_IS_MMS
import com.simplemobiletools.smsmessenger.helpers.THREAD_ID

class MarkAsReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MARK_AS_READ -> {
                val messageId = intent.getIntExtra(MESSAGE_ID, 0)
                context.notificationManager.cancel(messageId)
                ensureBackgroundThread {
                    val isMMS = intent.getBooleanExtra(MESSAGE_IS_MMS, false)
                    context.markMessageRead(messageId, isMMS)

                    val threadId = intent.getIntExtra(THREAD_ID, 0)
                    context.conversationsDB.markRead(threadId.toLong())
                }
            }
        }
    }
}
