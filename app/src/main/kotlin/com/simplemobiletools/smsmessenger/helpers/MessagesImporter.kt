package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import android.util.JsonToken
import android.util.Xml
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.helpers.MessagesImporter.ImportResult.IMPORT_FAIL
import com.simplemobiletools.smsmessenger.helpers.MessagesImporter.ImportResult.IMPORT_NOTHING_NEW
import com.simplemobiletools.smsmessenger.helpers.MessagesImporter.ImportResult.IMPORT_OK
import com.simplemobiletools.smsmessenger.helpers.MessagesImporter.ImportResult.IMPORT_PARTIAL
import com.simplemobiletools.smsmessenger.models.MmsBackup
import com.simplemobiletools.smsmessenger.models.SmsBackup
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream

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
                val isXml = if (path.endsWith("txt")) {
                    // Need to read the first line to determine if it is xml
                    val tempStream = getInputStreamForPath(path)
                    tempStream.bufferedReader().use {
                        it.readLine().startsWith("<?xml")
                    }
                } else {
                    path.endsWith("xml")
                }

                val inputStream = getInputStreamForPath(path)

                if (isXml) {
                    inputStream.importXml()
                } else {
                    inputStream.importJson()
                }

            } catch (e: Exception) {
                context.showErrorToast(e)
                messagesFailed++
            }

            callback.invoke(
                when {
                    messagesImported == 0 && messagesFailed == 0 -> IMPORT_NOTHING_NEW
                    messagesFailed > 0 && messagesImported > 0 -> IMPORT_PARTIAL
                    messagesFailed > 0 -> IMPORT_FAIL
                    else -> IMPORT_OK
                }
            )
        }
    }

    private fun getInputStreamForPath(path: String): InputStream {
        return if (path.contains("/")) {
            File(path).inputStream()
        } else {
            context.assets.open(path)
        }
    }

    private fun InputStream.importJson() {
        bufferedReader().use { reader ->
            val jsonReader = gson.newJsonReader(reader)
            val smsMessageType = object : TypeToken<SmsBackup>() {}.type
            val mmsMessageType = object : TypeToken<MmsBackup>() {}.type

            jsonReader.beginArray()
            while (jsonReader.hasNext()) {
                jsonReader.beginObject()
                while (jsonReader.hasNext()) {
                    val nextToken = jsonReader.peek()
                    if (nextToken.ordinal == JsonToken.NAME.ordinal) {
                        val msgType = jsonReader.nextName()

                        if ((!msgType.equals("sms") && !msgType.equals("mms")) ||
                            (msgType.equals("sms") && !config.importSms) ||
                            (msgType.equals("mms") && !config.importMms)
                        ) {
                            jsonReader.skipValue()
                            continue
                        }

                        jsonReader.beginArray()
                        while (jsonReader.hasNext()) {
                            try {
                                if (msgType.equals("sms")) {
                                    val message = gson.fromJson<SmsBackup>(jsonReader, smsMessageType)
                                    messageWriter.writeSmsMessage(message)
                                } else {
                                    val message = gson.fromJson<MmsBackup>(jsonReader, mmsMessageType)
                                    messageWriter.writeMmsMessage(message)
                                }

                                messagesImported++
                            } catch (e: Exception) {
                                context.showErrorToast(e)
                                messagesFailed++

                            }
                        }
                        jsonReader.endArray()
                    } else {
                        jsonReader.skipValue()
                    }
                }

                jsonReader.endObject()
                refreshMessages()
            }

            jsonReader.endArray()
        }
    }

    private fun InputStream.importXml() {
        bufferedReader().use { reader ->
            val xmlParser = Xml.newPullParser().apply {
                setInput(reader)
            }

            xmlParser.nextTag()
            xmlParser.require(XmlPullParser.START_TAG, null, "smses")

            var depth = 1
            while (depth != 0) {
                when (xmlParser.next()) {
                    XmlPullParser.END_TAG -> depth--
                    XmlPullParser.START_TAG -> depth++
                }

                if (xmlParser.eventType != XmlPullParser.START_TAG) {
                    continue
                }

                try {
                    if (xmlParser.name == "sms") {
                        if (config.importSms) {
                            val message = xmlParser.readSms()
                            messageWriter.writeSmsMessage(message)
                            messagesImported++
                        } else {
                            xmlParser.skip()
                        }
                    } else {
                        xmlParser.skip()
                    }
                } catch (e: Exception) {
                    context.showErrorToast(e)
                    messagesFailed++
                }
            }

            refreshMessages()
        }
    }

    private fun XmlPullParser.readSms(): SmsBackup {
        require(XmlPullParser.START_TAG, null, "sms")

        return SmsBackup(
            subscriptionId = 0,
            address = getAttributeValue(null, "address"),
            body = getAttributeValue(null, "body"),
            date = getAttributeValue(null, "date").toLong(),
            dateSent = getAttributeValue(null, "date").toLong(),
            locked = getAttributeValue(null, "locked").toInt(),
            protocol = getAttributeValue(null, "protocol"),
            read = getAttributeValue(null, "read").toInt(),
            status = getAttributeValue(null, "status").toInt(),
            type = getAttributeValue(null, "type").toInt(),
            serviceCenter = getAttributeValue(null, "service_center")
        )
    }

    private fun XmlPullParser.skip() {
        if (eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }
}
