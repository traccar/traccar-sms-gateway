package com.simplemobiletools.smsmessenger.models

import android.provider.Telephony
import android.text.TextUtils

data class Message(
    val id: Int, val body: String, val type: Int, val participants: ArrayList<Contact>, val date: Int, val read: Boolean, val thread: Int
) : ThreadItem() {
    fun isReceivedMessage() = type == Telephony.Sms.MESSAGE_TYPE_INBOX

    fun getThreadTitle() = TextUtils.join(", ", participants.map { it.name }.toTypedArray())
}
