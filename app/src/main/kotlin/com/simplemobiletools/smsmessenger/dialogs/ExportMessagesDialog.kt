package com.simplemobiletools.smsmessenger.dialogs

import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.dialogs.FilePickerDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.helpers.EXPORT_FILE_EXT
import kotlinx.android.synthetic.main.dialog_export_messages.view.*
import java.io.File

class ExportMessagesDialog(
    private val activity: SimpleActivity,
    private val path: String,
    private val hidePath: Boolean,
    private val callback: (file: File) -> Unit,
) {
    private var realPath = if (path.isEmpty()) activity.internalStoragePath else path
    private val config = activity.config

    init {
        val view = (activity.layoutInflater.inflate(R.layout.dialog_export_messages, null) as ViewGroup).apply {
            export_messages_folder.setText(activity.humanizePath(realPath))
            export_messages_filename.setText("${activity.getString(R.string.messages)}_${activity.getCurrentFormattedDateTime()}")
            export_sms_checkbox.isChecked = config.exportSms
            export_mms_checkbox.isChecked = config.exportMms

            if (hidePath) {
                export_messages_folder_hint.beGone()
            } else {
                export_messages_folder.setOnClickListener {
                    activity.hideKeyboard(export_messages_filename)
                    FilePickerDialog(activity, realPath, false, showFAB = true) {
                        export_messages_folder.setText(activity.humanizePath(it))
                        realPath = it
                    }
                }
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this, R.string.export_messages) { alertDialog ->
                    alertDialog.showKeyboard(view.export_messages_filename)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val filename = view.export_messages_filename.value
                        when {
                            filename.isEmpty() -> activity.toast(R.string.empty_name)
                            filename.isAValidFilename() -> {
                                val file = File(realPath, "$filename$EXPORT_FILE_EXT")
                                if (!hidePath && file.exists()) {
                                    activity.toast(R.string.name_taken)
                                    return@setOnClickListener
                                }

                                if (!view.export_sms_checkbox.isChecked && !view.export_mms_checkbox.isChecked) {
                                    activity.toast(R.string.no_option_selected)
                                    return@setOnClickListener
                                }

                                config.exportSms = view.export_sms_checkbox.isChecked
                                config.exportMms = view.export_mms_checkbox.isChecked
                                config.lastExportPath = file.absolutePath.getParentPath()
                                callback(file)
                                alertDialog.dismiss()
                            }
                            else -> activity.toast(R.string.invalid_name)
                        }
                    }
                }
            }
    }
}
