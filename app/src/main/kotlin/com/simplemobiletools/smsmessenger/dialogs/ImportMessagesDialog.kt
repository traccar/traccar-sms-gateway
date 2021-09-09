package com.simplemobiletools.smsmessenger.dialogs

import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.extensions.config
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
                        dismiss()
                        callback.invoke(true)
                    }
                }
            }
    }
}
