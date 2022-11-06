package com.simplemobiletools.smsmessenger.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import android.widget.Toast
import com.simplemobiletools.commons.dialogs.FilePickerDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.FAQItem
import com.simplemobiletools.commons.models.Release
import com.simplemobiletools.smsmessenger.BuildConfig
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.ConversationsAdapter
import com.simplemobiletools.smsmessenger.dialogs.ExportMessagesDialog
import com.simplemobiletools.smsmessenger.dialogs.ImportMessagesDialog
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.EXPORT_MIME_TYPE
import com.simplemobiletools.smsmessenger.helpers.MessagesExporter
import com.simplemobiletools.smsmessenger.helpers.THREAD_ID
import com.simplemobiletools.smsmessenger.helpers.THREAD_TITLE
import com.simplemobiletools.smsmessenger.models.Conversation
import com.simplemobiletools.smsmessenger.models.Events
import kotlinx.android.synthetic.main.activity_main.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.FileOutputStream
import java.io.OutputStream

class MainActivity : SimpleActivity() {
    private val MAKE_DEFAULT_APP_REQUEST = 1
    private val PICK_IMPORT_SOURCE_INTENT = 11
    private val PICK_EXPORT_FILE_INTENT = 21

    private var storedTextColor = 0
    private var storedFontSize = 0
    private var bus: EventBus? = null
    private val smsExporter by lazy { MessagesExporter(this) }

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()

        if (checkAppSideloading()) {
            return
        }

