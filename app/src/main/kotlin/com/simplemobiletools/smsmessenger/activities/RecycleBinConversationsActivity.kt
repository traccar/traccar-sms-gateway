package com.simplemobiletools.smsmessenger.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.ConversationsAdapter
import com.simplemobiletools.smsmessenger.adapters.RecycleBinConversationsAdapter
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.*
import com.simplemobiletools.smsmessenger.models.Conversation
import com.simplemobiletools.smsmessenger.models.Events
import kotlinx.android.synthetic.main.activity_recycle_bin_conversations.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class RecycleBinConversationsActivity : SimpleActivity() {
    private var bus: EventBus? = null

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recycle_bin_conversations)
        setupOptionsMenu()

        updateMaterialActivityViews(recycle_bin_coordinator, conversations_list, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(conversations_list, recycle_bin_toolbar)

        loadRecycleBinConversations()
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(recycle_bin_toolbar, NavigationIcon.Arrow)
        updateMenuColors()

        loadRecycleBinConversations()
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    private fun setupOptionsMenu() {
        recycle_bin_toolbar.inflateMenu(R.menu.recycle_bin_menu)

        recycle_bin_toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.empty_recycle_bin -> removeAll()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun updateOptionsMenu(conversations: ArrayList<Conversation>) {
        recycle_bin_toolbar.menu.apply {
            findItem(R.id.empty_recycle_bin).isVisible = conversations.isNotEmpty()
        }
    }

    private fun updateMenuColors() {
        updateStatusbarColor(getProperBackgroundColor())
    }

    private fun loadRecycleBinConversations() {
        ensureBackgroundThread {
            val conversations = try {
                conversationsDB.getAllWithMessagesInRecycleBin().toMutableList() as ArrayList<Conversation>
            } catch (e: Exception) {
                ArrayList()
            }

            runOnUiThread {
                setupConversations(conversations)
            }
        }

        bus = EventBus.getDefault()
        try {
            bus!!.register(this)
        } catch (e: Exception) {
        }
    }

    private fun removeAll() {
        ConfirmationDialog(this, "", R.string.empty_recycle_bin_messages_confirmation, R.string.yes, R.string.no) {
            ensureBackgroundThread {
                emptyMessagesRecycleBin()
                loadRecycleBinConversations()
            }
        }
    }

    private fun getOrCreateConversationsAdapter(): RecycleBinConversationsAdapter {
        var currAdapter = conversations_list.adapter
        if (currAdapter == null) {
            hideKeyboard()
            currAdapter = RecycleBinConversationsAdapter(
                activity = this,
                recyclerView = conversations_list,
                onRefresh = { notifyDatasetChanged() },
                itemClick = { handleConversationClick(it) }
            )

            conversations_list.adapter = currAdapter
            if (areSystemAnimationsEnabled) {
                conversations_list.scheduleLayoutAnimation()
            }
        }
        return currAdapter as RecycleBinConversationsAdapter
    }

    private fun setupConversations(conversations: ArrayList<Conversation>) {
        val sortedConversations = conversations.sortedWith(
            compareByDescending<Conversation> { config.pinnedConversations.contains(it.threadId.toString()) }
                .thenByDescending { it.date }
        ).toMutableList() as ArrayList<Conversation>

        showOrHidePlaceholder(conversations.isEmpty())
        updateOptionsMenu(conversations)

        try {
            getOrCreateConversationsAdapter().apply {
                updateConversations(sortedConversations)
            }
        } catch (ignored: Exception) {
        }
    }

    private fun showOrHidePlaceholder(show: Boolean) {
        conversations_fastscroller.beGoneIf(show)
        no_conversations_placeholder.beVisibleIf(show)
        no_conversations_placeholder.text = getString(R.string.no_conversations_found)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyDatasetChanged() {
        getOrCreateConversationsAdapter().notifyDataSetChanged()
    }

    private fun handleConversationClick(any: Any) {
        Intent(this, ThreadActivity::class.java).apply {
            val conversation = any as Conversation
            putExtra(THREAD_ID, conversation.threadId)
            putExtra(THREAD_TITLE, conversation.title)
            putExtra(WAS_PROTECTION_HANDLED, true)
            putExtra(IS_RECYCLE_BIN, true)
            startActivity(this)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshMessages(event: Events.RefreshMessages) {
        loadRecycleBinConversations()
    }
}
