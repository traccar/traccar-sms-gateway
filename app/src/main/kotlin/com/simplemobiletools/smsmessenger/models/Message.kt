package com.simplemobiletools.smsmessenger.models

import android.provider.Telephony
import com.simplemobiletools.commons.models.SimpleContact

data class Message(
    val id: Int, val body: String, val type: Int, val participants: ArrayList<SimpleContact>, val date: Int, val read: Boolean, val thread: Int,
    val isMMS: Boolean, val attachment: MessageAttachment?, var senderName: String, val senderPhotoUri: String, val subscriptionId: Int) : ThreadItem() {
    fun isReceivedMessage() = type == Telephony.Sms.MESSAGE_TYPE_INBOX
}
