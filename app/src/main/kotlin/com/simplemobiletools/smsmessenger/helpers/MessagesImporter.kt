package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.provider.Telephony.*
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.extensions.queryCursor
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.models.ExportedMessage
import java.io.File

class MessagesImporter(private val context: Context) {
    companion object {
        private const val TAG = "MessagesImporter"
    }

    enum class ImportResult {
        IMPORT_FAIL, IMPORT_OK, IMPORT_PARTIAL, IMPORT_NOTHING_NEW
    }

    private val gson = Gson()
    private val messageWriter = MessagesWriter(context)
    private val config = context.config

    fun importMessages(path: String, callback: (result: ImportResult) -> Unit) {
        ensureBackgroundThread {
            if (path.isEmpty()) {
                callback.invoke(ImportResult.IMPORT_FAIL)
                return@ensureBackgroundThread
            }

            val inputStream = if (path.contains("/")) {
                File(path).inputStream()
            } else {
                context.assets.open(path)
            }

            inputStream.bufferedReader().use {
                try {
                    val json = it.readText()
                    Log.d(TAG, "importMessages: json== $json")
                    val type = object : TypeToken<List<ExportedMessage>>() {}.type
                    val messages = gson.fromJson<List<ExportedMessage>>(json, type)
                    Log.d(TAG, "importMessages: ${messages.size}")
                    for (message in messages) {
                        // add sms
                        if (config.importSms) {
                            message.sms.forEach(messageWriter::writeSmsMessage)
                        }
                        // add mms
                        if (config.importMms) {
                            message.mms.forEach(messageWriter::writeMmsMessage)
                        }


                        context.queryCursor(Threads.CONTENT_URI) { cursor ->
                            val json = cursor.rowsToJson()
                            Log.w(TAG, "converations = $json")
                        }

                        context.queryCursor(Sms.CONTENT_URI) { cursor ->
                            val json = cursor.rowsToJson()
                            Log.w(TAG, "smses = $json")
                        }

                        context.queryCursor(Mms.CONTENT_URI) { cursor ->
                            val json = cursor.rowsToJson()
                            Log.w(TAG, "mmses = $json")
                        }

                        context.queryCursor(Uri.parse("content://mms/part")) { cursor ->
                            val json = cursor.rowsToJson()
                            Log.w(TAG, "parts = $json")
                        }

                        refreshMessages()
                        callback.invoke(ImportResult.IMPORT_OK)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "importMessages: ", e)
                    callback.invoke(ImportResult.IMPORT_FAIL)
                }
            }
        }
    }
}
