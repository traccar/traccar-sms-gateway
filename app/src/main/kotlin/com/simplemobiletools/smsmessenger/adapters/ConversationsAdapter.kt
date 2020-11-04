package com.simplemobiletools.smsmessenger.adapters

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.TextUtils
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.bumptech.glide.Glide
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.KEY_PHONE
import com.simplemobiletools.commons.helpers.SimpleContactsHelper
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isNougatPlus
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
    private var fontSize = activity.getTextSize()

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_conversations

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_block_number).isVisible = isNougatPlus()
            findItem(R.id.cab_add_number_to_contact).isVisible = isOneItemSelected() && getSelectedItems().firstOrNull()?.isGroupConversation == false
            findItem(R.id.cab_dial_number).isVisible = isOneItemSelected() && getSelectedItems().firstOrNull()?.isGroupConversation == false
            findItem(R.id.cab_copy_number).isVisible = isOneItemSelected() && getSelectedItems().firstOrNull()?.isGroupConversation == false
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_add_number_to_contact -> addNumberToContact()
            R.id.cab_block_number -> askConfirmBlock()
            R.id.cab_dial_number -> dialNumber()
            R.id.cab_copy_number -> copyNumberToClipboard()
            R.id.cab_delete -> askConfirmDelete()
        }
    }

    override fun getSelectableItemCount() = conversations.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = conversations.getOrNull(position)?.thread_id

    override fun getItemKeyPosition(key: Int) = conversations.indexOfFirst { it.thread_id == key }

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

    private fun askConfirmBlock() {
        val numbers = getSelectedItems().distinctBy { it.phoneNumber }.map { it.phoneNumber }
        val numbersString = TextUtils.join(", ", numbers)
        val question = String.format(resources.getString(R.string.block_confirmation), numbersString)

        ConfirmationDialog(activity, question) {
            blockNumbers()
        }
    }

    private fun blockNumbers() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val numbersToBlock = getSelectedItems()
        val positions = getSelectedItemPositions()
        conversations.removeAll(numbersToBlock)

        ensureBackgroundThread {
            numbersToBlock.map { it.phoneNumber }.forEach { number ->
                activity.addBlockedNumber(number)
            }

            activity.runOnUiThread {
                removeSelectedItems(positions)
                finishActMode()
            }
        }
    }

    private fun dialNumber() {
        val conversation = getSelectedItems().firstOrNull() ?: return
        Intent(Intent.ACTION_DIAL).apply {
            data = Uri.fromParts("tel", conversation.phoneNumber, null)

            if (resolveActivity(activity.packageManager) != null) {
                activity.startActivity(this)
                finishActMode()
            } else {
                activity.toast(R.string.no_app_found)
            }
        }
    }

    private fun copyNumberToClipboard() {
        val conversation = getSelectedItems().firstOrNull() ?: return
        activity.copyToClipboard(conversation.phoneNumber)
        finishActMode()
    }

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

        val conversationsToRemove = conversations.filter { selectedKeys.contains(it.thread_id) } as ArrayList<Conversation>
        val positions = getSelectedItemPositions()
        conversationsToRemove.forEach {
            activity.deleteConversation(it.thread_id)
            activity.notificationManager.cancel(it.thread_id)
        }

        try {
            conversations.removeAll(conversationsToRemove)
        } catch (ignored: Exception) {
        }

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

    private fun addNumberToContact() {
        val conversation = getSelectedItems().firstOrNull() ?: return
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, conversation.phoneNumber)

            if (resolveActivity(activity.packageManager) != null) {
                activity.startActivity(this)
            } else {
                activity.toast(R.string.no_app_found)
            }
        }
    }

    private fun getSelectedItems() = conversations.filter { selectedKeys.contains(it.thread_id) } as ArrayList<Conversation>

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            Glide.with(activity).clear(holder.itemView.conversation_image)
        }
    }

    fun updateFontSize() {
        fontSize = activity.getTextSize()
        notifyDataSetChanged()
    }

    fun updateConversations(newConversations: ArrayList<Conversation>) {
        val oldHashCode = conversations.hashCode()
        val newHashCode = newConversations.hashCode()
        if (newHashCode != oldHashCode) {
            conversations = newConversations
            notifyDataSetChanged()
        }
    }

    private fun setupView(view: View, conversation: Conversation) {
        view.apply {
            conversation_frame.isSelected = selectedKeys.contains(conversation.thread_id)

            conversation_address.apply {
                text = conversation.title
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 1.2f)
            }

            conversation_body_short.apply {
                text = conversation.snippet
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.9f)
            }

            conversation_date.apply {
                text = conversation.date.formatDateOrTime(context, true)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

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
