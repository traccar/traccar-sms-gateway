package com.simplemobiletools.smsmessenger.dialogs

import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.smsmessenger.R
import kotlinx.android.synthetic.main.dialog_select_text.view.*

// helper dialog for selecting just a part of a message, not copying the whole into clipboard
class SelectTextDialog(val activity: BaseSimpleActivity, val text: String) {
    init {
        val view = activity.layoutInflater.inflate(R.layout.dialog_select_text, null).apply {
            dialog_select_text_value.text = text
        }

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok) { dialog, which -> { } }
            .create().apply {
                activity.setupDialogStuff(view, this)
            }
    }
}
