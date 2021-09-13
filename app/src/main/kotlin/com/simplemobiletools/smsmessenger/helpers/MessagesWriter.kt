package com.simplemobiletools.smsmessenger.helpers

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.simplemobiletools.commons.extensions.queryCursor
import com.simplemobiletools.smsmessenger.extensions.getThreadId
import com.simplemobiletools.smsmessenger.extensions.toContentValues

class MessagesWriter(private val context: Context) {
    companion object {
        private const val TAG = "MessagesWriter"
    }

    private val contentResolver = context.contentResolver

    fun writeSmsMessage(smsMap: Map<String, Any>) {
        Log.w(TAG, "writeSmsMessage: map=$smsMap")
        val contentValues = smsMap.toContentValues()
        contentValues.remove(Telephony.Sms._ID)
        replaceSmsThreadId(contentValues)
        Log.d(TAG, "writeSmsMessage: contentValues=$contentValues")
        val type = contentValues.getAsInteger(Telephony.Sms.TYPE)
        Log.d(TAG, "writeSmsMessage: type=$type")
        if ((type == Telephony.Sms.MESSAGE_TYPE_INBOX || type == Telephony.Sms.MESSAGE_TYPE_SENT) && !smsExist(smsMap)) {
            Log.d(TAG, "writeSmsMessage: Inserting SMS...")
            val uri = Telephony.Sms.CONTENT_URI
            Log.d(TAG, "writeSmsMessage: uri=$uri")
            contentResolver.insert(Telephony.Sms.CONTENT_URI, contentValues)
        }
    }

    private fun replaceSmsThreadId(contentValues: ContentValues) {
        val address = contentValues.get(Telephony.Sms.ADDRESS)
        val threadId = context.getThreadId(address.toString())
        contentValues.put(Telephony.Sms.THREAD_ID, threadId)
    }

    private fun smsExist(smsMap: Map<String, Any>): Boolean {
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(Telephony.Sms._ID)
        val selection = "${Telephony.Sms.DATE} = ? AND ${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.TYPE} = ?"
        val selectionArgs = arrayOf(smsMap[Telephony.Sms.DATE].toString(), smsMap[Telephony.Sms.ADDRESS].toString(), smsMap[Telephony.Sms.TYPE].toString())

        var exists = false
        context.queryCursor(uri, projection, selection, selectionArgs) {
            exists = it.count > 0
            Log.i(TAG, "smsExist After: $exists")
        }
        Log.i(TAG, "smsExist: $exists")

        return exists
    }

    fun updateAllSmsThreads(){
        // thread dates + states might be wrong, we need to force a full update
        // unfortunately there's no direct way to do that in the SDK, but passing a
        // negative conversation id to delete should to the trick
        contentResolver.delete(Telephony.Sms.Conversations.CONTENT_URI.buildUpon().appendPath("-1").build(), null, null)
    }

    fun writeMmsMessage(mmsMap: Map<String, Any>) {
        val contentValues = mmsMap.toContentValues()
        contentValues.remove(Telephony.Mms._ID)
        replaceMmsThreadId(contentValues, mmsMap)
        Telephony.Mms.MESSAGE_TYPE


    }

    private fun replaceMmsThreadId(contentValues: ContentValues, mmsMap: Map<String, Any>) {
        val addresses = (mmsMap[Telephony.Mms.Addr.ADDRESS] as? List<*>)?.map { it.toString() }?.toHashSet()
        val threadId = context.getThreadId(addresses ?: setOf())
        contentValues.put(Telephony.Mms.THREAD_ID, threadId)
    }
}
