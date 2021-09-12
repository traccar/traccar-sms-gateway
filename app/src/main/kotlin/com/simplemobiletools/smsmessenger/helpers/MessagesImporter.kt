package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import android.provider.Telephony
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

            //read data from path
            // parse json
            // write data to sql db
            val inputStream = if (path.contains("/")) {
                File(path).inputStream()
            } else {
                context.assets.open(path)
            }

            inputStream.bufferedReader().use {
                try {
                    val json = it.readText()
                    Log.d(TAG, "importMessages: json== ${json.length}")
                    val type = object : TypeToken<Map<String, ExportedMessage>>() {}.type
                    val data = gson.fromJson<Map<String, ExportedMessage>>(json, type)
                    Log.d(TAG, "importMessages: ${data.size}")
                    for (message in data.values) {
                        // add sms
                        if (config.importSms) {
                            message.sms.forEach(messageWriter::writeSmsMessage)
                        }

                        // add mms
                        if (config.importMms) {
                            message.sms.forEach(messageWriter::writeMmsMessage)
                        }

//                        messageWriter.updateAllSmsThreads()

                        val conversations = context.getConversations()
                        val conversationIds = context.getConversationIds()
                        Log.w(TAG, "conversations = $conversations")
                        Log.w(TAG, "conversationIds = $conversationIds")
                        context.queryCursor(Telephony.Sms.CONTENT_URI) { cursor ->
                            val json = cursor.rowsToJson()
                            Log.w(TAG, "messages = $json")
                        }

                        refreshMessages()


                    }
                } catch (e: Exception) {
                    Log.e(TAG, "importMessages: ", e)
                    callback.invoke(ImportResult.IMPORT_FAIL)
                }
            }
        }
    }
}
