package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import com.google.gson.Gson
import com.google.gson.stream.JsonWriter
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.extensions.getConversationIds
import java.io.OutputStream

class MessagesExporter(private val context: Context) {
    enum class ExportResult {
        EXPORT_FAIL, EXPORT_OK
    }

    private val config = context.config
    private val messageReader = MessagesReader(context)
    private val gson = Gson()

    fun exportMessages(outputStream: OutputStream?, onProgress: (total: Int, current: Int) -> Unit = { _, _ -> }, callback: (result: ExportResult) -> Unit) {
        ensureBackgroundThread {
            if (outputStream == null) {
                callback.invoke(ExportResult.EXPORT_FAIL)
                return@ensureBackgroundThread
            }
            val writer = JsonWriter(outputStream.bufferedWriter())
            writer.use {
                try {
                    var written = 0
                    writer.beginArray()
                    val conversationIds = context.getConversationIds()
                    val totalMessages = messageReader.getMessagesCount()
                    for (threadId in conversationIds) {
                        writer.beginObject()
                        if (config.exportSms && messageReader.getSmsCount() > 0) {
                            writer.name("sms")
                            writer.beginArray()
                            messageReader.forEachSms(threadId) {
                                writer.jsonValue(gson.toJson(it))
                                written++
                                onProgress.invoke(totalMessages, written)
                            }
                            writer.endArray()
                        }

                        if (config.exportMms && messageReader.getMmsCount() > 0) {
                            writer.name("mms")
                            writer.beginArray()
                            messageReader.forEachMms(threadId) {
                                writer.jsonValue(gson.toJson(it))
                                written++
                                onProgress.invoke(totalMessages, written)
                            }

                            writer.endArray()
                        }

                        writer.endObject()
                    }
                    writer.endArray()
                    callback.invoke(ExportResult.EXPORT_OK)
                } catch (e: Exception) {
                    callback.invoke(ExportResult.EXPORT_FAIL)
                }
            }
        }
    }
}
