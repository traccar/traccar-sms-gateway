package com.simplemobiletools.smsmessenger.receivers

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.provider.Telephony
import com.bumptech.glide.Glide
import com.klinker.android.send_message.MmsReceivedReceiver
import com.simplemobiletools.commons.extensions.getStringValue
import com.simplemobiletools.commons.extensions.isNumberBlocked
import com.simplemobiletools.commons.extensions.normalizePhoneNumber
import com.simplemobiletools.commons.extensions.queryCursor
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.refreshMessages

// more info at https://github.com/klinker41/android-smsmms
class MmsReceiver : MmsReceivedReceiver() {

    private var carriers = mutableMapOf<Int, MmscInformation>()

    private fun populateMmscInformation(context: Context, subscriptionId: Int) {
        if (isRPlus() && carriers.isNotEmpty()) {
            // This information is stored by ApnUtils from android-smsmms
            PreferenceManager.getDefaultSharedPreferences(context).apply {
                carriers[0] = MmscInformation(
                    getString("mmsc_url", ""),
                    getString("mms_proxy", ""),
                    getString("mms_port", "0")?.toInt() ?: 0
                )
            }
        } else {
            if (carriers.containsKey(subscriptionId).not()) {
                val baseUri = if (isQPlus()) {
                    Telephony.Carriers.SIM_APN_URI
                } else {
                    Telephony.Carriers.CONTENT_URI
                }
                val uri = Uri.withAppendedPath(baseUri, subscriptionId.toString())
                val projection = arrayOf(
                    Telephony.Carriers.MMSC,
                    Telephony.Carriers.MMSPROXY,
                    Telephony.Carriers.MMSPORT,
                )
                val selection = "${Telephony.Carriers.TYPE} LIKE ?"
                val selectionArgs = arrayOf("%mms%")

                context.queryCursor(uri, projection = projection, selection = selection, selectionArgs = selectionArgs) { cursor ->
                    carriers[subscriptionId] = MmscInformation(
                        cursor.getStringValue(Telephony.Carriers.MMSC),
                        cursor.getStringValue(Telephony.Carriers.MMSPROXY),
                        cursor.getStringValue(Telephony.Carriers.MMSPORT).toIntOrNull() ?: 0,
                    )
                }
            }
        }
    }

    private fun getMmscInformation(subscriptionId: Int): MmscInformation? {
        return if (isRPlus()) {
            carriers[0]
        } else {
            carriers[subscriptionId]
        }
    }

    override fun getMmscInfoForReceptionAck(context: Context, subscriptionId: Int): MmscInformation? {
        populateMmscInformation(context, subscriptionId)
        return getMmscInformation(subscriptionId)
    }

    override fun isAddressBlocked(context: Context, address: String): Boolean {
        val normalizedAddress = address.normalizePhoneNumber()
        return context.isNumberBlocked(normalizedAddress)
    }

    override fun onMessageReceived(context: Context, messageUri: Uri) {
        val mms = context.getLatestMMS() ?: return
        val address = mms.getSender()?.phoneNumbers?.first()?.normalizedNumber ?: ""

        val size = context.resources.getDimension(R.dimen.notification_large_icon_size).toInt()
        ensureBackgroundThread {
            val glideBitmap = try {
                Glide.with(context)
                    .asBitmap()
                    .load(mms.attachment!!.attachments.first().getUri())
                    .centerCrop()
                    .into(size, size)
                    .get()
            } catch (e: Exception) {
                null
            }

            Handler(Looper.getMainLooper()).post {
                context.showReceivedMessageNotification(mms.id, address, mms.body, mms.threadId, glideBitmap)
                val conversation = context.getConversations(mms.threadId).firstOrNull() ?: return@post
                ensureBackgroundThread {
                    context.insertOrUpdateConversation(conversation)
                    context.updateUnreadCountBadge(context.conversationsDB.getUnreadConversations())
                    refreshMessages()
                }
            }
        }
    }

    override fun onError(context: Context, error: String) {}
}
