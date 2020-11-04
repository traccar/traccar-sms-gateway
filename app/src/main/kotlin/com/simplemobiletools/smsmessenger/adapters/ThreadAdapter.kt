package com.simplemobiletools.smsmessenger.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.telephony.SubscriptionManager
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.SimpleContactsHelper
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.FastScroller
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.dialogs.SelectTextDialog
import com.simplemobiletools.smsmessenger.extensions.deleteMessage
import com.simplemobiletools.smsmessenger.helpers.*
import com.simplemobiletools.smsmessenger.models.*
import kotlinx.android.synthetic.main.item_attachment_image.view.*
import kotlinx.android.synthetic.main.item_received_message.view.*
import kotlinx.android.synthetic.main.item_received_unknown_attachment.view.*
import kotlinx.android.synthetic.main.item_sent_unknown_attachment.view.*
import kotlinx.android.synthetic.main.item_thread_date_time.view.*
import kotlinx.android.synthetic.main.item_thread_error.view.*
import kotlinx.android.synthetic.main.item_thread_success.view.*

class ThreadAdapter(activity: SimpleActivity, var messages: ArrayList<ThreadItem>, recyclerView: MyRecyclerView, fastScroller: FastScroller,
                    itemClick: (Any) -> Unit) : MyRecyclerViewAdapter(activity, recyclerView, fastScroller, itemClick) {
    private val roundedCornersRadius = resources.getDimension(R.dimen.normal_margin).toInt()
    private var fontSize = activity.getTextSize()

    @SuppressLint("MissingPermission")
    private val hasMultipleSIMCards = SubscriptionManager.from(activity).activeSubscriptionInfoList?.size ?: 0 > 1

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_thread

    override fun prepareActionMode(menu: Menu) {
        val isOneItemSelected = isOneItemSelected()
        menu.apply {
            findItem(R.id.cab_copy_to_clipboard).isVisible = isOneItemSelected
            findItem(R.id.cab_share).isVisible = isOneItemSelected
            findItem(R.id.cab_select_text).isVisible = isOneItemSelected
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_copy_to_clipboard -> copyToClipboard()
            R.id.cab_share -> shareText()
            R.id.cab_select_text -> selectText()
            R.id.cab_delete -> askConfirmDelete()
        }
    }

    override fun getSelectableItemCount() = messages.filter { it is Message }.size

    override fun getIsItemSelectable(position: Int) = !isThreadDateTime(position)

    override fun getItemSelectionKey(position: Int) = (messages.getOrNull(position) as? Message)?.id

    override fun getItemKeyPosition(key: Int) = messages.indexOfFirst { (it as? Message)?.id == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = when (viewType) {
            THREAD_DATE_TIME -> R.layout.item_thread_date_time
            THREAD_RECEIVED_MESSAGE -> R.layout.item_received_message
            THREAD_SENT_MESSAGE_ERROR -> R.layout.item_thread_error
            THREAD_SENT_MESSAGE_SUCCESS -> R.layout.item_thread_success
            else -> R.layout.item_sent_message
        }
        return createViewHolder(layout, parent)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = messages[position]
        holder.bindView(item, true, item is Message) { itemView, layoutPosition ->
            when (item) {
                is ThreadDateTime -> setupDateTime(itemView, item)
                is ThreadSuccess -> setupThreadSuccess(itemView)
                is ThreadError -> setupThreadError(itemView)
                else -> setupView(itemView, item as Message)
            }
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = messages.size

    override fun getItemViewType(position: Int): Int {
        val item = messages[position]
        return when {
            item is ThreadDateTime -> THREAD_DATE_TIME
            (messages[position] as? Message)?.isReceivedMessage() == true -> THREAD_RECEIVED_MESSAGE
            item is ThreadError -> THREAD_SENT_MESSAGE_ERROR
            item is ThreadSuccess -> THREAD_SENT_MESSAGE_SUCCESS
            else -> THREAD_SENT_MESSAGE
        }
    }

    private fun copyToClipboard() {
        val firstItem = getSelectedItems().first() as? Message ?: return
        activity.copyToClipboard(firstItem.body)
    }

    private fun shareText() {
        val firstItem = getSelectedItems().first() as? Message ?: return
        activity.shareTextIntent(firstItem.body)
    }

    private fun selectText() {
        val firstItem = getSelectedItems().first() as? Message ?: return
        if (firstItem.body.trim().isNotEmpty()) {
            SelectTextDialog(activity, firstItem.body)
        }
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val items = resources.getQuantityString(R.plurals.delete_messages, itemsCnt, itemsCnt)

        val baseString = R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                deleteMessages()
            }
        }
    }

    private fun deleteMessages() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val messagesToRemove = messages.filter { selectedKeys.contains((it as? Message)?.id ?: 0) } as ArrayList<ThreadItem>
        val positions = getSelectedItemPositions()
        messagesToRemove.forEach {
            activity.deleteMessage((it as Message).id, it.isMMS)
        }
        messages.removeAll(messagesToRemove)

        activity.runOnUiThread {
            if (messages.filter { it is Message }.isEmpty()) {
                activity.finish()
            } else {
                removeSelectedItems(positions)
            }
            refreshMessages()
        }
    }

    private fun getSelectedItems() = messages.filter { selectedKeys.contains((it as? Message)?.id ?: 0) } as ArrayList<ThreadItem>

    private fun isThreadDateTime(position: Int) = messages.getOrNull(position) is ThreadDateTime

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing && holder.itemView.thread_message_sender_photo != null) {
            Glide.with(activity).clear(holder.itemView.thread_message_sender_photo)
        }
    }

    private fun setupView(view: View, message: Message) {
        view.apply {
            thread_message_holder.isSelected = selectedKeys.contains(message.id)
            thread_message_body.apply {
                text = message.body
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            }
            thread_message_body.beVisibleIf(message.body.isNotEmpty())

            if (message.isReceivedMessage()) {
                thread_message_sender_photo.beVisible()
                thread_message_body.setTextColor(textColor)
                thread_message_body.setLinkTextColor(context.getAdjustedPrimaryColor())
                SimpleContactsHelper(context).loadContactImage(message.senderPhotoUri, thread_message_sender_photo, message.senderName)
            } else {
                thread_message_sender_photo?.beGone()
                val background = context.getAdjustedPrimaryColor()
                thread_message_body.background.applyColorFilter(background.adjustAlpha(0.8f))

                val contrastColor = background.getContrastColor()
                thread_message_body.setTextColor(contrastColor)
                thread_message_body.setLinkTextColor(contrastColor)
            }

            thread_mesage_attachments_holder.removeAllViews()
            if (message.attachment?.attachments?.isNotEmpty() == true) {
                for (attachment in message.attachment.attachments) {
                    val mimetype = attachment.mimetype
                    val uri = attachment.uri
                    if (mimetype.startsWith("image/") || mimetype.startsWith("video/")) {
                        val imageView = layoutInflater.inflate(R.layout.item_attachment_image, null)
                        thread_mesage_attachments_holder.addView(imageView)

                        val isTallImage = attachment.height > attachment.width
                        val transformation = if (isTallImage) CenterCrop() else FitCenter()
                        val options = RequestOptions()
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .transform(transformation, RoundedCorners(roundedCornersRadius))

                        var builder = Glide.with(context)
                            .load(uri)
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .apply(options)
                            .listener(object : RequestListener<Drawable> {
                                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                                    thread_message_play_outline.beGone()
                                    thread_mesage_attachments_holder.removeView(imageView)
                                    return false
                                }

                                override fun onResourceReady(dr: Drawable?, a: Any?, t: Target<Drawable>?, d: DataSource?, i: Boolean) =
                                    false
                            })

                        if (isTallImage) {
                            builder = builder.override(attachment.width, attachment.width)
                        }

                        builder.into(imageView.attachment_image)
                        imageView.attachment_image.setOnClickListener { launchViewIntent(uri, mimetype, attachment.filename) }
                    } else {
                        if (message.isReceivedMessage()) {
                            val attachmentView = layoutInflater.inflate(R.layout.item_received_unknown_attachment, null).apply {
                                thread_received_attachment_label.apply {
                                    if (attachment.filename.isNotEmpty()) {
                                        thread_received_attachment_label.text = attachment.filename
                                    }
                                    setTextColor(textColor)
                                    setOnClickListener { launchViewIntent(uri, mimetype, attachment.filename) }
                                }
                            }
                            thread_mesage_attachments_holder.addView(attachmentView)
                        } else {
                            val background = context.getAdjustedPrimaryColor()
                            val attachmentView = layoutInflater.inflate(R.layout.item_sent_unknown_attachment, null).apply {
                                thread_sent_attachment_label.apply {
                                    this.background.applyColorFilter(background.adjustAlpha(0.8f))
                                    setTextColor(background.getContrastColor())
                                    if (attachment.filename.isNotEmpty()) {
                                        thread_sent_attachment_label.text = attachment.filename
                                    }
                                    setOnClickListener { launchViewIntent(uri, mimetype, attachment.filename) }
                                }
                            }
                            thread_mesage_attachments_holder.addView(attachmentView)
                        }
                    }

                    thread_message_play_outline.beVisibleIf(mimetype.startsWith("video/"))
                }
            }
        }
    }

    private fun launchViewIntent(uri: Uri, mimetype: String, filename: String) {
        Intent().apply {
            action = Intent.ACTION_VIEW
            setDataAndType(uri, mimetype)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            if (resolveActivity(activity.packageManager) != null) {
                activity.startActivity(this)
            } else {
                val newMimetype = filename.getMimeType()
                if (newMimetype.isNotEmpty() && mimetype != newMimetype) {
                    launchViewIntent(uri, newMimetype, filename)
                } else {
                    activity.toast(R.string.no_app_found)
                }
            }
        }
    }

    private fun setupDateTime(view: View, dateTime: ThreadDateTime) {
        view.apply {
            thread_date_time.apply {
                text = dateTime.date.formatDateOrTime(context, false)
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

    private fun setupThreadSuccess(view: View) {
        view.thread_success.applyColorFilter(textColor)
    }

    private fun setupThreadError(view: View) {
        view.thread_error.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
    }
}