        if (isQPlus()) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager!!.isRoleAvailable(RoleManager.ROLE_SMS)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    askPermissions()
                } else {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
                }
            } else {
                toast(R.string.unknown_error_occurred)
                finish()
            }
        } else {
            if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
                askPermissions()
            } else {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
            }
        }

        clearAllMessagesIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(main_toolbar)

        if (storedTextColor != getProperTextColor()) {
            (conversations_list.adapter as? ConversationsAdapter)?.updateTextColor(getProperTextColor())
        }

        if (storedFontSize != config.fontSize) {
            (conversations_list.adapter as? ConversationsAdapter)?.updateFontSize()
        }

        (conversations_list.adapter as? ConversationsAdapter)?.updateDrafts()
        updateTextColors(main_coordinator)

        val properPrimaryColor = getProperPrimaryColor()
        no_conversations_placeholder_2.setTextColor(properPrimaryColor)
        no_conversations_placeholder_2.underlineText()
        conversations_fastscroller.updateColors(properPrimaryColor)
        checkShortcut()
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    private fun setupOptionsMenu() {
        main_toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.search -> launchSearch()
                R.id.settings -> launchSettings()
                R.id.export_messages -> tryToExportMessages()
                R.id.import_messages -> tryImportMessages()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == MAKE_DEFAULT_APP_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                askPermissions()
            } else {
                finish()
            }
        } else if (requestCode == PICK_IMPORT_SOURCE_INTENT && resultCode == Activity.RESULT_OK && resultData != null && resultData.data != null) {
            tryImportMessagesFromFile(resultData.data!!)
        } else if (requestCode == PICK_EXPORT_FILE_INTENT && resultCode == Activity.RESULT_OK && resultData != null && resultData.data != null) {
            val outputStream = contentResolver.openOutputStream(resultData.data!!)
            exportMessagesTo(outputStream)
        }
    }

    private fun storeStateVariables() {
        storedTextColor = getProperTextColor()
        storedFontSize = config.fontSize
    }

    // while SEND_SMS and READ_SMS permissions are mandatory, READ_CONTACTS is optional. If we don't have it, we just won't be able to show the contact name in some cases
    private fun askPermissions() {
        handlePermission(PERMISSION_READ_SMS) {
            if (it) {
                handlePermission(PERMISSION_SEND_SMS) {
                    if (it) {
                        handlePermission(PERMISSION_READ_CONTACTS) {
                            handleNotificationPermission { granted ->
                                if (!granted) {
                                    toast(R.string.no_post_notifications_permissions)
                                }
                            }

                            initMessenger()
                            bus = EventBus.getDefault()
                            try {
                                bus!!.register(this)
                            } catch (e: Exception) {
                            }
                        }
                    } else {
                        finish()
                    }
                }
            } else {
                finish()
            }
        }
    }

    private fun initMessenger() {
        checkWhatsNewDialog()
        storeStateVariables()
        getCachedConversations()

        no_conversations_placeholder_2.setOnClickListener {
            launchNewConversation()
        }

        conversations_fab.setOnClickListener {
            launchNewConversation()
        }
    }

    private fun getCachedConversations() {
        ensureBackgroundThread {
            val conversations = try {
                conversationsDB.getAll().toMutableList() as ArrayList<Conversation>
            } catch (e: Exception) {
                ArrayList()
            }

            updateUnreadCountBadge(conversations)
            runOnUiThread {
                setupConversations(conversations)
                getNewConversations(conversations)
            }
            conversations.forEach {
                clearExpiredScheduledMessages(it.threadId)
            }
        }
    }

    private fun getNewConversations(cachedConversations: ArrayList<Conversation>) {
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ensureBackgroundThread {
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            val conversations = getConversations(privateContacts = privateContacts)

            conversations.forEach { clonedConversation ->
                val threadIds = cachedConversations.map { it.threadId }
                if (!threadIds.contains(clonedConversation.threadId)) {
                    conversationsDB.insertOrUpdate(clonedConversation)
                    cachedConversations.add(clonedConversation)
                }
            }

            cachedConversations.forEach { cachedConversation ->
                val threadId = cachedConversation.threadId

                val isTemporaryThread = cachedConversation.isScheduled
                val isConversationDeleted = !conversations.map { it.threadId }.contains(threadId)
                if (isConversationDeleted && !isTemporaryThread) {
                    conversationsDB.deleteThreadId(threadId)
                }

                val newConversation = conversations.find { it.phoneNumber == cachedConversation.phoneNumber }
                if (isTemporaryThread && newConversation != null) {
                    // delete the original temporary thread and move any scheduled messages to the new thread
                    conversationsDB.deleteThreadId(threadId)
                    messagesDB.getScheduledThreadMessages(threadId)
                        .forEach { message ->
                            messagesDB.insertOrUpdate(message.copy(threadId = newConversation.threadId))
                        }
                }
            }

            cachedConversations.forEach { cachedConv ->
                val conv = conversations.find { it.threadId == cachedConv.threadId && !cachedConv.areContentsTheSame(it) }
                if (conv != null) {
                    val conversation = conv.copy(date = maxOf(cachedConv.date, conv.date))
                    conversationsDB.insertOrUpdate(conversation)
                }
            }

            val allConversations = conversationsDB.getAll() as ArrayList<Conversation>
            runOnUiThread {
                setupConversations(allConversations)
            }

            if (config.appRunCount == 1) {
                conversations.map { it.threadId }.forEach { threadId ->
                    val messages = getMessages(threadId, getImageResolutions = false, includeScheduledMessages = false)
                    messages.chunked(30).forEach { currentMessages ->
                        messagesDB.insertMessages(*currentMessages.toTypedArray())
                    }
                }
            }
        }
    }

    private fun setupConversations(conversations: ArrayList<Conversation>) {
        val hasConversations = conversations.isNotEmpty()
        val sortedConversations = conversations.sortedWith(
            compareByDescending<Conversation> { config.pinnedConversations.contains(it.threadId.toString()) }
                .thenByDescending { it.date }
        ).toMutableList() as ArrayList<Conversation>

        conversations_fastscroller.beVisibleIf(hasConversations)
        no_conversations_placeholder.beGoneIf(hasConversations)
        no_conversations_placeholder_2.beGoneIf(hasConversations)

        if (!hasConversations && config.appRunCount == 1) {
            no_conversations_placeholder.text = getString(R.string.loading_messages)
            no_conversations_placeholder_2.beGone()
        }

        val currAdapter = conversations_list.adapter
        if (currAdapter == null) {
            hideKeyboard()
            ConversationsAdapter(this, sortedConversations, conversations_list) {
                Intent(this, ThreadActivity::class.java).apply {
                    val conversation = it as Conversation
                    putExtra(THREAD_ID, conversation.threadId)
                    putExtra(THREAD_TITLE, conversation.title)
                    startActivity(this)
                }
            }.apply {
                conversations_list.adapter = this
            }

            if (areSystemAnimationsEnabled) {
                conversations_list.scheduleLayoutAnimation()
            }
        } else {
            try {
                (currAdapter as ConversationsAdapter).updateConversations(sortedConversations)
                if (currAdapter.conversations.isEmpty()) {
                    conversations_fastscroller.beGone()
                    no_conversations_placeholder.text = getString(R.string.no_conversations_found)
                    no_conversations_placeholder.beVisible()
                    no_conversations_placeholder_2.beVisible()
                }
            } catch (ignored: Exception) {
            }
        }
    }

    private fun launchNewConversation() {
        hideKeyboard()
        Intent(this, NewConversationActivity::class.java).apply {
            startActivity(this)
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcut() {
        val appIconColor = config.appIconColor
        if (isNougatMR1Plus() && config.lastHandledShortcutColor != appIconColor) {
            val newConversation = getCreateNewContactShortcut(appIconColor)

            val manager = getSystemService(ShortcutManager::class.java)
            try {
                manager.dynamicShortcuts = listOf(newConversation)
                config.lastHandledShortcutColor = appIconColor
            } catch (ignored: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getCreateNewContactShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.new_conversation)
        val drawable = resources.getDrawable(R.drawable.shortcut_plus)
        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_plus_background).applyColorFilter(appIconColor)
        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, NewConversationActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "new_conversation")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .build()
    }

    private fun launchSearch() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SearchActivity::class.java))
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = LICENSE_EVENT_BUS or LICENSE_SMS_MMS or LICENSE_INDICATOR_FAST_SCROLL

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_2_title, R.string.faq_2_text),
            FAQItem(R.string.faq_3_title, R.string.faq_3_text),
            FAQItem(R.string.faq_9_title_commons, R.string.faq_9_text_commons)
        )

        if (!resources.getBoolean(R.bool.hide_google_relations)) {
            faqItems.add(FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons))
            faqItems.add(FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons))
        }

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }

    private fun tryToExportMessages() {
        if (isQPlus()) {
            ExportMessagesDialog(this, config.lastExportPath, true) { file ->
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = EXPORT_MIME_TYPE
                    putExtra(Intent.EXTRA_TITLE, file.name)
                    addCategory(Intent.CATEGORY_OPENABLE)

                    try {
                        startActivityForResult(this, PICK_EXPORT_FILE_INTENT)
                    } catch (e: ActivityNotFoundException) {
                        toast(R.string.system_service_disabled, Toast.LENGTH_LONG)
                    } catch (e: Exception) {
                        showErrorToast(e)
                    }
                }
            }
        } else {
            handlePermission(PERMISSION_WRITE_STORAGE) {
                if (it) {
                    ExportMessagesDialog(this, config.lastExportPath, false) { file ->
                        getFileOutputStream(file.toFileDirItem(this), true) { outStream ->
                            exportMessagesTo(outStream)
                        }
                    }
                }
            }
        }
    }

    private fun exportMessagesTo(outputStream: OutputStream?) {
        toast(R.string.exporting)
        ensureBackgroundThread {
            smsExporter.exportMessages(outputStream) {
                val toastId = when (it) {
                    MessagesExporter.ExportResult.EXPORT_OK -> R.string.exporting_successful
                    else -> R.string.exporting_failed
                }

                toast(toastId)
            }
        }
    }

    private fun tryImportMessages() {
        if (isQPlus()) {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = EXPORT_MIME_TYPE

                try {
                    startActivityForResult(this, PICK_IMPORT_SOURCE_INTENT)
                } catch (e: ActivityNotFoundException) {
                    toast(R.string.system_service_disabled, Toast.LENGTH_LONG)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        } else {
            handlePermission(PERMISSION_READ_STORAGE) {
                if (it) {
                    importEvents()
                }
            }
        }
    }

    private fun importEvents() {
        FilePickerDialog(this) {
            showImportEventsDialog(it)
        }
    }

    private fun showImportEventsDialog(path: String) {
        ImportMessagesDialog(this, path)
    }

    private fun tryImportMessagesFromFile(uri: Uri) {
        when (uri.scheme) {
            "file" -> showImportEventsDialog(uri.path!!)
            "content" -> {
                val tempFile = getTempFile("messages", "backup.json")
                if (tempFile == null) {
                    toast(R.string.unknown_error_occurred)
                    return
                }

                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val out = FileOutputStream(tempFile)
                    inputStream!!.copyTo(out)
                    showImportEventsDialog(tempFile.absolutePath)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
            else -> toast(R.string.invalid_file_format)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshMessages(event: Events.RefreshMessages) {
        initMessenger()
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            add(Release(48, R.string.release_48))
            add(Release(62, R.string.release_62))
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}
