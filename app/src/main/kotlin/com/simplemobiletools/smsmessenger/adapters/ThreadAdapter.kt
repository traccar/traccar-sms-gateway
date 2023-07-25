package com.simplemobiletools.smsmessenger.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.Size
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.simplemobiletools.commons.adapters.MyRecyclerViewListAdapter
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.SimpleContactsHelper
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.NewConversationActivity
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.activities.ThreadActivity
import com.simplemobiletools.smsmessenger.activities.VCardViewerActivity
import com.simplemobiletools.smsmessenger.dialogs.DeleteConfirmationDialog
import com.simplemobiletools.smsmessenger.dialogs.MessageDetailsDialog
import com.simplemobiletools.smsmessenger.dialogs.SelectTextDialog
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.*
import com.simplemobiletools.smsmessenger.models.Attachment
import com.simplemobiletools.smsmessenger.models.Message
import com.simplemobiletools.smsmessenger.models.ThreadItem
import com.simplemobiletools.smsmessenger.models.ThreadItem.*
import kotlinx.android.synthetic.main.item_attachment_image.view.*
import kotlinx.android.synthetic.main.item_received_message.view.*
import kotlinx.android.synthetic.main.item_received_message.view.thread_mesage_attachments_holder
import kotlinx.android.synthetic.main.item_received_message.view.thread_message_body
import kotlinx.android.synthetic.main.item_received_message.view.thread_message_holder
import kotlinx.android.synthetic.main.item_received_message.view.thread_message_play_outline
import kotlinx.android.synthetic.main.item_sent_message.view.*
import kotlinx.android.synthetic.main.item_thread_date_time.view.*
import kotlinx.android.synthetic.main.item_thread_error.view.*
import kotlinx.android.synthetic.main.item_thread_loading.view.*
import kotlinx.android.synthetic.main.item_thread_sending.view.*
import kotlinx.android.synthetic.main.item_thread_success.view.*

class ThreadAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit,
    val isRecycleBin: Boolean,
    val deleteMessages: (messages: List<Message>, toRecycleBin: Boolean, fromRecycleBin: Boolean) -> Unit
) : MyRecyclerViewListAdapter<ThreadItem>(activity, recyclerView, ThreadItemDiffCallback(), itemClick) {
    private var fontSize = activity.getTextSize()

    @SuppressLint("MissingPermission")
    private val hasMultipleSIMCards = (activity.subscriptionManagerCompat().activeSubscriptionInfoList?.size ?: 0) > 1
    private val maxChatBubbleWidth = activity.usableScreenSize.x * 0.8f

    init {
        setupDragListener(true)
        setHasStableIds(true)
    }

    override fun getActionMenuId() = R.menu.cab_thread

    override fun prepareActionMode(menu: Menu) {
        val isOneItemSelected = isOneItemSelected()
        val selectedItem = getSelectedItems().firstOrNull() as? Message
        val hasText = selectedItem?.body != null && selectedItem.body != ""
        menu.apply {
            findItem(R.id.cab_copy_to_clipboard).isVisible = isOneItemSelected && hasText
            findItem(R.id.cab_save_as).isVisible = isOneItemSelected && selectedItem?.attachment?.attachments?.size == 1
            findItem(R.id.cab_share).isVisible = isOneItemSelected && hasText
            findItem(R.id.cab_forward_message).isVisible = isOneItemSelected
            findItem(R.id.cab_select_text).isVisible = isOneItemSelected && hasText
            findItem(R.id.cab_properties).isVisible = isOneItemSelected
            findItem(R.id.cab_restore).isVisible = isRecycleBin
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_copy_to_clipboard -> copyToClipboard()
            R.id.cab_save_as -> saveAs()
            R.id.cab_share -> shareText()
            R.id.cab_forward_message -> forwardMessage()
            R.id.cab_select_text -> selectText()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_restore -> askConfirmRestore()
            R.id.cab_select_all -> selectAll()
            R.id.cab_properties -> showMessageDetails()
        }
    }

    override fun getSelectableItemCount() = currentList.filterIsInstance<Message>().size

    override fun getIsItemSelectable(position: Int) = !isThreadDateTime(position)

    override fun getItemSelectionKey(position: Int) = (currentList.getOrNull(position) as? Message)?.hashCode()

    override fun getItemKeyPosition(key: Int) = currentList.indexOfFirst { (it as? Message)?.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = when (viewType) {
            THREAD_LOADING -> R.layout.item_thread_loading
            THREAD_DATE_TIME -> R.layout.item_thread_date_time
            THREAD_RECEIVED_MESSAGE -> R.layout.item_received_message
            THREAD_SENT_MESSAGE_ERROR -> R.layout.item_thread_error
            THREAD_SENT_MESSAGE_SENT -> R.layout.item_thread_success
            THREAD_SENT_MESSAGE_SENDING -> R.layout.item_thread_sending
            else -> R.layout.item_sent_message
        }
        return createViewHolder(layout, parent)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val isClickable = item is ThreadError || item is Message
        val isLongClickable = item is Message
        holder.bindView(item, isClickable, isLongClickable) { itemView, _ ->
            when (item) {
                is ThreadLoading -> setupThreadLoading(itemView)
                is ThreadDateTime -> setupDateTime(itemView, item)
                is ThreadError -> setupThreadError(itemView)
                is ThreadSent -> setupThreadSuccess(itemView, item.delivered)
                is ThreadSending -> setupThreadSending(itemView)
                is Message -> setupView(holder, itemView, item)
            }
        }
        bindViewHolder(holder)
    }

    override fun getItemId(position: Int): Long {
        return when (val item = getItem(position)) {
            is Message -> Message.getStableId(item)
            else -> item.hashCode().toLong()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is ThreadLoading -> THREAD_LOADING
            is ThreadDateTime -> THREAD_DATE_TIME
            is ThreadError -> THREAD_SENT_MESSAGE_ERROR
            is ThreadSent -> THREAD_SENT_MESSAGE_SENT
            is ThreadSending -> THREAD_SENT_MESSAGE_SENDING
            is Message -> if (item.isReceivedMessage()) THREAD_RECEIVED_MESSAGE else THREAD_SENT_MESSAGE
        }
    }

    private fun copyToClipboard() {
        val firstItem = getSelectedItems().firstOrNull() as? Message ?: return
        activity.copyToClipboard(firstItem.body)
    }

    private fun saveAs() {
        val firstItem = getSelectedItems().firstOrNull() as? Message ?: return
        val attachment = firstItem.attachment?.attachments?.first() ?: return
        (activity as ThreadActivity).saveMMS(attachment.mimetype, attachment.uriString)
    }

    private fun shareText() {
        val firstItem = getSelectedItems().firstOrNull() as? Message ?: return
        activity.shareTextIntent(firstItem.body)
    }

    private fun selectText() {
        val firstItem = getSelectedItems().firstOrNull() as? Message ?: return
        if (firstItem.body.trim().isNotEmpty()) {
            SelectTextDialog(activity, firstItem.body)
        }
    }

    private fun showMessageDetails() {
        val message = getSelectedItems().firstOrNull() as? Message ?: return
        MessageDetailsDialog(activity, message)
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size

        // not sure how we can get UnknownFormatConversionException here, so show the error and hope that someone reports it
        val items = try {
            resources.getQuantityString(R.plurals.delete_messages, itemsCnt, itemsCnt)
        } catch (e: Exception) {
            activity.showErrorToast(e)
            return
        }

        val baseString = if (activity.config.useRecycleBin && !isRecycleBin) {
            R.string.move_to_recycle_bin_confirmation
        } else {
            R.string.deletion_confirmation
        }
        val question = String.format(resources.getString(baseString), items)

        DeleteConfirmationDialog(activity, question, activity.config.useRecycleBin && !isRecycleBin) { skipRecycleBin ->
            ensureBackgroundThread {
                val messagesToRemove = getSelectedItems()
                if (messagesToRemove.isNotEmpty()) {
                    val toRecycleBin = !skipRecycleBin && activity.config.useRecycleBin && !isRecycleBin
                    deleteMessages(messagesToRemove.filterIsInstance<Message>(), toRecycleBin, false)
                }
            }
        }
    }

    private fun askConfirmRestore() {
        val itemsCnt = selectedKeys.size

        // not sure how we can get UnknownFormatConversionException here, so show the error and hope that someone reports it
        val items = try {
            resources.getQuantityString(R.plurals.delete_messages, itemsCnt, itemsCnt)
        } catch (e: Exception) {
            activity.showErrorToast(e)
            return
        }

        val baseString = R.string.restore_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                val messagesToRestore = getSelectedItems()
                if (messagesToRestore.isNotEmpty()) {
                    deleteMessages(messagesToRestore.filterIsInstance<Message>(), false, true)
                }
            }
        }
    }

    private fun forwardMessage() {
        val message = getSelectedItems().firstOrNull() as? Message ?: return
        val attachment = message.attachment?.attachments?.firstOrNull()
        Intent(activity, NewConversationActivity::class.java).apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, message.body)

            if (attachment != null) {
                putExtra(Intent.EXTRA_STREAM, attachment.getUri())
            }

            activity.startActivity(this)
        }
    }

    private fun getSelectedItems() = currentList.filter { selectedKeys.contains((it as? Message)?.hashCode() ?: 0) } as ArrayList<ThreadItem>

    private fun isThreadDateTime(position: Int) = currentList.getOrNull(position) is ThreadDateTime

    fun updateMessages(newMessages: ArrayList<ThreadItem>, scrollPosition: Int = -1) {
        val latestMessages = newMessages.toMutableList()
        submitList(latestMessages) {
            if (scrollPosition != -1) {
                recyclerView.scrollToPosition(scrollPosition)
            }
        }
    }

    private fun setupView(holder: ViewHolder, view: View, message: Message) {
        view.apply {
            thread_message_holder.isSelected = selectedKeys.contains(message.hashCode())
            thread_message_body.apply {
                text = message.body
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            }
            thread_message_body.beVisibleIf(message.body.isNotEmpty())

            if (message.isReceivedMessage()) {
                setupReceivedMessageView(view, message)
            } else {
                setupSentMessageView(view, message)
            }

            thread_message_body.setOnLongClickListener {
                holder.viewLongClicked()
                true
            }

            thread_message_body.setOnClickListener {
                holder.viewClicked(message)
            }

            if (message.attachment?.attachments?.isNotEmpty() == true) {
                thread_mesage_attachments_holder.beVisible()
                thread_mesage_attachments_holder.removeAllViews()
                for (attachment in message.attachment.attachments) {
                    val mimetype = attachment.mimetype
                    when {
                        mimetype.isImageMimeType() || mimetype.isVideoMimeType() -> setupImageView(holder, view, message, attachment)
                        mimetype.isVCardMimeType() -> setupVCardView(holder, view, message, attachment)
                        else -> setupFileView(holder, view, message, attachment)
                    }

                    thread_message_play_outline.beVisibleIf(mimetype.startsWith("video/"))
                }
            } else {
                thread_mesage_attachments_holder.beGone()
            }
        }
    }

    private fun setupReceivedMessageView(view: View, message: Message) {
        view.apply {
            thread_message_sender_photo.beVisible()
            thread_message_sender_photo.setOnClickListener {
                val contact = message.getSender()!!
                context.getContactFromAddress(contact.phoneNumbers.first().normalizedNumber) {
                    if (it != null) {
                        activity.startContactDetailsIntent(it)
                    }
                }
            }
            thread_message_body.setTextColor(textColor)
            thread_message_body.setLinkTextColor(context.getProperPrimaryColor())

            if (!activity.isFinishing && !activity.isDestroyed) {
                val contactLetterIcon = SimpleContactsHelper(context).getContactLetterIcon(message.senderName)
                val placeholder = BitmapDrawable(context.resources, contactLetterIcon)

                val options = RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .error(placeholder)
                    .centerCrop()

                Glide.with(context)
                    .load(message.senderPhotoUri)
                    .placeholder(placeholder)
                    .apply(options)
                    .apply(RequestOptions.circleCropTransform())
                    .into(thread_message_sender_photo)
            }
        }
    }

    private fun setupSentMessageView(view: View, message: Message) {
        view.apply {
            thread_message_sender_photo?.beGone()
            val background = context.getProperPrimaryColor()
            thread_message_body.background.applyColorFilter(background)

            val contrastColor = background.getContrastColor()
            thread_message_body.setTextColor(contrastColor)
            thread_message_body.setLinkTextColor(contrastColor)

            val padding = thread_message_body.paddingStart
            if (message.isScheduled) {
                thread_message_scheduled_icon.beVisible()
                thread_message_scheduled_icon.applyColorFilter(contrastColor)

                val iconWidth = resources.getDimensionPixelSize(R.dimen.small_icon_size)
                val rightPadding = padding + iconWidth
                thread_message_body.setPadding(padding, padding, rightPadding, padding)
                thread_message_body.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            } else {
                thread_message_scheduled_icon.beGone()

                thread_message_body.setPadding(padding, padding, padding, padding)
                thread_message_body.typeface = Typeface.DEFAULT
            }
        }
    }

    private fun setupImageView(holder: ViewHolder, parent: View, message: Message, attachment: Attachment) {
        val mimetype = attachment.mimetype
        val uri = attachment.getUri()
        parent.apply {
            val imageView = layoutInflater.inflate(R.layout.item_attachment_image, null)
            thread_mesage_attachments_holder.addView(imageView)

            val placeholderDrawable = ColorDrawable(Color.TRANSPARENT)
            val isTallImage = attachment.height > attachment.width
            val transformation = if (isTallImage) CenterCrop() else FitCenter()
            val options = RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .placeholder(placeholderDrawable)
                .transform(transformation)

            var builder = Glide.with(context)
                .load(uri)
                .apply(options)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                        thread_message_play_outline.beGone()
                        thread_mesage_attachments_holder.removeView(imageView)
                        return false
                    }

                    override fun onResourceReady(dr: Drawable?, a: Any?, t: Target<Drawable>?, d: DataSource?, i: Boolean) = false
                })

            // limit attachment sizes to avoid causing OOM
            var wantedAttachmentSize = Size(attachment.width, attachment.height)
            if (wantedAttachmentSize.width > maxChatBubbleWidth) {
                val newHeight = wantedAttachmentSize.height / (wantedAttachmentSize.width / maxChatBubbleWidth)
                wantedAttachmentSize = Size(maxChatBubbleWidth.toInt(), newHeight.toInt())
            }

            builder = if (isTallImage) {
                builder.override(wantedAttachmentSize.width, wantedAttachmentSize.width)
            } else {
                builder.override(wantedAttachmentSize.width, wantedAttachmentSize.height)
            }

            try {
                builder.into(imageView.attachment_image)
            } catch (ignore: Exception) {
            }

            imageView.attachment_image.setOnClickListener {
                if (actModeCallback.isSelectable) {
                    holder.viewClicked(message)
                } else {
                    activity.launchViewIntent(uri, mimetype, attachment.filename)
                }
            }
            imageView.setOnLongClickListener {
                holder.viewLongClicked()
                true
            }
        }
    }

    private fun setupVCardView(holder: ViewHolder, parent: View, message: Message, attachment: Attachment) {
        val uri = attachment.getUri()
        parent.apply {
            val vCardView = layoutInflater.inflate(R.layout.item_attachment_vcard, null).apply {
                setupVCardPreview(
                    activity = activity,
                    uri = uri,
                    onClick = {
                        if (actModeCallback.isSelectable) {
                            holder.viewClicked(message)
                        } else {
                            val intent = Intent(context, VCardViewerActivity::class.java).also {
                                it.putExtra(EXTRA_VCARD_URI, uri)
                            }
                            context.startActivity(intent)
                        }
                    },
                    onLongClick = { holder.viewLongClicked() }
                )
            }
            thread_mesage_attachments_holder.addView(vCardView)
        }
    }

    private fun setupFileView(holder: ViewHolder, parent: View, message: Message, attachment: Attachment) {
        val mimetype = attachment.mimetype
        val uri = attachment.getUri()
        parent.apply {
            val attachmentView = layoutInflater.inflate(R.layout.item_attachment_document, null).apply {
                setupDocumentPreview(
                    uri = uri,
                    title = attachment.filename,
                    mimeType = attachment.mimetype,
                    onClick = {
                        if (actModeCallback.isSelectable) {
                            holder.viewClicked(message)
                        } else {
                            activity.launchViewIntent(uri, mimetype, attachment.filename)
                        }
                    },
                    onLongClick = { holder.viewLongClicked() },
                )
            }
            thread_mesage_attachments_holder.addView(attachmentView)
        }
    }

    private fun setupDateTime(view: View, dateTime: ThreadDateTime) {
        view.apply {
            thread_date_time.apply {
                text = dateTime.date.formatDateOrTime(context, false, false)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            }
            thread_date_time.setTextColor(textColor)

            thread_sim_icon.beVisibleIf(hasMultipleSIMCards)
            thread_sim_number.beVisibleIf(hasMultipleSIMCards)
            if (hasMultipleSIMCards) {
                thread_sim_number.text = dateTime.simID
                thread_sim_number.setTextColor(textColor.getContrastColor())
                thread_sim_icon.applyColorFilter(textColor)
            }
        }
    }

    private fun setupThreadSuccess(view: View, isDelivered: Boolean) {
        view.thread_success.setImageResource(if (isDelivered) R.drawable.ic_check_double_vector else R.drawable.ic_check_vector)
        view.thread_success.applyColorFilter(textColor)
    }

    private fun setupThreadError(view: View) {
        view.thread_error.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize - 4)
    }

    private fun setupThreadSending(view: View) {
        view.thread_sending.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            setTextColor(textColor)
        }
    }

    private fun setupThreadLoading(view: View) = view.thread_loading.setIndicatorColor(properPrimaryColor)

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing && holder.itemView.thread_message_sender_photo != null) {
            Glide.with(activity).clear(holder.itemView.thread_message_sender_photo)
        }
    }
}

