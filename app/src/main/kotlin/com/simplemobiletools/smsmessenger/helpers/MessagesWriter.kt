package com.simplemobiletools.smsmessenger.helpers

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.provider.Telephony.Mms
import android.util.Base64
import android.util.Log
import com.google.android.mms.pdu_alt.PduHeaders
import com.klinker.android.send_message.Utils
import com.simplemobiletools.commons.extensions.getLongValue
import com.simplemobiletools.commons.extensions.queryCursor
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.smsmessenger.extensions.getThreadId
import com.simplemobiletools.smsmessenger.models.MmsAddress
import com.simplemobiletools.smsmessenger.models.MmsBackup
import com.simplemobiletools.smsmessenger.models.MmsPart
import com.simplemobiletools.smsmessenger.models.SmsBackup
import java.nio.charset.Charset

class MessagesWriter(private val context: Context) {
    companion object {
        private const val INVALID_ID = -1L
        private const val TAG = "MessagesWriter"
    }

    private val contentResolver = context.contentResolver

    fun writeSmsMessage(smsBackup: SmsBackup) {
        Log.w(TAG, "writeSmsMessage: smsBackup=$smsBackup")
        val contentValues = smsBackup.toContentValues()
        replaceSmsThreadId(contentValues)
        Log.d(TAG, "writeSmsMessage: contentValues=$contentValues")
        Log.d(TAG, "writeSmsMessage: type=${smsBackup.type}")
        if ((smsBackup.type == Telephony.Sms.MESSAGE_TYPE_INBOX || smsBackup.type == Telephony.Sms.MESSAGE_TYPE_SENT) && !smsExist(smsBackup)) {
            Log.d(TAG, "writeSmsMessage: Inserting SMS...")
            val uri = Telephony.Sms.CONTENT_URI
            Log.d(TAG, "writeSmsMessage: uri=$uri")
            contentResolver.insert(Telephony.Sms.CONTENT_URI, contentValues)
        }
    }

    private fun replaceSmsThreadId(contentValues: ContentValues) {
        val address = contentValues.get(Telephony.Sms.ADDRESS)
        val threadId = Utils.getOrCreateThreadId(context, address.toString())
        contentValues.put(Telephony.Sms.THREAD_ID, threadId)
    }

    private fun smsExist(smsBackup: SmsBackup): Boolean {
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(Telephony.Sms._ID)
        val selection = "${Telephony.Sms.DATE} = ? AND ${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.TYPE} = ?"
        val selectionArgs = arrayOf(smsBackup.date.toString(), smsBackup.address, smsBackup.type.toString())

        var exists = false
        context.queryCursor(uri, projection, selection, selectionArgs) {
            exists = it.count > 0
            Log.i(TAG, "smsExist After: $exists")
        }
        Log.i(TAG, "smsExist: $exists")

        return exists
    }

    fun writeMmsMessage(mmsBackup: MmsBackup) {
        // 1. write mms msg, get the msg_id, check if mms exists before writing
        // 2. write parts - parts depend on the msg id, check if part exist before writing, store _data in Downloads directory
        // 3. write the addresses, address depends on msg id too, check if address exist before writing
        Log.w(TAG, "writeMmsMessage: backup=$mmsBackup")
        val contentValues = mmsBackup.toContentValues()
        val threadId = getMmsThreadId(mmsBackup)
        if (threadId != INVALID_ID) {
            contentValues.put(Telephony.Mms.THREAD_ID, threadId)
            Log.w(TAG, "writeMmsMessage: backup=$mmsBackup")
            //write mms
            if ((mmsBackup.messageBox == Telephony.Mms.MESSAGE_BOX_INBOX || mmsBackup.messageBox == Telephony.Mms.MESSAGE_BOX_SENT) && !mmsExist(mmsBackup)) {
                contentResolver.insert(Telephony.Mms.CONTENT_URI, contentValues)
            }

            val messageId = getMmsId(mmsBackup)
            if (messageId != INVALID_ID) {
                Log.d(TAG, "writing mms addresses")
                //write addresses
                mmsBackup.addresses.forEach { writeMmsAddress(it, messageId) }
                mmsBackup.mmsParts.forEach { writeMmsPart(it, messageId) }
            } else {
                Log.d(TAG, "failed to write mms message, invalid mms id")
            }
        } else {
            Log.d(TAG, "failed to write mms message, invalid thread id")
        }
    }

    private fun getMmsThreadId(mmsBackup: MmsBackup): Long {
        val address = when (mmsBackup.messageBox) {
            Mms.MESSAGE_BOX_INBOX -> {
                mmsBackup.addresses.firstOrNull { it.type == PduHeaders.FROM }?.address
            }
            else -> {
                mmsBackup.addresses.firstOrNull { it.type == PduHeaders.TO }?.address
            }
        }
        return if (!address.isNullOrEmpty()) {
            Utils.getOrCreateThreadId(context, address)
        } else {
            INVALID_ID
        }
    }

