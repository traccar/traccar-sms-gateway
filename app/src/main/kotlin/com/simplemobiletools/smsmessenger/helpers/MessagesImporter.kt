package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import android.net.Uri
import android.provider.Telephony.*
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.extensions.queryCursor
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.MessagesImporter.ImportResult.*
import com.simplemobiletools.smsmessenger.models.ExportedMessage
import java.io.File

class MessagesImporter(private val context: Context) {
    companion object {
        private const val TAG = "MessagesImporter"
    }

    enum class ImportResult {
        IMPORT_FAIL, IMPORT_OK, IMPORT_PARTIAL
    }

    private val gson = Gson()
    private val messageWriter = MessagesWriter(context)
    private val config = context.config
    private var messagesImported = 0
    private var messagesFailed = 0

    fun importMessages(path: String, onProgress: (total: Int, current: Int) -> Unit = { _, _ -> }, callback: (result: ImportResult) -> Unit) {
        ensureBackgroundThread {
            try {

                val inputStream = if (path.contains("/")) {
                    File(path).inputStream()
                } else {
                    context.assets.open(path)
                }

                inputStream.bufferedReader().use { reader ->
                    val json = reader.readText()
                    Log.d(TAG, "importMessages: json== $json")
                    val type = object : TypeToken<List<ExportedMessage>>() {}.type
                    val messages = gson.fromJson<List<ExportedMessage>>(json, type)
                    val totalMessages = messages.flatMap { it.sms }.size + messages.flatMap { it.mms }.size
                    onProgress.invoke(totalMessages, messagesImported)
                    for (message in messages) {
                        // add sms
                        if (config.importSms) {
                            message.sms.forEach { backup ->
                                messageWriter.writeSmsMessage(backup)
                                messagesImported++
                                onProgress.invoke(totalMessages, messagesImported)
                            }
                        }
                        // add mms
                        if (config.importMms) {
                            message.mms.forEach { backup ->
                                messageWriter.writeMmsMessage(backup)
                                messagesImported++
                                onProgress.invoke(totalMessages, messagesImported)
                            }
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
                    }
                }
            } catch (e: Exception) {
                context.showErrorToast(e)
                messagesFailed++
            }

            callback.invoke(
                when {
                    messagesImported == 0 -> {
                        IMPORT_FAIL
                    }
                    messagesFailed > 0 -> IMPORT_PARTIAL
                    else -> IMPORT_OK
                }
            )
        }
    }
}
