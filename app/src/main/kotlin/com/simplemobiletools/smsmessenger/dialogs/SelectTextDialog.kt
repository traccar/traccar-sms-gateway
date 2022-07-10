package com.simplemobiletools.smsmessenger.dialogs

import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.smsmessenger.R
import kotlinx.android.synthetic.main.dialog_select_text.view.*

// helper dialog for selecting just a part of a message, not copying the whole into clipboard
class SelectTextDialog(val activity: BaseSimpleActivity, val text: String) {
    init {
        val view = activity.layoutInflater.inflate(R.layout.dialog_select_text, null).apply {
            dialog_select_text_value.text = text
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok) { dialog, which -> { } }
            .apply {
                activity.setupDialogStuff(view, this)
            }
    }
}
