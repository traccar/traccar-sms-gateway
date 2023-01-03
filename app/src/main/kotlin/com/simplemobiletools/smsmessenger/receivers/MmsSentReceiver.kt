package com.simplemobiletools.smsmessenger.receivers

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.Telephony
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.smsmessenger.helpers.refreshMessages
import com.simplemobiletools.smsmessenger.messaging.SendStatusReceiver
import java.io.File

/** Handles updating databases and states when a MMS message is sent. */
class MmsSentReceiver : SendStatusReceiver() {

    override fun updateAndroidDatabase(context: Context, intent: Intent, receiverResultCode: Int) {
        val uri = Uri.parse(intent.getStringExtra(EXTRA_CONTENT_URI))
        val values = ContentValues(1)
        values.put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_SENT)
        try {
            context.contentResolver.update(uri, values, null, null)
        } catch (e: SQLiteException) {
            context.showErrorToast(e)
        }

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        if (filePath != null) {
            File(filePath).delete()
        }
    }

    override fun updateAppDatabase(context: Context, intent: Intent, receiverResultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            refreshMessages()
        }
    }

    companion object {
        private const val EXTRA_CONTENT_URI = "content_uri"
        private const val EXTRA_FILE_PATH = "file_path"
    }
}
