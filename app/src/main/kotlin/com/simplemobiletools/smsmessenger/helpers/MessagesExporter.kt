package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
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

    fun exportMessages(
        outputStream: OutputStream?,
        callback: (result: ExportResult) -> Unit,
    ) {
        ensureBackgroundThread {
            if (outputStream == null) {
                callback.invoke(ExportResult.EXPORT_FAIL)
                return@ensureBackgroundThread
            }

            /*
            * We should have json in this format
            *   {
            *       "threadId" : {
            *                   "threadId": ""
            *                   "sms": [{ smses }],
            *                   "mms": [{ mmses }]
            *                    }
            *    }
            *
            * */
            val writer = JsonWriter(outputStream.bufferedWriter())
            writer.use {
                try {
                    var written = 0
                    writer.beginObject()
                    val conversationIds = context.getConversationIds()
                    for (threadId in conversationIds) {
                        writer.name(threadId.toString())

                        writer.beginObject()
                        writer.name("threadId")
                        writer.value(threadId)
                        if (config.exportSms) {
                            writer.name("sms")
                            writer.beginArray()
                            //write all sms
                            messageReader.forEachSms(threadId) {
                                JsonObjectWriter(writer).write(it)
                                written++
                            }
                            writer.endArray()
                        }

                        if (config.exportMms) {
                            writer.name("mms")
                            writer.beginArray()
                            //write all mms
                            messageReader.forEachMms(threadId) {
                                JsonObjectWriter(writer).write(it)
                                written++
                            }

                            writer.endArray()
                        }

                        writer.endObject()
                    }
                    writer.endObject()
                    callback.invoke(ExportResult.EXPORT_OK)
                } catch (e: Exception) {
                    callback.invoke(ExportResult.EXPORT_FAIL)
                }
            }
        }
    }
}
