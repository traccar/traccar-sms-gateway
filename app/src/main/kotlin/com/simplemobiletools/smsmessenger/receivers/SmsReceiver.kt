package com.simplemobiletools.smsmessenger.receivers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import com.simplemobiletools.commons.helpers.isMarshmallowPlus
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.ThreadActivity
import com.simplemobiletools.smsmessenger.extensions.insertNewSMS
import com.simplemobiletools.smsmessenger.helpers.THREAD_ID
import com.simplemobiletools.smsmessenger.helpers.THREAD_NAME
import com.simplemobiletools.smsmessenger.helpers.THREAD_NUMBER
import com.simplemobiletools.smsmessenger.models.Events
import org.greenrobot.eventbus.EventBus

class SmsReceiver : BroadcastReceiver() {
    @SuppressLint("NewApi")
    override fun onReceive(context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        messages.forEach {
            val address = it.originatingAddress ?: ""
            val subject = it.pseudoSubject
            val body = it.messageBody
            val date = it.timestampMillis
            val threadID = if (isMarshmallowPlus()) {
                Telephony.Threads.getOrCreateThreadId(context, address)
            } else {
                0
            }

            context.insertNewSMS(address, subject, body, date, threadID)
            showNotification(context, address, body, threadID.toInt())
        }

        EventBus.getDefault().post(Events.RefreshMessages())
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
            putExtra(THREAD_NAME, address)
            putExtra(THREAD_NUMBER, address)
        }

        val pendingIntent = PendingIntent.getActivity(context, threadID, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val summaryText = context.getString(R.string.new_message)

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(address)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_background))
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
}
