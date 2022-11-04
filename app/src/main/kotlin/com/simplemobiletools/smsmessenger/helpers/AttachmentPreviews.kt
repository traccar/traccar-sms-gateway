package com.simplemobiletools.smsmessenger.helpers

import android.app.Activity
import android.net.Uri
import android.view.View
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.SimpleContactsHelper
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.extensions.*
import kotlinx.android.synthetic.main.item_attachment_document.view.*
import kotlinx.android.synthetic.main.item_attachment_vcard.view.*
import kotlinx.android.synthetic.main.item_attachment_vcard_preview.view.*
import kotlinx.android.synthetic.main.item_remove_attachment_button.view.*

fun View.setupDocumentPreview(
    uri: Uri,
    title: String,
    mimeType: String,
    attachment: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onRemoveButtonClicked: (() -> Unit)? = null
) {
    if (title.isNotEmpty()) {
        filename.text = title
    }

    try {
        val size = context.getFileSizeFromUri(uri)
        file_size.beVisible()
        file_size.text = size.formatSize()
    } catch (e: Exception) {
        file_size.beGone()
    }

    val textColor = context.getProperTextColor()
    val primaryColor = context.getProperPrimaryColor()

    document_attachment_holder.background.applyColorFilter(textColor)
    filename.setTextColor(textColor)
    file_size.setTextColor(textColor)

    icon.setImageResource(getIconResourceForMimeType(mimeType))
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

fun View.setupVCardPreview(
    activity: Activity,
    uri: Uri,
    attachment: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onRemoveButtonClicked: (() -> Unit)? = null,
) {
    val textColor = activity.getProperTextColor()
    val primaryColor = activity.getProperPrimaryColor()

    vcard_attachment_holder.background.applyColorFilter(primaryColor.darkenColor())
    vcard_title.setTextColor(textColor)
    vcard_subtitle.setTextColor(textColor)

    if (attachment) {
        vcard_progress.beVisible()
    }
    arrayOf(vcard_photo, vcard_title, vcard_subtitle, view_contact_details).forEach {
        it.beGone()
    }

    parseVCardFromUri(activity, uri) { vCards ->
        activity.runOnUiThread {
            if (vCards.isEmpty()) {
                vcard_title.beVisible()
                vcard_title.text = context.getString(R.string.unknown_error_occurred)
                return@runOnUiThread
            }
            val title = vCards.firstOrNull()?.parseNameFromVCard()
            val imageIcon = if (title != null) {
                SimpleContactsHelper(activity).getContactLetterIcon(title)
            } else {
                null
            }

            arrayOf(vcard_photo, vcard_title).forEach {
                it.beVisible()
            }

            vcard_photo.setImageBitmap(imageIcon)
            vcard_title.text = title

            if (vCards.size > 1) {
                vcard_subtitle.beVisible()
                val quantity = vCards.size - 1
                vcard_subtitle.text = resources.getQuantityString(R.plurals.and_other_contacts, quantity, quantity)
            } else {
                vcard_subtitle.beGone()
            }

            if (attachment) {
                vcard_progress.beGone()
                remove_attachment_button.apply {
                    beVisible()
                    background.applyColorFilter(primaryColor)
                    if (onRemoveButtonClicked != null) {
                        setOnClickListener {
                            onRemoveButtonClicked.invoke()
                        }
                    }
                }
            } else {
                view_contact_details.setTextColor(primaryColor)
                view_contact_details.beVisible()
            }

            vcard_attachment_holder.setOnClickListener {
                onClick?.invoke()
            }
            vcard_attachment_holder.setOnLongClickListener {
                onLongClick?.invoke()
                true
            }
        }
    }
}

private fun getIconResourceForMimeType(mimeType: String) = when {
    mimeType.isAudioMimeType() -> R.drawable.ic_vector_audio_file
    mimeType.isCalendarMimeType() -> R.drawable.ic_calendar_month_vector
    mimeType.isPdfMimeType() -> R.drawable.ic_vector_pdf
    mimeType.isZipMimeType() -> R.drawable.ic_vector_folder_zip
    else -> R.drawable.ic_document_vector
}
