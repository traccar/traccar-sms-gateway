package com.simplemobiletools.smsmessenger.models

// show a check after the latest message, if it is a sent one and succeeded,
// show a double check if it is delivered
data class ThreadSent(val messageID: Long, val delivered: Boolean) : ThreadItem()
