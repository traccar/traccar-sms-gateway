package com.simplemobiletools.smsmessenger.receivers

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony.Sms
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.messagesDB
import com.simplemobiletools.smsmessenger.helpers.refreshMessages
import com.simplemobiletools.smsmessenger.messaging.SendStatusReceiver

/** Handles updating databases and states when a sent SMS message is delivered. */
class SmsStatusDeliveredReceiver : SendStatusReceiver() {

    override fun updateAndroidDatabase(context: Context, intent: Intent, receiverResultCode: Int) {
        val uri: Uri? = intent.data
        val resultCode = resultCode

        try {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    if (uri != null) {
                        val values = ContentValues().apply {
                            put(Sms.Sent.STATUS, Sms.Sent.STATUS_COMPLETE)
                            put(Sms.Sent.DATE_SENT, System.currentTimeMillis())
                            put(Sms.Sent.READ, true)
                        }
                        context.contentResolver.update(uri, values, null, null)
                    } else {
                        updateLatestSmsStatus(context, status = Sms.Sent.STATUS_COMPLETE, read = true, date = System.currentTimeMillis())
                    }
                }
                Activity.RESULT_CANCELED -> {
                    if (uri != null) {
                        val values = ContentValues().apply {
                            put(Sms.Sent.STATUS, Sms.Sent.STATUS_FAILED)
                            put(Sms.Sent.DATE_SENT, System.currentTimeMillis())
                            put(Sms.Sent.READ, true)
                            put(Sms.Sent.ERROR_CODE, resultCode)
                        }
                        context.contentResolver.update(uri, values, null, null)
                    } else {
                        updateLatestSmsStatus(context, status = Sms.Sent.STATUS_FAILED, read = true, errorCode = resultCode)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("Range")
    private fun updateLatestSmsStatus(context: Context, status: Int, read: Boolean, date: Long = -1L, errorCode: Int = -1) {
        val query = context.contentResolver.query(Sms.Sent.CONTENT_URI, null, null, null, "date desc")

        // mark message as delivered in database
        if (query!!.moveToFirst()) {
            val id = query.getString(query.getColumnIndex(Sms.Sent._ID))

            val values = ContentValues().apply {
                put(Sms.Sent.STATUS, status)
                put(Sms.Sent.READ, read)

                if (date != -1L) {
                    put(Sms.Sent.DATE_SENT, date)
                }
                if (errorCode != -1) {
                    put(Sms.Sent.ERROR_CODE, errorCode)
                }
            }

            context.contentResolver.update(Sms.Sent.CONTENT_URI, values, "_id=$id", null)
        }
        query.close()
    }

    override fun updateAppDatabase(context: Context, intent: Intent, receiverResultCode: Int) {
        val uri = intent.data
        if (uri != null) {
            val messageId = uri.lastPathSegment?.toLong() ?: 0L
            ensureBackgroundThread {
                val status = Sms.Sent.STATUS_COMPLETE
                val updated = context.messagesDB.updateStatus(messageId, status)
                if (updated == 0) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        ensureBackgroundThread {
                            context.messagesDB.updateStatus(messageId, status)
                        }
                    }, 2000)
                }

                refreshMessages()
            }
        }
    }
}