    private fun getMmsId(mmsBackup: MmsBackup): Long {
        val threadId = getMmsThreadId(mmsBackup)
        val uri = Telephony.Mms.CONTENT_URI
        val projection = arrayOf(Telephony.Mms._ID)
        val selection = "${Telephony.Mms.DATE} = ? AND ${Telephony.Mms.DATE_SENT} = ? AND ${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.MESSAGE_BOX} = ?"
        val selectionArgs = arrayOf(mmsBackup.date.toString(), mmsBackup.dateSent.toString(), threadId.toString(), mmsBackup.messageBox.toString())

        var id = INVALID_ID
        context.queryCursor(uri, projection, selection, selectionArgs) {
            id = it.getLongValue(Telephony.Mms._ID)
            Log.i(TAG, "getMmsId After: $id")
        }
        Log.i(TAG, "getMmsId: $id")

        return id
    }

    private fun mmsExist(mmsBackup: MmsBackup): Boolean {
        return getMmsId(mmsBackup) != INVALID_ID
    }

    @SuppressLint("NewApi")
    private fun mmsAddressExist(mmsAddress: MmsAddress, messageId: Long): Boolean {
        val addressUri = if (isQPlus()) {
            Telephony.Mms.Addr.getAddrUriForMessage(messageId.toString())
        } else {
            Uri.parse("content://mms/$messageId/addr")
        }
        val projection = arrayOf(Telephony.Mms.Addr._ID)
        val selection = "${Telephony.Mms.Addr.TYPE} = ? AND ${Telephony.Mms.Addr.ADDRESS} = ? AND ${Telephony.Mms.Addr.MSG_ID} = ?"
        val selectionArgs = arrayOf(mmsAddress.type.toString(), mmsAddress.address.toString(), messageId.toString())

        var exists = false
        context.queryCursor(addressUri, projection, selection, selectionArgs) {
            exists = it.count > 0
            Log.i(TAG, "mmsAddressExist After: $exists")
        }
        Log.i(TAG, "mmsAddressExist: $exists")

        return exists
    }

    @SuppressLint("NewApi")
    private fun writeMmsAddress(mmsAddress: MmsAddress, messageId: Long) {
        if (!mmsAddressExist(mmsAddress, messageId)) {
            val addressUri = if (isQPlus()) {
                Telephony.Mms.Addr.getAddrUriForMessage(messageId.toString())
            } else {
                Uri.parse("content://mms/$messageId/addr")
            }

            val contentValues = mmsAddress.toContentValues()
            contentValues.put(Telephony.Mms.Addr.MSG_ID, messageId)
            contentResolver.insert(addressUri, contentValues)
        } else {
            Log.w(TAG, "writeMmsAddress: Skip already exists")
        }
    }

    @SuppressLint("NewApi")
    private fun writeMmsPart(mmsPart: MmsPart, messageId: Long) {
        Log.d(TAG, "writeMmsPart: Writing part= $mmsPart")
        if (!mmsPartExist(mmsPart, messageId)) {
            val uri = Uri.parse("content://mms/${messageId}/part")
            val contentValues = mmsPart.toContentValues()
            contentValues.put(Mms.Part.MSG_ID, messageId)
            val partUri = contentResolver.insert(uri, contentValues)
            //write data
            Log.d(TAG, "writeMmsPart: Inserted part=$partUri")
            try {
                if (partUri != null) {
                    if (mmsPart.isNonText()) {
                        contentResolver.openOutputStream(partUri).use {
                            val arr = Base64.decode(mmsPart.mmsContent, Base64.DEFAULT)
                            it!!.write(arr)
                            Log.d(TAG, "Wrote part data $mmsPart")
                        }
                    } else {
                        Log.w(TAG, "skip writing text content")
                    }

                } else {
                    Log.e(TAG, "invalid uri while writing part")
                }
            } catch (e: Exception) {
                Log.e(TAG, "writeMmsPart: uri", e)
            }
        }
    }

    @SuppressLint("NewApi")
    private fun mmsPartExist(mmsPart: MmsPart, messageId: Long): Boolean {
        val uri = Uri.parse("content://mms/${messageId}/part")
        val projection = arrayOf(Telephony.Mms.Part._ID)
        val selection =
            "${Telephony.Mms.Part.CONTENT_LOCATION} = ? AND ${Telephony.Mms.Part.CT_TYPE} = ? AND ${Mms.Part.MSG_ID} = ? AND ${Telephony.Mms.Part.CONTENT_ID} = ?"
        val selectionArgs = arrayOf(mmsPart.contentLocation.toString(), mmsPart.contentType.toString(), messageId.toString(), mmsPart.contentId.toString())
        var exists = false
        context.queryCursor(uri, projection, selection, selectionArgs) {
            exists = it.count > 0
            Log.i(TAG, "mmsPartExist After: $exists")
        }
        Log.i(TAG, "mmsPartExist: $exists")

        return exists
    }

    fun updateAllSmsThreads() {
        // thread dates + states might be wrong, we need to force a full update
        // unfortunately there's no direct way to do that in the SDK, but passing a
        // negative conversation id to delete should to the trick
        contentResolver.delete(Telephony.Sms.Conversations.CONTENT_URI.buildUpon().appendPath("-1").build(), null, null)
    }
}
