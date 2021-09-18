package com.simplemobiletools.smsmessenger.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Base64
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.smsmessenger.extensions.optLong
import com.simplemobiletools.smsmessenger.extensions.optString
import com.simplemobiletools.smsmessenger.extensions.rowsToJson
import java.io.IOException
import java.io.InputStream

class MessagesReader(private val context: Context) {
    companion object {
        private const val TAG = "MessagesReader"
    }

    fun forEachSms(threadId: Long, block: (JsonObject) -> Unit) {
        val selection = "${Telephony.Sms.THREAD_ID} = ?"
        val selectionArgs = arrayOf(threadId.toString())
        context.queryCursor(Telephony.Sms.CONTENT_URI, null, selection, selectionArgs) { cursor ->
            val json = cursor.rowsToJson()
            block(json)
        }
    }

    // all mms from simple sms are non-text messages
    fun forEachMms(threadId: Long, includeTextOnlyAttachment: Boolean = false, block: (JsonObject) -> Unit) {

        val selection = if (includeTextOnlyAttachment) {
            "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.TEXT_ONLY} = ?"
        } else {
            "${Telephony.Mms.THREAD_ID} = ?"
        }

        val selectionArgs = if (includeTextOnlyAttachment) {
            arrayOf(threadId.toString(), "1")
        } else {
            arrayOf(threadId.toString())
        }
        context.queryCursor(Telephony.Mms.CONTENT_URI, null, selection, selectionArgs) { cursor ->
            val json = cursor.rowsToJson()
            json.add("parts", getParts(json.getAsJsonPrimitive(Telephony.Mms._ID).asLong))
            json.add("addresses", getMMSAddresses(json.getAsJsonPrimitive(Telephony.Mms._ID).asLong))
            block(json)
        }
    }

    @SuppressLint("NewApi")
    private fun getParts(mmsId: Long): JsonArray {
        val jsonArray = JsonArray()
        val uri = if (isQPlus()) {
            Telephony.Mms.Part.CONTENT_URI
        } else {
            Uri.parse("content://mms/part")
        }

        val selection = "${Telephony.Mms.Part.MSG_ID}= ?"
        val selectionArgs = arrayOf(mmsId.toString())
        context.queryCursor(uri, null, selection, selectionArgs) { cursor ->
            val part = cursor.rowsToJson()

            val hasTextValue = (part.has(Telephony.Mms.Part.TEXT) && !part.get(Telephony.Mms.Part.TEXT).optString.isNullOrEmpty())

            when {
                hasTextValue -> {
                    part.addProperty(MMS_CONTENT, "")
                }

                part.get(Telephony.Mms.Part.CONTENT_TYPE).optString?.startsWith("text/") == true -> {
                    part.addProperty(MMS_CONTENT, usePart(part.get(Telephony.Mms.Part._ID).asLong) { stream ->
                        stream.readBytes().toString(Charsets.UTF_8)
                    })
                }
                else -> {
                    part.addProperty(MMS_CONTENT, usePart(part.get(Telephony.Mms.Part._ID).asLong) { stream ->
                        val arr = stream.readBytes()
                        Log.d(TAG, "getParts: $arr")
                        Log.d(TAG, "getParts: size = ${arr.size}")
                        Log.d(TAG, "getParts: US_ASCII->  ${arr.toString(Charsets.US_ASCII)}")
                        Log.d(TAG, "getParts: UTF_8-> ${arr.toString(Charsets.UTF_8)}")
                        Base64.encodeToString(arr, Base64.DEFAULT)
                    })
                }
            }
            jsonArray.add(part)
        }

        return jsonArray
    }

    @SuppressLint("NewApi")
    private fun usePart(partId: Long, block: (InputStream) -> String): String {
        val partUri = if (isQPlus()) {
            Telephony.Mms.Part.CONTENT_URI.buildUpon().appendPath(partId.toString()).build()
        } else {
            Uri.parse("content://mms/part/$partId")
        }
        try {
            val stream = context.contentResolver.openInputStream(partUri)
            if (stream == null) {
                val msg = "failed opening stream for mms part $partUri"
                Log.e(TAG, msg)
                return ""
            }
            stream.use {
                return block(stream)
            }
        } catch (e: IOException) {
            val msg = "failed to read MMS part on $partUri"
            Log.e(TAG, msg, e)
            return ""
        }
    }

    @SuppressLint("NewApi")
    private fun getMMSAddresses(messageId: Long): JsonArray {
        val jsonArray = JsonArray()
        val addressUri = if (isQPlus()) {
            Telephony.Mms.Addr.getAddrUriForMessage(messageId.toString())
        } else {
            Uri.parse("content://mms/$messageId/addr")
        }

        val projection = arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE)
        val selection = "${Telephony.Mms.Addr.MSG_ID}= ?"
        val selectionArgs = arrayOf(messageId.toString())

        context.queryCursor(addressUri, null, selection, selectionArgs) { cursor ->
            val part = cursor.rowsToJson()
            jsonArray.add(part)
//            when (cursor.getIntValue(Telephony.Mms.Addr.TYPE)) {
//                PduHeaders.FROM, PduHeaders.TO, PduHeaders.CC, PduHeaders.BCC -> jsonArray.add(cursor.getStringValue(Telephony.Mms.Addr.ADDRESS))
//            }
        }

        return jsonArray
    }
}
