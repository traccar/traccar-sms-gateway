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

    fun writeSmsMessage(map: Map<String, String>) {
        Log.w(TAG, "writeSmsMessage: map=$map")
        val contentValues = map.toContentValues()
        contentValues.remove(Telephony.Sms._ID)
        replaceThreadId(contentValues)
        Log.d(TAG, "writeSmsMessage: contentValues=$contentValues")
        val type = contentValues.getAsInteger(Telephony.Sms.TYPE)
        Log.d(TAG, "writeSmsMessage: type=$type")
        if ((type == Telephony.Sms.MESSAGE_TYPE_INBOX || type == Telephony.Sms.MESSAGE_TYPE_SENT) && !smsExist(map)) {
            Log.d(TAG, "writeSmsMessage: Inserting SMS...")
            val uri = Telephony.Sms.CONTENT_URI
            Log.d(TAG, "writeSmsMessage: uri=$uri")
            contentResolver.insert(Telephony.Sms.CONTENT_URI, contentValues)
        }
    }

    private fun replaceThreadId(contentValues: ContentValues) {
        val address = contentValues.get(Telephony.Sms.ADDRESS)
        val threadId = context.getThreadId(address.toString())
        contentValues.put(Telephony.Sms.THREAD_ID, threadId)
    }

    fun writeMmsMessage(map: Map<String, Any>) {

    }

    private fun smsExist(sms: Map<String, String>): Boolean {
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(Telephony.Sms._ID)
        val selection = "${Telephony.Sms.DATE} = ? AND ${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.TYPE} = ?"
        val selectionArgs = arrayOf(sms[Telephony.Sms.DATE] as String, sms[Telephony.Sms.ADDRESS] as String, sms[Telephony.Sms.TYPE] as String)

        var exists = false
        context.queryCursor(uri, projection, selection, selectionArgs) {
            exists = it.count > 0
            Log.i(TAG, "smsExist After: $exists")
        }
        Log.i(TAG, "smsExist: $exists")

        return exists
    }

    private fun Map<String, String>.toSmsContentValues(): ContentValues {
        val values =  ContentValues()
        val address = get(Telephony.Sms.ADDRESS) as String
        values.put(Telephony.Sms.ADDRESS, address)
        values.put(Telephony.Sms.DATE, get(Telephony.Sms.DATE))
        values.put(Telephony.Sms.DATE_SENT, get(Telephony.Sms.DATE_SENT))
        values.put(Telephony.Sms.BODY, get(Telephony.Sms.BODY) as String)
        values.put(Telephony.Sms.TYPE, get(Telephony.Sms.TYPE))
        values.put(Telephony.Sms.PROTOCOL, get(Telephony.Sms.PROTOCOL))
        values.put(Telephony.Sms.SERVICE_CENTER, get(Telephony.Sms.SERVICE_CENTER))
        values.put(Telephony.Sms.STATUS, get(Telephony.Sms.STATUS))
        values.put(Telephony.Sms.READ, get(Telephony.Sms.READ))
        values.put(Telephony.Sms.CREATOR, get(Telephony.Sms.CREATOR))
        values.put(Telephony.Sms.LOCKED, get(Telephony.Sms.LOCKED))
        values.put(Telephony.Sms.SUBSCRIPTION_ID, get(Telephony.Sms.SUBSCRIPTION_ID))
        values.put(Telephony.Sms.THREAD_ID, context.getThreadId(address))
        return values
    }

    fun updateAllSmsThreads(){
        // thread dates + states might be wrong, we need to force a full update
        // unfortunately there's no direct way to do that in the SDK, but passing a
        // negative conversation id to delete should to the trick
        contentResolver.delete(Telephony.Sms.Conversations.CONTENT_URI.buildUpon().appendPath("-1").build(), null, null)
    }
}
