package com.simplemobiletools.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.messagesDB
import com.simplemobiletools.smsmessenger.extensions.updateMessageType
import com.simplemobiletools.smsmessenger.helpers.refreshMessages

class SmsStatusDeliveredReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.extras?.containsKey("message_uri") == true) {
            val uri = Uri.parse(intent.getStringExtra("message_uri"))
            val messageId = uri?.lastPathSegment?.toLong() ?: 0L
            ensureBackgroundThread {
                val type = Telephony.Sms.MESSAGE_TYPE_SENT
                context.updateMessageType(messageId, type)
                val updated = context.messagesDB.updateType(messageId, type)
                if (updated == 0) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        ensureBackgroundThread {
                            context.messagesDB.updateType(messageId, type)
                        }
                    }, 2000)
                }

                refreshMessages()
            }
        }
    }
}
