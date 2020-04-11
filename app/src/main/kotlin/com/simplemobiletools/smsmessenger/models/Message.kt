package com.simplemobiletools.smsmessenger.models

import android.provider.Telephony

data class Message(
    val id: Int, val body: String, val type: Int, val participants: ArrayList<Contact>, val date: Int, val read: Boolean, val thread: Int,
    val isMMS: Boolean, val attachment: MessageAttachment?
) : ThreadItem() {
    fun isReceivedMessage() = type == Telephony.Sms.MESSAGE_TYPE_INBOX
}
