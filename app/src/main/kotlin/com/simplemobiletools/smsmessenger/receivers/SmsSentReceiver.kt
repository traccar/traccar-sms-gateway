package com.simplemobiletools.smsmessenger.receivers

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.simplemobiletools.smsmessenger.extensions.getThreadId
import com.simplemobiletools.smsmessenger.extensions.insertNewSMS
import com.simplemobiletools.smsmessenger.helpers.MESSAGE_ADDRESS
import com.simplemobiletools.smsmessenger.helpers.MESSAGE_BODY
import com.simplemobiletools.smsmessenger.helpers.refreshMessages

class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras
        if (extras != null) {
            val address = extras.getString(MESSAGE_ADDRESS) ?: ""
            val subject = ""
            val body = extras.getString(MESSAGE_BODY) ?: ""
            val date = System.currentTimeMillis()
            val threadId = context.getThreadId(address)
            val type = if (resultCode == Activity.RESULT_OK) {
                Telephony.Sms.MESSAGE_TYPE_SENT
            } else {
                Telephony.Sms.MESSAGE_TYPE_FAILED
            }
            val read = 1
            context.insertNewSMS(address, subject, body, date, read, threadId, type)
        }

        refreshMessages()
    }
}
