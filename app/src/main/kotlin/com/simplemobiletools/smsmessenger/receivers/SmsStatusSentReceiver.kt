package com.simplemobiletools.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.messagesDB
import com.simplemobiletools.smsmessenger.extensions.updateMessageType
import com.simplemobiletools.smsmessenger.helpers.refreshMessages

class SmsStatusSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.extras?.containsKey("message_uri") == true) {
            val uri = Uri.parse(intent.getStringExtra("message_uri"))
            val id = uri?.lastPathSegment?.toLong() ?: 0L
            ensureBackgroundThread {
                val type = if (intent.extras!!.containsKey("errorCode")) Telephony.Sms.MESSAGE_TYPE_FAILED else Telephony.Sms.MESSAGE_TYPE_OUTBOX
                context.updateMessageType(id, type)
                context.messagesDB.updateType(id, type)
                refreshMessages()
            }
        }
    }
}
