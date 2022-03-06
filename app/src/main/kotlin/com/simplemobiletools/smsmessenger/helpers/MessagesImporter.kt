package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import android.provider.Telephony.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.MessagesImporter.ImportResult.*
import com.simplemobiletools.smsmessenger.models.ExportedMessage
import java.io.File

class MessagesImporter(private val context: Context) {
    enum class ImportResult {
        IMPORT_FAIL, IMPORT_OK, IMPORT_PARTIAL, IMPORT_NOTHING_NEW
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
                    val type = object : TypeToken<List<ExportedMessage>>() {}.type
                    val messages = gson.fromJson<List<ExportedMessage>>(json, type)
                    val totalMessages = messages.flatMap { it.sms ?: emptyList() }.size + messages.flatMap { it.mms ?: emptyList() }.size
                    if (totalMessages <= 0) {
                        callback.invoke(IMPORT_NOTHING_NEW)
                        return@ensureBackgroundThread
                    }

                    onProgress.invoke(totalMessages, messagesImported)
                    for (message in messages) {
                        if (config.importSms) {
                            message.sms?.forEach { backup ->
                                messageWriter.writeSmsMessage(backup)
                                messagesImported++
                                onProgress.invoke(totalMessages, messagesImported)
                            }
                        }
                        if (config.importMms) {
                            message.mms?.forEach { backup ->
                                messageWriter.writeMmsMessage(backup)
                                messagesImported++
                                onProgress.invoke(totalMessages, messagesImported)
                            }
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
                    messagesImported == 0 -> IMPORT_FAIL
                    messagesFailed > 0 -> IMPORT_PARTIAL
                    else -> IMPORT_OK
                }
            )
        }
    }
}
