package com.simplemobiletools.smsmessenger.adapters

import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.formatDateOrTime
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.FastScroller
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.extensions.deleteThread
import com.simplemobiletools.smsmessenger.extensions.getNameLetter
import com.simplemobiletools.smsmessenger.extensions.getNotificationLetterIcon
import com.simplemobiletools.smsmessenger.helpers.refreshMessages
import com.simplemobiletools.smsmessenger.models.Message
import kotlinx.android.synthetic.main.item_message.view.*

class MessagesAdapter(
    activity: SimpleActivity, var messages: ArrayList<Message>,
    recyclerView: MyRecyclerView,
    fastScroller: FastScroller,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, fastScroller, itemClick) {

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_messages

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_select_all -> selectAll()
            R.id.cab_delete -> askConfirmDelete()
        }
    }

    override fun getSelectableItemCount() = messages.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = messages.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = messages.indexOfFirst { it.id == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_message, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        holder.bindView(message, true, true) { itemView, layoutPosition ->
            setupView(itemView, message)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = messages.size

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val items = resources.getQuantityString(R.plurals.delete_conversations, itemsCnt, itemsCnt)

        val baseString = R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                deleteThreads()
            }
        }
    }

    private fun deleteThreads() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val messagesToRemove = messages.filter { selectedKeys.contains(it.id) } as ArrayList<Message>
        val positions = getSelectedItemPositions()
        messagesToRemove.forEach {
            activity.deleteThread(it.thread)
        }
        messages.removeAll(messagesToRemove)

        activity.runOnUiThread {
            if (messagesToRemove.isEmpty()) {
                refreshMessages()
                finishActMode()
            } else {
                removeSelectedItems(positions)
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            Glide.with(activity).clear(holder.itemView.message_image)
        }
    }

    private fun setupView(view: View, message: Message) {
        view.apply {
            message_frame.isSelected = selectedKeys.contains(message.id)

            message_address.text = message.getThreadTitle()
            message_body_short.text = message.body
            message_date.text = message.date.formatDateOrTime(context, true)

            if (message.read) {
                message_address.setTypeface(null, Typeface.NORMAL)
                message_body_short.setTypeface(null, Typeface.NORMAL)
                message_body_short.alpha = 0.7f
            } else {
                message_address.setTypeface(null, Typeface.BOLD)
                message_body_short.setTypeface(null, Typeface.BOLD)
                message_body_short.alpha = 1f
            }

            arrayListOf<TextView>(message_address, message_body_short, message_date).forEach {
                it.setTextColor(textColor)
            }

            val participant = message.participants.first()
            val uri = Uri.parse(participant.photoUri)
            val placeholder = BitmapDrawable(activity.resources, activity.getNotificationLetterIcon(participant.name.getNameLetter()))
            val options = RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .error(placeholder)
                .centerCrop()

            Glide.with(context)
                .load(uri)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(placeholder)
                .apply(options)
                .apply(RequestOptions.circleCropTransform())
                .into(message_image)
        }
    }
}
