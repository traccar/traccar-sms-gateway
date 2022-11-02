package com.simplemobiletools.smsmessenger.adapters

import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.smsmessenger.activities.VCardViewerActivity
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.extensions.isImageMimeType
import com.simplemobiletools.smsmessenger.extensions.isVideoMimeType
import com.simplemobiletools.smsmessenger.extensions.launchViewIntent
import com.simplemobiletools.smsmessenger.helpers.*
import com.simplemobiletools.smsmessenger.models.AttachmentSelection
import kotlinx.android.synthetic.main.item_attachment_media_preview.view.*
import kotlinx.android.synthetic.main.item_remove_attachment_button.view.*

class AttachmentsAdapter(
    val activity: BaseSimpleActivity,
    val onItemClick: (AttachmentSelection) -> Unit,
    val onAttachmentsRemoved: () -> Unit,
    val onReady: (() -> Unit)
) : ListAdapter<AttachmentSelection, AttachmentsAdapter.ViewHolder>(AttachmentDiffCallback()) {

    private val config = activity.config
    private val resources = activity.resources
    private val primaryColor = activity.getProperPrimaryColor()
    private val imageCompressor by lazy { ImageCompressor(activity) }

    val attachments = mutableListOf<AttachmentSelection>()

    fun clear() {
        attachments.clear()
        submitList(ArrayList())
        onAttachmentsRemoved()
    }

    fun addAttachment(attachment: AttachmentSelection) {
        attachments.removeAll { AttachmentSelection.areItemsTheSame(it, attachment) }
        attachments.add(attachment)
        submitList(attachments.toList())
    }

    private fun removeAttachment(attachment: AttachmentSelection) {
        attachments.removeAll { AttachmentSelection.areItemsTheSame(it, attachment) }
        if (attachments.isEmpty()) {
            onAttachmentsRemoved()
        } else {
            submitList(ArrayList(attachments))
        }
    }

    override fun getItemViewType(position: Int): Int {
        return getItem(position).viewType
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutRes = when (viewType) {
            ATTACHMENT_DOCUMENT -> com.simplemobiletools.smsmessenger.R.layout.item_attachment_document_preview
            ATTACHMENT_VCARD -> com.simplemobiletools.smsmessenger.R.layout.item_attachment_vcard_preview
            ATTACHMENT_MEDIA -> com.simplemobiletools.smsmessenger.R.layout.item_attachment_media_preview
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }

        val view = activity.layoutInflater.inflate(layoutRes, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val attachment = getItem(position)
        holder.bindView(attachment, allowSingleClick = true, allowLongClick = false) { view, position ->
            when (attachment.viewType) {
                ATTACHMENT_DOCUMENT -> {
                    view.setupDocumentPreview(
                        uri = attachment.uri,
                        title = attachment.filename,
                        attachment = true,
                        onClick = { activity.launchViewIntent(attachment.uri, attachment.mimetype, attachment.filename) },
                        onRemoveButtonClicked = { removeAttachment(attachment) }
                    )
                }
                ATTACHMENT_VCARD -> {
                    view.setupVCardPreview(
                        activity = activity,
                        uri = attachment.uri,
                        attachment = true,
                        onClick = {
                            val intent = Intent(activity, VCardViewerActivity::class.java).also {
                                it.putExtra(EXTRA_VCARD_URI, attachment.uri)
                            }
                            activity.startActivity(intent)
                        },
                        onRemoveButtonClicked = { removeAttachment(attachment) }
                    )
                }
                ATTACHMENT_MEDIA -> setupMediaPreview(view, attachment)
            }
        }
    }

    private fun setupMediaPreview(view: View, attachment: AttachmentSelection) {
        view.apply {
            media_attachment_holder.background.applyColorFilter(primaryColor.darkenColor())
            media_attachment_holder.setOnClickListener {
                activity.launchViewIntent(attachment.uri, attachment.mimetype, attachment.filename)
            }
            remove_attachment_button.apply {
                beVisible()
                background.applyColorFilter(primaryColor)
                setOnClickListener {
                    removeAttachment(attachment)
                }
            }

            if (attachment.mimetype.isImageMimeType() && attachment.isPending && config.mmsFileSizeLimit != FILE_SIZE_NONE) {
                thumbnail.beGone()
                compression_progress.beVisible()

                imageCompressor.compressImage(attachment.uri, config.mmsFileSizeLimit) { compressedUri ->
                    activity.runOnUiThread {
                        when (compressedUri) {
                            attachment.uri -> {
                                attachments.find { it.uri == attachment.uri }?.isPending = false
                                loadMediaPreview(view, attachment)
                            }
                            null -> {
                                activity.toast(com.simplemobiletools.smsmessenger.R.string.compress_error)
                                removeAttachment(attachment)
                            }
                            else -> {
                                attachments.remove(attachment)
                                addAttachment(attachment.copy(uri = compressedUri, isPending = false))
                            }
                        }
                        onReady()
                    }
                }
            } else {
                loadMediaPreview(view, attachment)
            }
        }
    }

    private fun loadMediaPreview(view: View, attachment: AttachmentSelection) {
        val roundedCornersRadius = resources.getDimension(com.simplemobiletools.smsmessenger.R.dimen.activity_margin).toInt()
        val size = resources.getDimension(com.simplemobiletools.smsmessenger.R.dimen.attachment_preview_size).toInt()

        val options = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .transform(CenterCrop(), RoundedCorners(roundedCornersRadius))

        Glide.with(view.thumbnail)
            .load(attachment.uri)
            .transition(DrawableTransitionOptions.withCrossFade())
            .override(size, size)
            .apply(options)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                    removeAttachment(attachment)
                    activity.toast(com.simplemobiletools.smsmessenger.R.string.unknown_error_occurred)
                    return false
                }

                override fun onResourceReady(dr: Drawable?, a: Any?, t: Target<Drawable>?, d: DataSource?, i: Boolean): Boolean {
                    view.thumbnail.beVisible()
                    view.play_icon.beVisibleIf(attachment.mimetype.isVideoMimeType())
                    view.compression_progress.beGone()
                    return false
                }
            })
            .into(view.thumbnail)
    }

    open inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bindView(
            any: AttachmentSelection,
            allowSingleClick: Boolean,
            allowLongClick: Boolean,
            callback: (itemView: View, adapterPosition: Int) -> Unit
        ): View {
            return itemView.apply {
                callback(this, adapterPosition)

                if (allowSingleClick) {
                    setOnClickListener { viewClicked(any) }
                    setOnLongClickListener { if (allowLongClick) viewLongClicked() else viewClicked(any); true }
                } else {
                    setOnClickListener(null)
                    setOnLongClickListener(null)
                }
            }
        }

        private fun viewClicked(any: AttachmentSelection) {
            onItemClick.invoke(any)
        }

        private fun viewLongClicked() {

        }
    }
}

private class AttachmentDiffCallback : DiffUtil.ItemCallback<AttachmentSelection>() {
    override fun areItemsTheSame(oldItem: AttachmentSelection, newItem: AttachmentSelection): Boolean {
        return AttachmentSelection.areItemsTheSame(oldItem, newItem)
    }

    override fun areContentsTheSame(oldItem: AttachmentSelection, newItem: AttachmentSelection): Boolean {
        return AttachmentSelection.areContentsTheSame(oldItem, newItem)
    }

}
