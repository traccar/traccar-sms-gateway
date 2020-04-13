package com.simplemobiletools.smsmessenger.receivers

import android.content.Context
import android.content.Intent
import com.klinker.android.send_message.SentReceiver
import com.simplemobiletools.smsmessenger.helpers.refreshMessages

class SmsSentReceiver : SentReceiver() {
    override fun onMessageStatusUpdated(context: Context, intent: Intent, receiverResultCode: Int) {
        refreshMessages()
    }
}
