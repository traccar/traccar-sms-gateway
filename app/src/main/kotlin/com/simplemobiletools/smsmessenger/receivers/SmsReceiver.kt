package com.simplemobiletools.smsmessenger.receivers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.provider.Telephony
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.simplemobiletools.commons.extensions.getAdjustedPrimaryColor
import com.simplemobiletools.commons.extensions.getContrastColor
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.ThreadActivity
import com.simplemobiletools.smsmessenger.extensions.getThreadId
import com.simplemobiletools.smsmessenger.extensions.insertNewSMS
import com.simplemobiletools.smsmessenger.helpers.THREAD_ID
import com.simplemobiletools.smsmessenger.helpers.refreshMessages

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        messages.forEach {
            val address = it.originatingAddress ?: ""
            val subject = it.pseudoSubject
            val body = it.messageBody
            val date = it.timestampMillis
            val threadId = context.getThreadId(address)
            val type = Telephony.Sms.MESSAGE_TYPE_INBOX
            val read = 0
            context.insertNewSMS(address, subject, body, date, read, threadId, type)
            showNotification(context, address, body, threadId.toInt())
        }

        refreshMessages()
    }

    @SuppressLint("NewApi")
    private fun showNotification(context: Context, address: String, body: String, threadID: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val channelId = "simple_sms_messenger"
        if (isOreoPlus()) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
                .build()

            val name = context.getString(R.string.channel_received_sms)
            val importance = NotificationManager.IMPORTANCE_HIGH
            NotificationChannel(channelId, name, importance).apply {
                setBypassDnd(false)
                enableLights(true)
                setSound(soundUri, audioAttributes)
                enableVibration(true)
                notificationManager.createNotificationChannel(this)
            }
        }

        val intent = Intent(context, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, threadID)
        }

        val pendingIntent = PendingIntent.getActivity(context, threadID, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val summaryText = context.getString(R.string.new_message)

        val firstLetter = address.toCharArray().getOrNull(0)?.toString() ?: "S"
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(address)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_messenger)
            .setLargeIcon(getNotificationLetterIcon(context, firstLetter))
            .setStyle(NotificationCompat.BigTextStyle().setSummaryText(summaryText).bigText(body))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(Notification.DEFAULT_LIGHTS)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setSound(soundUri, AudioManager.STREAM_NOTIFICATION)
            .setChannelId(channelId)

        notificationManager.notify(threadID, builder.build())
    }

    private fun getNotificationLetterIcon(context: Context, letter: String): Bitmap {
        val size = context.resources.getDimension(R.dimen.notification_large_icon_size).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val view = TextView(context)
        view.layout(0, 0, size, size)

        val circlePaint = Paint().apply {
            color = context.getAdjustedPrimaryColor()
            isAntiAlias = true
        }

        val wantedTextSize = size / 2f
        val textPaint = Paint().apply {
            color = circlePaint.color.getContrastColor()
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = wantedTextSize
        }

        canvas.drawCircle(size / 2f, size / 2f, size / 2f, circlePaint)

        val xPos = canvas.width / 2f
        val yPos = canvas.height / 2 - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(letter, xPos, yPos, textPaint)
        view.draw(canvas)
        return bitmap
    }
}