private class ThreadItemDiffCallback : DiffUtil.ItemCallback<ThreadItem>() {

    override fun areItemsTheSame(oldItem: ThreadItem, newItem: ThreadItem): Boolean {
        if (oldItem::class.java != newItem::class.java) return false
        return when (oldItem) {
            is ThreadLoading -> oldItem.id == (newItem as ThreadLoading).id
            is ThreadDateTime -> oldItem.date == (newItem as ThreadDateTime).date
            is ThreadError -> oldItem.messageId == (newItem as ThreadError).messageId
            is ThreadSent -> oldItem.messageId == (newItem as ThreadSent).messageId
            is ThreadSending -> oldItem.messageId == (newItem as ThreadSending).messageId
            is Message -> Message.areItemsTheSame(oldItem, newItem as Message)
        }
    }

    override fun areContentsTheSame(oldItem: ThreadItem, newItem: ThreadItem): Boolean {
        if (oldItem::class.java != newItem::class.java) return false
        return when (oldItem) {
            is ThreadLoading, is ThreadSending -> true
            is ThreadDateTime -> oldItem.simID == (newItem as ThreadDateTime).simID
            is ThreadError -> oldItem.messageText == (newItem as ThreadError).messageText
            is ThreadSent -> oldItem.delivered == (newItem as ThreadSent).delivered
            is Message -> Message.areContentsTheSame(oldItem, newItem as Message)
        }
    }
}
