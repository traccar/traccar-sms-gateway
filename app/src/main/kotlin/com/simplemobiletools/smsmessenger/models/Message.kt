package com.simplemobiletools.smsmessenger.models

import android.provider.Telephony

data class Message(
    val id: Int, val subject: String, val body: String, val type: Int, val address: String, val date: Int, val read: Boolean,
    val thread: Int
) : ThreadItem() {
    fun isReceivedMessage() = type == Telephony.Sms.MESSAGE_TYPE_INBOX
}
