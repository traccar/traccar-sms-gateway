package com.simplemobiletools.smsmessenger.dialogs

import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.helpers.MessagesImporter
import com.simplemobiletools.smsmessenger.helpers.MessagesImporter.ImportResult.IMPORT_FAIL
import com.simplemobiletools.smsmessenger.helpers.MessagesImporter.ImportResult.IMPORT_NOTHING_NEW
import com.simplemobiletools.smsmessenger.helpers.MessagesImporter.ImportResult.IMPORT_OK
import com.simplemobiletools.smsmessenger.helpers.MessagesImporter.ImportResult.IMPORT_PARTIAL
import kotlinx.android.synthetic.main.dialog_import_messages.view.import_mms_checkbox
import kotlinx.android.synthetic.main.dialog_import_messages.view.import_sms_checkbox

class ImportMessagesDialog(
    private val activity: SimpleActivity,
    private val path: String,
    private val callback: (refresh: Boolean) -> Unit,
) {

    private val config = activity.config

    init {
        val view = (activity.layoutInflater.inflate(R.layout.dialog_import_messages, null) as ViewGroup).apply {
            import_sms_checkbox.isChecked = config.importSms
            import_mms_checkbox.isChecked = config.importMms
        }

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view, this, R.string.import_messages) {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        activity.toast(R.string.importing)
                        config.importSms = view.import_sms_checkbox.isChecked
                        config.importMms = view.import_mms_checkbox.isChecked
                        ensureBackgroundThread {
                            MessagesImporter(activity).importMessages(path) { result ->
                                when (result) {
                                    IMPORT_FAIL -> activity.toast(R.string.importing_failed)
                                    IMPORT_OK -> activity.toast(R.string.importing_successful)
                                    IMPORT_PARTIAL -> activity.toast(R.string.importing_some_entries_failed)
                                    IMPORT_NOTHING_NEW -> activity.toast(R.string.no_entries_for_importing)
                                }
                            }
                            dismiss()
                            callback.invoke(true)
                        }
                    }
                }
            }
    }
}
