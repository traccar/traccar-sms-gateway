package com.simplemobiletools.smsmessenger.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Base64
import android.util.Log
import com.google.android.mms.pdu_alt.PduHeaders
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.simplemobiletools.commons.extensions.getIntValue
import com.simplemobiletools.commons.extensions.getStringValue
import com.simplemobiletools.commons.extensions.queryCursor
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
        forEachThreadMessage(Telephony.Sms.CONTENT_URI, threadId, block)
    }

    fun forEachMms(threadId: Long, includeAttachment: Boolean = true, block: (JsonObject) -> Unit) {
        forEachThreadMessage(Telephony.Mms.CONTENT_URI, threadId) { obj ->
            if (includeAttachment) {
                obj.add("parts", getParts(obj.getAsJsonPrimitive("_id").asLong))
            }
            obj.add(Telephony.CanonicalAddressesColumns.ADDRESS, getMMSAddresses(obj.getAsJsonPrimitive("_id").asLong))
            block(obj)
        }
    }

    private fun forEachThreadMessage(contentUri: Uri, threadId: Long, block: (JsonObject) -> Unit) {
        val selection = "${Telephony.Sms.THREAD_ID} = ?"
        val selectionArgs = arrayOf(threadId.toString())
        context.queryCursor(contentUri, null, selection, selectionArgs) { cursor ->
            val json = cursor.rowsToJson()
            forceMillisDate(json, "date")
            forceMillisDate(json, "date_sent")
            block(json)
        }
    }

    private fun forceMillisDate(message: JsonObject, field: String) {
        /* sometimes the sms are in millis and the mms in secs... */
        if (message.get(field).isJsonPrimitive) {
            val value = message.get(field).optLong
            if (value != null && value != 0L && value < 500000000000L) { // 500000000000 = Tuesday, 5 November 1985 00:53:20 GMT
                message.addProperty(field, value * 1000)
            }
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

        val selection = "${Telephony.Mms.Part.MSG_ID}=$mmsId"
        context.queryCursor(uri, emptyArray(), selection) { cursor ->
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
                        Base64.encodeToString(stream.readBytes(), Base64.DEFAULT)
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
        val selection = "${Telephony.Mms.Addr.MSG_ID}=$messageId"

        context.queryCursor(addressUri, projection, selection) { cursor ->
            when (cursor.getIntValue(Telephony.Mms.Addr.TYPE)) {
                PduHeaders.FROM, PduHeaders.TO, PduHeaders.CC, PduHeaders.BCC -> jsonArray.add(cursor.getStringValue(Telephony.Mms.Addr.ADDRESS))
            }
        }

        return jsonArray
    }
}
