package com.simplemobiletools.smsmessenger.models

import android.graphics.Bitmap
import android.provider.Telephony
import com.simplemobiletools.smsmessenger.extensions.getThreadTitle

data class Message(
    val id: Int, val body: String, val type: Int, val participants: ArrayList<Contact>, val date: Int, val read: Boolean, val thread: Int,
    val isMMS: Boolean, val attachment: MessageAttachment?
) : ThreadItem() {
    fun isReceivedMessage() = type == Telephony.Sms.MESSAGE_TYPE_INBOX

    fun getThreadTitle() = participants.getThreadTitle()

    fun getThreadImageUri() = participants.firstOrNull()?.photoUri
}
