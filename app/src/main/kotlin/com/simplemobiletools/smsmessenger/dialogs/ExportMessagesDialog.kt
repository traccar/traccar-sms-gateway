package com.simplemobiletools.smsmessenger.dialogs

import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.databinding.DialogExportMessagesBinding
import com.simplemobiletools.smsmessenger.extensions.config

class ExportMessagesDialog(
    private val activity: SimpleActivity,
    private val callback: (fileName: String) -> Unit,
) {
    private val config = activity.config

    init {
        val binding = DialogExportMessagesBinding.inflate(activity.layoutInflater).apply {
            exportSmsCheckbox.isChecked = config.exportSms
            exportMmsCheckbox.isChecked = config.exportMms
            exportMessagesFilename.setText(
                activity.getString(R.string.messages) + "_" + activity.getCurrentFormattedDateTime()
            )
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok, null)
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.export_messages) { alertDialog ->
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        config.exportSms = binding.exportSmsCheckbox.isChecked
                        config.exportMms = binding.exportMmsCheckbox.isChecked
                        val filename = binding.exportMessagesFilename.value
                        when {
                            filename.isEmpty() -> activity.toast(com.simplemobiletools.commons.R.string.empty_name)
                            filename.isAValidFilename() -> {
                                callback(filename)
                                alertDialog.dismiss()
                            }

                            else -> activity.toast(com.simplemobiletools.commons.R.string.invalid_name)
                        }
                    }
                }
            }
    }
}
