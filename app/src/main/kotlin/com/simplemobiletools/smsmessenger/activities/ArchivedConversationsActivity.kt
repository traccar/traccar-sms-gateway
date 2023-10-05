package com.simplemobiletools.smsmessenger.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.ArchivedConversationsAdapter
import com.simplemobiletools.smsmessenger.databinding.ActivityArchivedConversationsBinding
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.*
import com.simplemobiletools.smsmessenger.models.Conversation
import com.simplemobiletools.smsmessenger.models.Events
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class ArchivedConversationsActivity : SimpleActivity() {
    private var bus: EventBus? = null
    private val binding by viewBinding(ActivityArchivedConversationsBinding::inflate)

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()

        updateMaterialActivityViews(
            mainCoordinatorLayout = binding.archiveCoordinator,
            nestedView = binding.conversationsList,
            useTransparentNavigation = true,
            useTopSearchMenu = false
        )
        setupMaterialScrollListener(scrollingView = binding.conversationsList, toolbar = binding.archiveToolbar)

        loadArchivedConversations()
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.archiveToolbar, NavigationIcon.Arrow)
        updateMenuColors()

        loadArchivedConversations()
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    private fun setupOptionsMenu() {
        binding.archiveToolbar.inflateMenu(R.menu.archive_menu)
        binding.archiveToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.empty_archive -> removeAll()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun updateOptionsMenu(conversations: ArrayList<Conversation>) {
        binding.archiveToolbar.menu.apply {
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
        } catch (ignored: Exception) {
        }
    }

    private fun removeAll() {
        ConfirmationDialog(
            activity = this,
            message = "",
            messageId = R.string.empty_archive_confirmation,
            positive = com.simplemobiletools.commons.R.string.yes,
            negative = com.simplemobiletools.commons.R.string.no
        ) {
            removeAllArchivedConversations {
                loadArchivedConversations()
            }
        }
    }

    private fun getOrCreateConversationsAdapter(): ArchivedConversationsAdapter {
        var currAdapter = binding.conversationsList.adapter
        if (currAdapter == null) {
            hideKeyboard()
            currAdapter = ArchivedConversationsAdapter(
                activity = this,
                recyclerView = binding.conversationsList,
                onRefresh = { notifyDatasetChanged() },
                itemClick = { handleConversationClick(it) }
            )

            binding.conversationsList.adapter = currAdapter
            if (areSystemAnimationsEnabled) {
                binding.conversationsList.scheduleLayoutAnimation()
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
        binding.conversationsFastscroller.beGoneIf(show)
        binding.noConversationsPlaceholder.beVisibleIf(show)
        binding.noConversationsPlaceholder.setTextColor(getProperTextColor())
        binding.noConversationsPlaceholder.text = getString(R.string.no_archived_conversations)
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
