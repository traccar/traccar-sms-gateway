package com.simplemobiletools.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.simplemobiletools.commons.extensions.baseConfig
import com.simplemobiletools.commons.extensions.getMyContactsCursor
import com.simplemobiletools.commons.extensions.isNumberBlocked
import com.simplemobiletools.commons.helpers.SimpleContactsHelper
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.models.PhoneNumber
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.refreshMessages
import com.simplemobiletools.smsmessenger.models.Message

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        var address = ""
        var body = ""
        var subject = ""
        var date = 0L
        var threadId = 0L
        var status = Telephony.Sms.STATUS_NONE
        val type = Telephony.Sms.MESSAGE_TYPE_INBOX
        val read = 0
        val subscriptionId = intent.getIntExtra("subscription", -1)

        val privateCursor = context.getMyContactsCursor(false, true)
        ensureBackgroundThread {
            messages.forEach {
                address = it.originatingAddress ?: ""
                subject = it.pseudoSubject
                status = it.status
                body += it.messageBody
                date = System.currentTimeMillis()
                threadId = context.getThreadId(address)
            }

            if (context.baseConfig.blockUnknownNumbers) {
                val simpleContactsHelper = SimpleContactsHelper(context)
                simpleContactsHelper.exists(address, privateCursor) { exists ->
                    if (exists) {
                        handleMessage(context, address, subject, body, date, read, threadId, type, subscriptionId, status)
                    }
                }
            } else {
                handleMessage(context, address, subject, body, date, read, threadId, type, subscriptionId, status)
            }
        }
    }

    private fun handleMessage(
        context: Context, address: String, subject: String, body: String, date: Long, read: Int, threadId: Long, type: Int, subscriptionId: Int, status: Int
    ) {
        val bitmap = getPhotoForNotification(address, context)
        Handler(Looper.getMainLooper()).post {
            if (!context.isNumberBlocked(address)) {
                val privateCursor = context.getMyContactsCursor(false, true)
                ensureBackgroundThread {
                    val newMessageId = context.insertNewSMS(address, subject, body, date, read, threadId, type, subscriptionId)

                    val conversation = context.getConversations(threadId).firstOrNull() ?: return@ensureBackgroundThread
                    try {
                        context.conversationsDB.insertOrUpdate(conversation)
                    } catch (ignored: Exception) {
                    }

                    try {
                        context.updateUnreadCountBadge(context.conversationsDB.getUnreadConversations())
                    } catch (ignored: Exception) {
                    }

                    val senderName = context.getNameFromAddress(address, privateCursor)
                    val phoneNumber = PhoneNumber(address, 0, "", address)
                    val participant = SimpleContact(0, 0, senderName, "", arrayListOf(phoneNumber), ArrayList(), ArrayList())
                    val participants = arrayListOf(participant)
                    val messageDate = (date / 1000).toInt()

                    val message =
                        Message(newMessageId, body, type, status, participants, messageDate, false, threadId, false, null, address, "", subscriptionId)
                    context.messagesDB.insertOrUpdate(message)
                    refreshMessages()
                }

                context.showReceivedMessageNotification(address, body, threadId, bitmap)
            }
        }
    }

    private fun getPhotoForNotification(address: String, context: Context): Bitmap? {
        val photo = SimpleContactsHelper(context).getPhotoUriFromPhoneNumber(address)
        val size = context.resources.getDimension(R.dimen.notification_large_icon_size).toInt()
        if (photo.isEmpty()) {
            return null
        }

        val options = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .centerCrop()

        return try {
            Glide.with(context)
                .asBitmap()
                .load(photo)
                .apply(options)
                .apply(RequestOptions.circleCropTransform())
                .into(size, size)
                .get()
        } catch (e: Exception) {
            null
        }
    }
}
