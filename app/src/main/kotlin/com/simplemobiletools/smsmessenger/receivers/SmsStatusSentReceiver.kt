package com.simplemobiletools.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.updateMessageDeliveryStatus
import com.simplemobiletools.smsmessenger.helpers.refreshMessages

class SmsStatusSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.extras?.containsKey("message_uri") == true) {
            val uri = Uri.parse(intent.getStringExtra("message_uri"))
            val id = uri?.lastPathSegment?.toLong() ?: 0L
            ensureBackgroundThread {
                val newStatus = if (intent.extras!!.containsKey("errorCode")) Telephony.Sms.MESSAGE_TYPE_FAILED else Telephony.Sms.MESSAGE_TYPE_OUTBOX
                context.updateMessageDeliveryStatus(id, newStatus)
                refreshMessages()
            }
        }
    }
}
