package com.simplemobiletools.smsmessenger.adapters

import android.content.Intent
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.bumptech.glide.Glide
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.KEY_PHONE
import com.simplemobiletools.commons.helpers.SimpleContactsHelper
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isNougatPlus
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.refreshMessages
import com.simplemobiletools.smsmessenger.models.Conversation
import kotlinx.android.synthetic.main.item_conversation.view.*

class ConversationsAdapter(
    activity: SimpleActivity, var conversations: ArrayList<Conversation>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {
    private var fontSize = activity.getTextSize()
    private var drafts = HashMap<Long, String?>()

    init {
        setupDragListener(true)
        fetchDrafts(drafts)
    }

    override fun getActionMenuId() = R.menu.cab_conversations

    override fun prepareActionMode(menu: Menu) {
        val selectedItems = getSelectedItems()

        menu.apply {
            findItem(R.id.cab_block_number).isVisible = isNougatPlus()
            findItem(R.id.cab_add_number_to_contact).isVisible = isOneItemSelected() && selectedItems.firstOrNull()?.isGroupConversation == false
            findItem(R.id.cab_dial_number).isVisible = isOneItemSelected() && selectedItems.firstOrNull()?.isGroupConversation == false
            findItem(R.id.cab_copy_number).isVisible = isOneItemSelected() && selectedItems.firstOrNull()?.isGroupConversation == false
            findItem(R.id.cab_mark_as_read).isVisible = selectedItems.any { !it.read }
            findItem(R.id.cab_mark_as_unread).isVisible = selectedItems.any { it.read }
            checkPinBtnVisibility(this)
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
            R.id.cab_mark_as_read -> markAsRead()
            R.id.cab_mark_as_unread -> markAsUnread()
            R.id.cab_pin_conversation -> pinConversation(true)
            R.id.cab_unpin_conversation -> pinConversation(false)
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = conversations.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = conversations.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = conversations.indexOfFirst { it.hashCode() == key }

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

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            Glide.with(activity).clear(holder.itemView.conversation_image)
        }
    }

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
        activity.dialNumber(conversation.phoneNumber) {
            finishActMode()
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

        val conversationsToRemove = conversations.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
        val positions = getSelectedItemPositions()
        conversationsToRemove.forEach {
            activity.deleteConversation(it.threadId)
            activity.notificationManager.cancel(it.hashCode())
        }

        try {
            conversations.removeAll(conversationsToRemove.toSet())
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

    private fun markAsRead() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsMarkedAsRead = conversations.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
        ensureBackgroundThread {
            conversationsMarkedAsRead.filter { conversation -> !conversation.read }.forEach {
                activity.markThreadMessagesRead(it.threadId)
            }

            activity.runOnUiThread {
                refreshMessages()
                finishActMode()
            }
        }
    }

    private fun markAsUnread() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsMarkedAsUnread = conversations.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
        ensureBackgroundThread {
            conversationsMarkedAsUnread.filter { conversation -> conversation.read }.forEach {
                activity.markThreadMessagesUnread(it.threadId)
            }

            activity.runOnUiThread {
                refreshMessages()
                finishActMode()
            }
        }
    }

    private fun addNumberToContact() {
        val conversation = getSelectedItems().firstOrNull() ?: return
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, conversation.phoneNumber)
            activity.launchActivityIntent(this)
        }
    }

    private fun getSelectedItems() = conversations.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>

    private fun pinConversation(pin: Boolean) {
        val conversations = getSelectedItems()
        if (conversations.isEmpty()) {
            return
        }

        if (pin) {
            activity.config.addPinnedConversations(conversations)
        } else {
            activity.config.removePinnedConversations(conversations)
        }

        activity.runOnUiThread {
            refreshMessages()
            finishActMode()
        }
    }

    private fun checkPinBtnVisibility(menu: Menu) {
        val pinnedConversations = activity.config.pinnedConversations
        val selectedConversations = getSelectedItems()
        menu.findItem(R.id.cab_pin_conversation).isVisible = selectedConversations.any { !pinnedConversations.contains(it.threadId.toString()) }
        menu.findItem(R.id.cab_unpin_conversation).isVisible = selectedConversations.any { pinnedConversations.contains(it.threadId.toString()) }
    }

    private fun fetchDrafts(drafts: HashMap<Long, String?>) {
        drafts.clear()
        for ((threadId, draft) in activity.getAllDrafts()) {
            drafts[threadId] = draft
        }
    }

    fun updateFontSize() {
        fontSize = activity.getTextSize()
        notifyDataSetChanged()
    }

    fun updateConversations(newConversations: ArrayList<Conversation>) {
        val latestConversations = newConversations.clone() as ArrayList<Conversation>
        val oldHashCode = conversations.hashCode()
        val newHashCode = latestConversations.hashCode()
        if (newHashCode != oldHashCode) {
            conversations = latestConversations
            notifyDataSetChanged()
        }
    }

    fun updateDrafts() {
        val newDrafts = HashMap<Long, String?>()
        fetchDrafts(newDrafts)
        if (drafts.hashCode() != newDrafts.hashCode()) {
            drafts = newDrafts
            notifyDataSetChanged()
        }
    }

    private fun setupView(view: View, conversation: Conversation) {
        view.apply {
            val smsDraft = drafts[conversation.threadId]
            draft_indicator.beVisibleIf(smsDraft != null)
            draft_indicator.setTextColor(properPrimaryColor)

            pin_indicator.beVisibleIf(activity.config.pinnedConversations.contains(conversation.threadId.toString()))

            conversation_frame.isSelected = selectedKeys.contains(conversation.hashCode())

            conversation_address.apply {
                text = conversation.title
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 1.2f)
            }

            conversation_body_short.apply {
                text = smsDraft ?: conversation.snippet
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.9f)
            }

            conversation_date.apply {
                text = conversation.date.formatDateOrTime(context, true, false)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

            val style = if (conversation.read) {
                conversation_body_short.alpha = 0.7f
                if (conversation.isScheduled) Typeface.ITALIC else Typeface.NORMAL
            } else {
                conversation_body_short.alpha = 1f
                if (conversation.isScheduled) Typeface.BOLD_ITALIC else Typeface.BOLD

            }
            conversation_address.setTypeface(null, style)
            conversation_body_short.setTypeface(null, style)

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

    override fun onChange(position: Int) = conversations.getOrNull(position)?.title ?: ""
}
