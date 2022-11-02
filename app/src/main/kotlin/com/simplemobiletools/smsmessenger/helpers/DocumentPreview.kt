package com.simplemobiletools.smsmessenger.helpers

import android.net.Uri
import android.view.View
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.smsmessenger.extensions.getFileSizeFromUri
import kotlinx.android.synthetic.main.item_attachment_document.view.*
import kotlinx.android.synthetic.main.item_remove_attachment_button.view.*

fun View.setupDocumentPreview(
    uri: Uri,
    title: String,
    attachment: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onRemoveButtonClicked: (() -> Unit)? = null
) {
    if (title.isNotEmpty()) {
        filename.text = title
    }

    val size = context.getFileSizeFromUri(uri)
    file_size.beVisible()
    file_size.text = size.formatSize()

    val textColor = context.getProperTextColor()
    val primaryColor = context.getProperPrimaryColor()

    document_attachment_holder.background.applyColorFilter(textColor)
    filename.setTextColor(textColor)
    file_size.setTextColor(textColor)
    // todo: set icon drawable based on mime type
    icon.background.setTint(primaryColor)
    document_attachment_holder.background.applyColorFilter(primaryColor.darkenColor())

    if (attachment) {
        remove_attachment_button.apply {
            beVisible()
            background.applyColorFilter(primaryColor)
            if (onRemoveButtonClicked != null) {
                setOnClickListener {
                    onRemoveButtonClicked.invoke()
                }
            }
        }
    }

    document_attachment_holder.setOnClickListener {
        onClick?.invoke()
    }
    document_attachment_holder.setOnLongClickListener {
        onLongClick?.invoke()
        true
    }
}
