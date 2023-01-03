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
import android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE
import android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE
import android.telephony.SmsManager.RESULT_ERROR_NULL_PDU
import android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.simplemobiletools.commons.extensions.getMyContactsCursor
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.refreshMessages
import com.simplemobiletools.smsmessenger.messaging.SendStatusReceiver

/** Handles updating databases and states when a SMS message is sent. */
@SuppressLint("Range")
class SmsStatusSentReceiver : SendStatusReceiver() {

    override fun updateAndroidDatabase(context: Context, intent: Intent, receiverResultCode: Int) {
        val messageUri: Uri? = intent.data
        val resultCode = resultCode

        try {
            when (resultCode) {
                Activity.RESULT_OK -> if (messageUri != null) {
                    try {
                        val values = ContentValues()
                        values.put(Sms.Outbox.TYPE, Sms.MESSAGE_TYPE_SENT)
                        values.put(Sms.Outbox.READ, 1)
                        context.contentResolver.update(messageUri, values, null, null)
                    } catch (e: NullPointerException) {
                        updateLatestSms(context = context, type = Sms.MESSAGE_TYPE_SENT, read = 1)
                    }
                } else {
                    updateLatestSms(context = context, type = Sms.MESSAGE_TYPE_FAILED, read = 1)
                }
                RESULT_ERROR_GENERIC_FAILURE, RESULT_ERROR_NO_SERVICE, RESULT_ERROR_NULL_PDU, RESULT_ERROR_RADIO_OFF -> {
                    if (messageUri != null) {
                        val values = ContentValues()
                        values.put(Sms.Outbox.TYPE, Sms.MESSAGE_TYPE_FAILED)
                        values.put(Sms.Outbox.READ, true)
                        values.put(Sms.Outbox.ERROR_CODE, resultCode)
                        context.contentResolver.update(messageUri, values, null, null)
                    } else {
                        updateLatestSms(context = context, type = Sms.MESSAGE_TYPE_FAILED, read = 1, resultCode = resultCode)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        context.messagingUtils.maybeShowErrorToast(
            resultCode = resultCode,
            errorCode = intent.getIntExtra(EXTRA_ERROR_CODE, NO_ERROR_CODE)
        )
    }

    private fun updateLatestSms(context: Context, type: Int, read: Int, resultCode: Int = -1) {
        val query = context.contentResolver.query(Sms.Outbox.CONTENT_URI, null, null, null, null)

        if (query != null && query.moveToFirst()) {
            val id = query.getString(query.getColumnIndex(Sms.Outbox._ID))
            val values = ContentValues()
            values.put(Sms.Outbox.TYPE, type)
            values.put(Sms.Outbox.READ, read)
            if (resultCode != -1) {
                values.put(Sms.Outbox.ERROR_CODE, resultCode)
            }
            context.contentResolver.update(Sms.Outbox.CONTENT_URI, values, "_id=$id", null)
            query.close()
        }
    }

    override fun updateAppDatabase(context: Context, intent: Intent, receiverResultCode: Int) {
        val uri = intent.data
        if (uri != null) {
            val messageId = uri.lastPathSegment?.toLong() ?: 0L
            ensureBackgroundThread {
                val type = if (receiverResultCode == Activity.RESULT_OK) {
                    Sms.MESSAGE_TYPE_SENT
                } else {
                    showSendingFailedNotification(context, messageId)
                    Sms.MESSAGE_TYPE_FAILED
                }

                val updated = context.messagesDB.updateType(messageId, type)
                if (updated == 0) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        ensureBackgroundThread {
                            context.messagesDB.updateType(messageId, type)
                        }
                    }, 2000)
                }

                refreshMessages()
            }
        }
    }

    private fun showSendingFailedNotification(context: Context, messageId: Long) {
        Handler(Looper.getMainLooper()).post {
            if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                return@post
            }
            val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
            ensureBackgroundThread {
                val address = context.getMessageRecipientAddress(messageId)
                val threadId = context.getThreadId(address)
                val recipientName = context.getNameFromAddress(address, privateCursor)
                context.notificationHelper.showSendingFailedNotification(recipientName, threadId)
            }
        }
    }
}
