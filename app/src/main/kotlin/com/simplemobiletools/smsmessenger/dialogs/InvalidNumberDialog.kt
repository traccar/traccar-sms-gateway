package com.simplemobiletools.smsmessenger.dialogs

import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.smsmessenger.R
import kotlinx.android.synthetic.main.dialog_invalid_number.view.*

class InvalidNumberDialog(val activity: BaseSimpleActivity, val text: String) {
    init {
        val view = activity.layoutInflater.inflate(R.layout.dialog_invalid_number, null).apply {
            dialog_invalid_number_desc.text = text
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok) { _, _ -> { } }
            .apply {
                activity.setupDialogStuff(view, this)
            }
    }
}
