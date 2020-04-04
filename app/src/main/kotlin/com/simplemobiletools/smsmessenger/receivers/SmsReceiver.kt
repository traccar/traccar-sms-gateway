package com.simplemobiletools.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.simplemobiletools.smsmessenger.extensions.insertNewSMS
import com.simplemobiletools.smsmessenger.models.Events
import org.greenrobot.eventbus.EventBus

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        messages.forEach {
            val address = it.originatingAddress ?: ""
            val subject = it.pseudoSubject
            val body = it.messageBody
            val date = it.timestampMillis
            context.insertNewSMS(address, subject, body, date)
        }

        EventBus.getDefault().post(Events.RefreshMessages())
    }
}
