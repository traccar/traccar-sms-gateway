package com.simplemobiletools.smsmessenger.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.ArchivedConversationsAdapter
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.*
import com.simplemobiletools.smsmessenger.models.Conversation
import com.simplemobiletools.smsmessenger.models.Events
import kotlinx.android.synthetic.main.activity_archived_conversations.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class ArchivedConversationsActivity : SimpleActivity() {
    private var bus: EventBus? = null

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_archived_conversations)
        setupOptionsMenu()

        updateMaterialActivityViews(archive_coordinator, conversations_list, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(conversations_list, archive_toolbar)

        loadArchivedConversations()
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(archive_toolbar, NavigationIcon.Arrow)
        updateMenuColors()

        loadArchivedConversations()
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    private fun setupOptionsMenu() {
        archive_toolbar.inflateMenu(R.menu.archive_menu)

        archive_toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.empty_archive -> removeAll()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun updateOptionsMenu(conversations: ArrayList<Conversation>) {
        archive_toolbar.menu.apply {
            findItem(R.id.empty_archive).isVisible = conversations.isNotEmpty()
        }
    }

    private fun updateMenuColors() {
        updateStatusbarColor(getProperBackgroundColor())
    }

    private fun loadArchivedConversations() {
        ensureBackgroundThread {
            val conversations = try {
                conversationsDB.getAllArchived().toMutableList() as ArrayList<Conversation>
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
        ConfirmationDialog(this, "", R.string.empty_archive_confirmation, R.string.yes, R.string.no) {
            removeAllArchivedConversations {
                loadArchivedConversations()
            }
        }
    }

    private fun getOrCreateConversationsAdapter(): ArchivedConversationsAdapter {
        var currAdapter = conversations_list.adapter
        if (currAdapter == null) {
            hideKeyboard()
            currAdapter = ArchivedConversationsAdapter(
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
        return currAdapter as ArchivedConversationsAdapter
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
        no_conversations_placeholder.text = getString(R.string.no_archived_conversations)
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
            startActivity(this)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshMessages(event: Events.RefreshMessages) {
        loadArchivedConversations()
    }
}
