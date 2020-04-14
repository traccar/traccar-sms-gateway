package com.simplemobiletools.smsmessenger.helpers

import com.simplemobiletools.smsmessenger.models.Events
import org.greenrobot.eventbus.EventBus

const val THREAD_ID = "thread_id"
const val THREAD_TITLE = "thread_title"
const val THREAD_TEXT = "thread_text"
const val THREAD_NUMBER = "thread_number"
const val THREAD_ATTACHMENT_URI = "thread_attachment_uri"
const val THREAD_ATTACHMENT_URIS = "thread_attachment_uris"

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

// most app icon colors from md_app_icon_colors with reduced alpha
val letterBackgroundColors = arrayListOf(
    0xCCD32F2F,
    0xCCC2185B,
    0xCC1976D2,
    0xCC0288D1,
    0xCC0097A7,
    0xCC00796B,
    0xCC388E3C,
    0xCC689F38,
    0xCCF57C00,
    0xCCE64A19
)
