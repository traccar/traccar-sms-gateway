package com.simplemobiletools.smsmessenger.dialogs

import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.extensions.config
import kotlinx.android.synthetic.main.dialog_export_messages.view.export_messages_filename
import kotlinx.android.synthetic.main.dialog_export_messages.view.export_mms_checkbox
import kotlinx.android.synthetic.main.dialog_export_messages.view.export_sms_checkbox

class ExportMessagesDialog(
    private val activity: SimpleActivity,
    private val callback: (fileName: String) -> Unit,
) {
    private val config = activity.config

    init {
        val view = (activity.layoutInflater.inflate(R.layout.dialog_export_messages, null) as ViewGroup).apply {
            export_sms_checkbox.isChecked = config.exportSms
            export_mms_checkbox.isChecked = config.exportMms
            export_messages_filename.setText(
                activity.getString(R.string.messages) + "_" + activity.getCurrentFormattedDateTime()
            )
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this, R.string.export_messages) { alertDialog ->
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        config.exportSms = view.export_sms_checkbox.isChecked
                        config.exportMms = view.export_mms_checkbox.isChecked
                        val filename = view.export_messages_filename.value
                        when {
                            filename.isEmpty() -> activity.toast(R.string.empty_name)
                            filename.isAValidFilename() -> {
                                callback(filename)
                                alertDialog.dismiss()
                            }

                            else -> activity.toast(R.string.invalid_name)
                        }
                    }
                }
            }
    }
}
