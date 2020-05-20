package com.simplemobiletools.smsmessenger.models

data class Conversation(
    val id: Int, val snippet: String, val date: Int, val read: Boolean, var title: String, var photoUri: String,
    val isGroupConversation: Boolean, val phoneNumber: String)
