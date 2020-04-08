package com.simplemobiletools.smsmessenger.helpers

import com.simplemobiletools.smsmessenger.models.Events
import org.greenrobot.eventbus.EventBus

const val THREAD_ID = "thread_id"
const val THREAD_TITLE = "thread_title"

// view types for the thread list view
const val THREAD_DATE_TIME = 1
const val THREAD_RECEIVED_MESSAGE = 2
const val THREAD_SENT_MESSAGE = 3
const val THREAD_SENT_MESSAGE_ERROR = 4

// constants used at passing info to SmsSentReceiver
const val MESSAGE_BODY = "message_body"
const val MESSAGE_ADDRESS = "message_address"

fun refreshMessages() {
    EventBus.getDefault().post(Events.RefreshMessages())
}
