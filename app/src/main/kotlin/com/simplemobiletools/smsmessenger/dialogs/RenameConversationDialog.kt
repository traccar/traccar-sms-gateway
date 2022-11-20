package com.simplemobiletools.smsmessenger.dialogs

import android.app.Activity
import android.content.DialogInterface.BUTTON_POSITIVE
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.models.Conversation
import kotlinx.android.synthetic.main.dialog_rename_conversation.view.*

class RenameConversationDialog(
    private val activity: Activity,
    private val conversation: Conversation,
    private val callback: (name: String) -> Unit,
) {

    private var dialog: AlertDialog? = null

    init {
        val textColor = activity.getProperTextColor()
        val primaryColor = activity.getProperPrimaryColor()
        val backgroundColor = activity.getProperBackgroundColor()

        val view = (activity.layoutInflater.inflate(R.layout.dialog_rename_conversation, null) as ViewGroup).apply {
            rename_conv_input_layout.apply {
                setColors(textColor, primaryColor, backgroundColor)
                setBoxCornerRadiiResources(R.dimen.medium_margin, R.dimen.medium_margin, R.dimen.medium_margin, R.dimen.medium_margin)
            }
            rename_conv_edit_text.apply {
                setTextColor(textColor)
                if (conversation.usesCustomTitle) {
                    setText(conversation.title)
                }
                hint = conversation.title

                doAfterTextChanged {
                    dialog?.getButton(BUTTON_POSITIVE)?.isEnabled = !it.isNullOrEmpty()
                }
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this, R.string.rename_conversation) { alertDialog ->
                    dialog = alertDialog
                    alertDialog.showKeyboard(view.rename_conv_edit_text)
                    alertDialog.getButton(BUTTON_POSITIVE).apply {
                        val newTitle = view.rename_conv_edit_text.text.toString()
                        isEnabled = newTitle.isNotEmpty() && (newTitle != conversation.title)
                        setOnClickListener {
                            alertDialog.dismiss()
                            callback(view.rename_conv_edit_text.text.toString())
                        }
                    }
                }
            }
    }
}
