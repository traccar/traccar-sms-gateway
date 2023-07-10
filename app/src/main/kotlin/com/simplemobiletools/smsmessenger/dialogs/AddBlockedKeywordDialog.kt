package com.simplemobiletools.smsmessenger.dialogs

import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.extensions.showKeyboard
import com.simplemobiletools.commons.extensions.value
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.extensions.config
import kotlinx.android.synthetic.main.dialog_add_blocked_keyword.view.add_blocked_keyword_edittext

class AddBlockedKeywordDialog(val activity: BaseSimpleActivity, private val originalKeyword: String? = null, val callback: () -> Unit) {
    init {
        val view = activity.layoutInflater.inflate(R.layout.dialog_add_blocked_keyword, null).apply {
            if (originalKeyword != null) {
                add_blocked_keyword_edittext.setText(originalKeyword)
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this) { alertDialog ->
                    alertDialog.showKeyboard(view.add_blocked_keyword_edittext)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val newBlockedKeyword = view.add_blocked_keyword_edittext.value
                        if (originalKeyword != null && newBlockedKeyword != originalKeyword) {
                            activity.config.removeBlockedKeyword(originalKeyword)
                        }

                        if (newBlockedKeyword.isNotEmpty()) {
                            activity.config.addBlockedKeyword(newBlockedKeyword)
                        }

                        callback()
                        alertDialog.dismiss()
                    }
                }
            }
    }
}
