package com.simplemobiletools.smsmessenger.dialogs

import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.smsmessenger.databinding.DialogSelectTextBinding

// helper dialog for selecting just a part of a message, not copying the whole into clipboard
class SelectTextDialog(val activity: BaseSimpleActivity, val text: String) {
    init {
        val binding = DialogSelectTextBinding.inflate(activity.layoutInflater).apply {
            dialogSelectTextValue.text = text
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok) { _, _ -> { } }
            .apply {
                activity.setupDialogStuff(binding.root, this)
            }
    }
}
