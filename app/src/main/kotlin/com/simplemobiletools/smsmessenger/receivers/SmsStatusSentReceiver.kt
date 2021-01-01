package com.simplemobiletools.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simplemobiletools.smsmessenger.helpers.refreshMessages

class SmsStatusSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        refreshMessages()
    }
}
