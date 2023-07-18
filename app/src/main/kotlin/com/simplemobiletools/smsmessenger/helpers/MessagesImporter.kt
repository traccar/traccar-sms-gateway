package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.models.*

class MessagesImporter(private val context: Context) {

    private val messageWriter = MessagesWriter(context)
    private val config = context.config
    private var messagesImported = 0
    private var messagesFailed = 0

    fun importMessages(messagesBackup: List<MessagesBackup>, callback: (result: ImportResult) -> Unit) {
        ensureBackgroundThread {
            try {
                messagesBackup.forEach { message ->
                    try {
                        if (message.backupType == BackupType.SMS && config.importSms) {
                            messageWriter.writeSmsMessage(message as SmsBackup)
                        } else if (message.backupType == BackupType.MMS && config.importMms) {
                            messageWriter.writeMmsMessage(message as MmsBackup)
                        }
                        else return@forEach
                        messagesImported++
                    } catch (e: Exception) {
                        context.showErrorToast(e)
                        messagesFailed++
                    }
                }
                refreshMessages()
            } catch (e: Exception) {
                context.showErrorToast(e)
            }

            callback.invoke(
                when {
                    messagesImported == 0 && messagesFailed == 0 -> ImportResult.IMPORT_NOTHING_NEW
                    messagesFailed > 0 && messagesImported > 0 -> ImportResult.IMPORT_PARTIAL
                    messagesFailed > 0 -> ImportResult.IMPORT_FAIL
                    else -> ImportResult.IMPORT_OK
                }
            )
        }
    }
}
