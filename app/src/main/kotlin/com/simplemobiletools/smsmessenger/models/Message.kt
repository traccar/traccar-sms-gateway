package com.simplemobiletools.smsmessenger.models

data class Message(val id: Int, val subject: String, val body: String, val type: Int, val address: String, val date: Int, val read: Boolean)
