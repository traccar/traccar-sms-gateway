package com.simplemobiletools.smsmessenger.adapters

import android.graphics.Typeface
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.bumptech.glide.Glide
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.formatDateOrTime
import com.simplemobiletools.commons.helpers.SimpleContactsHelper
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.FastScroller
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.extensions.deleteConversation
import com.simplemobiletools.smsmessenger.helpers.refreshMessages
import com.simplemobiletools.smsmessenger.models.Conversation
import kotlinx.android.synthetic.main.item_conversation.view.*

class ConversationsAdapter(activity: SimpleActivity, var conversations: ArrayList<Conversation>, recyclerView: MyRecyclerView, fastScroller: FastScroller,
                           itemClick: (Any) -> Unit) : MyRecyclerViewAdapter(activity, recyclerView, fastScroller, itemClick) {

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_conversations

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

    override fun getSelectableItemCount() = conversations.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = conversations.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = conversations.indexOfFirst { it.id == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_conversation, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversation = conversations[position]
        holder.bindView(conversation, true, true) { itemView, layoutPosition ->
            setupView(itemView, conversation)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = conversations.size

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val items = resources.getQuantityString(R.plurals.delete_conversations, itemsCnt, itemsCnt)

        val baseString = R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                deleteConversations()
            }
        }
    }

    private fun deleteConversations() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsToRemove = conversations.filter { selectedKeys.contains(it.id) } as ArrayList<Conversation>
        val positions = getSelectedItemPositions()
        conversationsToRemove.forEach {
            activity.deleteConversation(it.id)
        }
        conversations.removeAll(conversationsToRemove)

        activity.runOnUiThread {
            if (conversationsToRemove.isEmpty()) {
                refreshMessages()
                finishActMode()
            } else {
                removeSelectedItems(positions)
                if (conversations.isEmpty()) {
                    refreshMessages()
                }
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            Glide.with(activity).clear(holder.itemView.conversation_image)
        }
    }

    private fun setupView(view: View, conversation: Conversation) {
        view.apply {
            conversation_frame.isSelected = selectedKeys.contains(conversation.id)

            conversation_address.text = conversation.title
            conversation_body_short.text = conversation.snippet
            conversation_date.text = conversation.date.formatDateOrTime(context, true)

            if (conversation.read) {
                conversation_address.setTypeface(null, Typeface.NORMAL)
                conversation_body_short.setTypeface(null, Typeface.NORMAL)
                conversation_body_short.alpha = 0.7f
            } else {
                conversation_address.setTypeface(null, Typeface.BOLD)
                conversation_body_short.setTypeface(null, Typeface.BOLD)
                conversation_body_short.alpha = 1f
            }

            arrayListOf<TextView>(conversation_address, conversation_body_short, conversation_date).forEach {
                it.setTextColor(textColor)
            }

            // at group conversations we use an icon as the placeholder, not any letter
            val placeholder = if (conversation.isGroupConversation) {
                SimpleContactsHelper(context).getColoredGroupIcon(conversation.title)
            } else {
                null
            }

            SimpleContactsHelper(context).loadContactImage(conversation.photoUri, conversation_image, conversation.title, placeholder)
        }
    }
}
