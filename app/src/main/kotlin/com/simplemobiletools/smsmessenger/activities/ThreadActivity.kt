package com.simplemobiletools.smsmessenger.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.drawable.LayerDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Telephony
import android.provider.Telephony.Sms.MESSAGE_TYPE_QUEUED
import android.provider.Telephony.Sms.STATUS_NONE
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.telephony.SubscriptionInfo
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.format.DateUtils.FORMAT_NO_YEAR
import android.text.format.DateUtils.FORMAT_SHOW_DATE
import android.text.format.DateUtils.FORMAT_SHOW_TIME
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.dialogs.FeatureLockedDialog
import com.simplemobiletools.commons.dialogs.PermissionRequiredDialog
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.PhoneNumber
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.smsmessenger.BuildConfig
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.AttachmentsAdapter
import com.simplemobiletools.smsmessenger.adapters.AutoCompleteTextViewAdapter
import com.simplemobiletools.smsmessenger.adapters.ThreadAdapter
import com.simplemobiletools.smsmessenger.dialogs.InvalidNumberDialog
import com.simplemobiletools.smsmessenger.dialogs.RenameConversationDialog
import com.simplemobiletools.smsmessenger.dialogs.ScheduleMessageDialog
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.*
import com.simplemobiletools.smsmessenger.messaging.*
import com.simplemobiletools.smsmessenger.models.*
import com.simplemobiletools.smsmessenger.models.ThreadItem.*
import kotlinx.android.synthetic.main.activity_thread.*
import kotlinx.android.synthetic.main.item_selected_contact.view.*
import kotlinx.android.synthetic.main.layout_attachment_picker.*
import kotlinx.android.synthetic.main.layout_invalid_short_code_info.*
import kotlinx.android.synthetic.main.layout_thread_send_message_holder.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.joda.time.DateTime
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class ThreadActivity : SimpleActivity() {
    private val MIN_DATE_TIME_DIFF_SECS = 300

    private val TYPE_EDIT = 14
    private val TYPE_SEND = 15
    private val TYPE_DELETE = 16

    private val SCROLL_TO_BOTTOM_FAB_LIMIT = 20

    private var threadId = 0L
    private var currentSIMCardIndex = 0
    private var isActivityVisible = false
    private var refreshedSinceSent = false
    private var threadItems = ArrayList<ThreadItem>()
    private var bus: EventBus? = null
    private var conversation: Conversation? = null
    private var participants = ArrayList<SimpleContact>()
    private var privateContacts = ArrayList<SimpleContact>()
    private var messages = ArrayList<Message>()
    private val availableSIMCards = ArrayList<SIMCard>()
    private var lastAttachmentUri: String? = null
    private var capturedImageUri: Uri? = null
    private var loadingOlderMessages = false
    private var allMessagesFetched = false
    private var oldestMessageDate = -1
    private var wasProtectionHandled = false
    private var isRecycleBin = false

    private var isScheduledMessage: Boolean = false
    private var messageToResend: Long? = null
    private var scheduledMessage: Message? = null
    private lateinit var scheduledDateTime: DateTime

    private var isAttachmentPickerVisible = false

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        finish()
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thread)
        setupOptionsMenu()
        refreshMenuItems()

        updateMaterialActivityViews(thread_coordinator, null, useTransparentNavigation = false, useTopSearchMenu = false)
        setupMaterialScrollListener(null, thread_toolbar)

        val extras = intent.extras
        if (extras == null) {
            toast(R.string.unknown_error_occurred)
            finish()
            return
        }

        clearAllMessagesIfNeeded()
        threadId = intent.getLongExtra(THREAD_ID, 0L)
        intent.getStringExtra(THREAD_TITLE)?.let {
            thread_toolbar.title = it
        }
        isRecycleBin = intent.getBooleanExtra(IS_RECYCLE_BIN, false)
        wasProtectionHandled = intent.getBooleanExtra(WAS_PROTECTION_HANDLED, false)

        bus = EventBus.getDefault()
        bus!!.register(this)

        if (savedInstanceState == null) {
            if (!wasProtectionHandled) {
                handleAppPasswordProtection {
                    wasProtectionHandled = it
                    if (it) {
                        loadConversation()
                    } else {
                        finish()
                    }
                }
            } else {
                loadConversation()
            }
        }

        setupAttachmentPickerView()
        setupKeyboardListener()
        hideAttachmentPicker()
        maybeSetupRecycleBinView()
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(thread_toolbar, NavigationIcon.Arrow, statusBarColor = getProperBackgroundColor())

        val smsDraft = getSmsDraft(threadId)
        if (smsDraft != null) {
            thread_type_message.setText(smsDraft)
        }
        isActivityVisible = true

        notificationManager.cancel(threadId.hashCode())

        ensureBackgroundThread {
            val newConv = conversationsDB.getConversationWithThreadId(threadId)
            if (newConv != null) {
                conversation = newConv
                runOnUiThread {
                    setupThreadTitle()
                }
            }
        }

        val bottomBarColor = getBottomBarColor()
        thread_send_message_holder.setBackgroundColor(bottomBarColor)
        reply_disabled_info_holder.setBackgroundColor(bottomBarColor)
        updateNavigationBarColor(bottomBarColor)
    }

    override fun onPause() {
        super.onPause()

        if (thread_type_message.value != "" && getAttachmentSelections().isEmpty()) {
            saveSmsDraft(thread_type_message.value, threadId)
        } else {
            deleteSmsDraft(threadId)
        }

        bus?.post(Events.RefreshMessages())
        isActivityVisible = false
    }

    override fun onBackPressed() {
        isAttachmentPickerVisible = false
        if (attachment_picker_holder.isVisible()) {
            hideAttachmentPicker()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(WAS_PROTECTION_HANDLED, wasProtectionHandled)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        wasProtectionHandled = savedInstanceState.getBoolean(WAS_PROTECTION_HANDLED, false)

        if (!wasProtectionHandled) {
            handleAppPasswordProtection {
                wasProtectionHandled = it
                if (it) {
                    loadConversation()
                } else {
                    finish()
                }
            }
        } else {
            loadConversation()
        }
    }

    private fun refreshMenuItems() {
        val firstPhoneNumber = participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.value
        thread_toolbar.menu.apply {
            findItem(R.id.delete).isVisible = threadItems.isNotEmpty()
            findItem(R.id.restore).isVisible = threadItems.isNotEmpty() && isRecycleBin
            findItem(R.id.archive).isVisible = threadItems.isNotEmpty() && conversation?.isArchived == false && !isRecycleBin
            findItem(R.id.unarchive).isVisible = threadItems.isNotEmpty() && conversation?.isArchived == true && !isRecycleBin
            findItem(R.id.rename_conversation).isVisible = participants.size > 1 && conversation != null && !isRecycleBin
            findItem(R.id.conversation_details).isVisible = conversation != null && !isRecycleBin
            findItem(R.id.block_number).title = addLockedLabelIfNeeded(R.string.block_number)
            findItem(R.id.block_number).isVisible = isNougatPlus() && !isRecycleBin
            findItem(R.id.dial_number).isVisible = participants.size == 1 && !isSpecialNumber() && !isRecycleBin
            findItem(R.id.manage_people).isVisible = !isSpecialNumber() && !isRecycleBin
            findItem(R.id.mark_as_unread).isVisible = threadItems.isNotEmpty() && !isRecycleBin

            // allow saving number in cases when we dont have it stored yet and it is a casual readable number
            findItem(R.id.add_number_to_contact).isVisible = participants.size == 1 && participants.first().name == firstPhoneNumber && firstPhoneNumber.any {
                it.isDigit()
            } && !isRecycleBin
        }
    }

    private fun setupOptionsMenu() {
        thread_toolbar.setOnMenuItemClickListener { menuItem ->
            if (participants.isEmpty()) {
                return@setOnMenuItemClickListener true
            }

            when (menuItem.itemId) {
                R.id.block_number -> tryBlocking()
                R.id.delete -> askConfirmDelete()
                R.id.restore -> askConfirmRestoreAll()
                R.id.archive -> archiveConversation()
                R.id.unarchive -> unarchiveConversation()
                R.id.rename_conversation -> renameConversation()
                R.id.conversation_details -> showConversationDetails()
                R.id.add_number_to_contact -> addNumberToContact()
                R.id.dial_number -> dialNumber()
                R.id.manage_people -> managePeople()
                R.id.mark_as_unread -> markAsUnread()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (resultCode != Activity.RESULT_OK) return
        val data = resultData?.data
        messageToResend = null

        if (requestCode == CAPTURE_PHOTO_INTENT && capturedImageUri != null) {
            addAttachment(capturedImageUri!!)
        } else if (data != null) {
            when (requestCode) {
                CAPTURE_VIDEO_INTENT, PICK_DOCUMENT_INTENT, CAPTURE_AUDIO_INTENT, PICK_PHOTO_INTENT, PICK_VIDEO_INTENT -> addAttachment(data)
                PICK_CONTACT_INTENT -> addContactAttachment(data)
                PICK_SAVE_FILE_INTENT -> saveAttachment(resultData)
            }
        }
    }

    private fun setupCachedMessages(callback: () -> Unit) {
        ensureBackgroundThread {
            messages = try {
                if (isRecycleBin) {
                    messagesDB.getThreadMessagesFromRecycleBin(threadId)
                } else {
                    if (config.useRecycleBin) {
                        messagesDB.getNonRecycledThreadMessages(threadId)
                    } else {
                        messagesDB.getThreadMessages(threadId)
                    }
                }.toMutableList() as ArrayList<Message>
            } catch (e: Exception) {
                ArrayList()
            }
            clearExpiredScheduledMessages(threadId, messages)
            messages.removeAll { it.isScheduled && it.millis() < System.currentTimeMillis() }

            messages.sortBy { it.date }
            if (messages.size > MESSAGES_LIMIT) {
                messages = ArrayList(messages.takeLast(MESSAGES_LIMIT))
            }

            setupParticipants()
            setupAdapter()

            runOnUiThread {
                if (messages.isEmpty()) {
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                    thread_type_message.requestFocus()
                }

                setupThreadTitle()
                setupSIMSelector()
                updateMessageType()
                callback()
            }
        }
    }

    private fun setupThread() {
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ensureBackgroundThread {
            privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)

            val cachedMessagesCode = messages.clone().hashCode()
            if (!isRecycleBin) {
                messages = getMessages(threadId, true)
                if (config.useRecycleBin) {
                    val recycledMessages = messagesDB.getThreadMessagesFromRecycleBin(threadId).map { it.id }
                    messages = messages.filter {  !recycledMessages.contains(it.id) }.toMutableList() as ArrayList<Message>
                }
            }

            val hasParticipantWithoutName = participants.any { contact ->
                contact.phoneNumbers.map { it.normalizedNumber }.contains(contact.name)
            }

            try {
                if (participants.isNotEmpty() && messages.hashCode() == cachedMessagesCode && !hasParticipantWithoutName) {
                    setupAdapter()
                    return@ensureBackgroundThread
                }
            } catch (ignored: Exception) {
            }

            setupParticipants()

            // check if no participant came from a privately stored contact in Simple Contacts
            if (privateContacts.isNotEmpty()) {
                val senderNumbersToReplace = HashMap<String, String>()
                participants.filter { it.doesHavePhoneNumber(it.name) }.forEach { participant ->
                    privateContacts.firstOrNull { it.doesHavePhoneNumber(participant.phoneNumbers.first().normalizedNumber) }?.apply {
                        senderNumbersToReplace[participant.phoneNumbers.first().normalizedNumber] = name
                        participant.name = name
                        participant.photoUri = photoUri
                    }
                }

                messages.forEach { message ->
                    if (senderNumbersToReplace.keys.contains(message.senderName)) {
                        message.senderName = senderNumbersToReplace[message.senderName]!!
                    }
                }
            }

            if (participants.isEmpty()) {
                val name = intent.getStringExtra(THREAD_TITLE) ?: ""
                val number = intent.getStringExtra(THREAD_NUMBER)
                if (number == null) {
                    toast(R.string.unknown_error_occurred)
                    finish()
                    return@ensureBackgroundThread
                }

                val phoneNumber = PhoneNumber(number, 0, "", number)
                val contact = SimpleContact(0, 0, name, "", arrayListOf(phoneNumber), ArrayList(), ArrayList())
                participants.add(contact)
            }

            if (!isRecycleBin) {
                messages.chunked(30).forEach { currentMessages ->
                    messagesDB.insertMessages(*currentMessages.toTypedArray())
                }
            }

            setupAttachmentSizes()
            setupAdapter()
            runOnUiThread {
                setupThreadTitle()
                setupSIMSelector()
            }
        }
    }

    private fun getOrCreateThreadAdapter(): ThreadAdapter {
        var currAdapter = thread_messages_list.adapter
        if (currAdapter == null) {
            currAdapter = ThreadAdapter(
                activity = this,
                recyclerView = thread_messages_list,
                itemClick = { handleItemClick(it) },
                isRecycleBin = isRecycleBin,
                deleteMessages = { messages, toRecycleBin, fromRecycleBin ->  deleteMessages(messages, toRecycleBin, fromRecycleBin) }
            )

            thread_messages_list.adapter = currAdapter
            thread_messages_list.endlessScrollListener = object : MyRecyclerView.EndlessScrollListener {
                override fun updateBottom() {}

                override fun updateTop() {
                    fetchNextMessages()
                }
            }
        }
        return currAdapter as ThreadAdapter
    }

    private fun setupAdapter() {
        threadItems = getThreadItems()

        runOnUiThread {
            refreshMenuItems()
            getOrCreateThreadAdapter().apply {
                val layoutManager = thread_messages_list.layoutManager as LinearLayoutManager
                val lastPosition = itemCount - 1
                val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
                val shouldScrollToBottom = currentList.lastOrNull() != threadItems.lastOrNull() && lastPosition - lastVisiblePosition == 1
                updateMessages(threadItems, if (shouldScrollToBottom) lastPosition else -1)
            }
        }

        SimpleContactsHelper(this).getAvailableContacts(false) { contacts ->
            contacts.addAll(privateContacts)
            runOnUiThread {
                val adapter = AutoCompleteTextViewAdapter(this, contacts)
                add_contact_or_number.setAdapter(adapter)
                add_contact_or_number.imeOptions = EditorInfo.IME_ACTION_NEXT
                add_contact_or_number.setOnItemClickListener { _, _, position, _ ->
                    val currContacts = (add_contact_or_number.adapter as AutoCompleteTextViewAdapter).resultList
                    val selectedContact = currContacts[position]
                    addSelectedContact(selectedContact)
                }

                add_contact_or_number.onTextChangeListener {
                    confirm_inserted_number.beVisibleIf(it.length > 2)
                }
            }
        }

        runOnUiThread {
            confirm_inserted_number?.setOnClickListener {
                val number = add_contact_or_number.value
                val phoneNumber = PhoneNumber(number, 0, "", number)
                val contact = SimpleContact(number.hashCode(), number.hashCode(), number, "", arrayListOf(phoneNumber), ArrayList(), ArrayList())
                addSelectedContact(contact)
            }
        }
    }

    private fun scrollToBottom() {
        val position = getOrCreateThreadAdapter().currentList.lastIndex
        if (position >= 0) {
            thread_messages_list.smoothScrollToPosition(position)
        }
    }

    private fun setupScrollFab() {
        thread_messages_list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = thread_messages_list.layoutManager as LinearLayoutManager
                val lastVisibleItemPosition = layoutManager.findLastCompletelyVisibleItemPosition()
                val isCloseToBottom = lastVisibleItemPosition >= getOrCreateThreadAdapter().itemCount - SCROLL_TO_BOTTOM_FAB_LIMIT
                if (isCloseToBottom) {
                    scroll_to_bottom_fab.hide()
                } else {
                    scroll_to_bottom_fab.show()
                }
            }
        })
    }

    private fun handleItemClick(any: Any) {
        when {
            any is Message && any.isScheduled -> showScheduledMessageInfo(any)
            any is ThreadError -> {
                thread_type_message.setText(any.messageText)
                messageToResend = any.messageId
            }
        }
    }

    private fun deleteMessages(messagesToRemove: List<Message>, toRecycleBin: Boolean, fromRecycleBin: Boolean) {
        val deletePosition = threadItems.indexOf(messagesToRemove.first())
        messages.removeAll(messagesToRemove.toSet())
        threadItems = getThreadItems()

        runOnUiThread {
            if (messages.isEmpty()) {
                finish()
            } else {
                getOrCreateThreadAdapter().apply {
                    updateMessages(threadItems, scrollPosition = deletePosition)
                    finishActMode()
                }
            }
        }

        messagesToRemove.forEach { message ->
            val messageId = message.id
            if (message.isScheduled) {
                deleteScheduledMessage(messageId)
                cancelScheduleSendPendingIntent(messageId)
            } else {
                if (toRecycleBin) {
                    moveMessageToRecycleBin(messageId)
                } else if (fromRecycleBin) {
                    restoreMessageFromRecycleBin(messageId)
                } else {
                    deleteMessage(messageId, message.isMMS)
                }
            }
        }
        updateLastConversationMessage(threadId)

        // move all scheduled messages to a temporary thread when there are no real messages left
        if (messages.isNotEmpty() && messages.all { it.isScheduled }) {
            val scheduledMessage = messages.last()
            val fakeThreadId = generateRandomId()
            createTemporaryThread(scheduledMessage, fakeThreadId, conversation)
            updateScheduledMessagesThreadId(messages, fakeThreadId)
            threadId = fakeThreadId
        }
    }

    private fun fetchNextMessages() {
        if (messages.isEmpty() || allMessagesFetched || loadingOlderMessages) {
            if (allMessagesFetched) {
                getOrCreateThreadAdapter().apply {
                    val newList = currentList.toMutableList().apply {
                        removeAll { it is ThreadLoading }
                    }
                    updateMessages(newMessages = newList as ArrayList<ThreadItem>, scrollPosition = 0)
                }
            }
            return
        }

        val firstItem = messages.first()
        val dateOfFirstItem = firstItem.date
        if (oldestMessageDate == dateOfFirstItem) {
            allMessagesFetched = true
            return
        }

        oldestMessageDate = dateOfFirstItem
        loadingOlderMessages = true

        ensureBackgroundThread {
            val olderMessages = getMessages(threadId, true, oldestMessageDate)
                .filter { message -> !messages.contains(message) }

            messages.addAll(0, olderMessages)
            allMessagesFetched = olderMessages.isEmpty()
            threadItems = getThreadItems()

            runOnUiThread {
                loadingOlderMessages = false
                val itemAtRefreshIndex = threadItems.indexOfFirst { it == firstItem }
                getOrCreateThreadAdapter().updateMessages(threadItems, itemAtRefreshIndex)
            }
        }
    }

    private fun loadConversation() {
        handlePermission(PERMISSION_READ_PHONE_STATE) { granted ->
            if (granted) {
                setupButtons()
                setupConversation()
                setupCachedMessages {
                    val searchedMessageId = intent.getLongExtra(SEARCHED_MESSAGE_ID, -1L)
                    intent.removeExtra(SEARCHED_MESSAGE_ID)
                    if (searchedMessageId != -1L) {
                        val index = threadItems.indexOfFirst { (it as? Message)?.id == searchedMessageId }
                        if (index != -1) {
                            thread_messages_list.smoothScrollToPosition(index)
                        }
                    }

                    setupThread()
                    setupScrollFab()
                }
            } else {
                finish()
            }
        }
    }

    private fun setupConversation() {
        ensureBackgroundThread {
            conversation = conversationsDB.getConversationWithThreadId(threadId)
        }
    }

    private fun setupButtons() {
        updateTextColors(thread_holder)
        val textColor = getProperTextColor()
        thread_send_message.apply {
            setTextColor(textColor)
            compoundDrawables.forEach {
                it?.applyColorFilter(textColor)
            }
        }

        confirm_manage_contacts.applyColorFilter(textColor)
        thread_add_attachment.applyColorFilter(textColor)

        val properPrimaryColor = getProperPrimaryColor()
        thread_messages_fastscroller.updateColors(properPrimaryColor)

        thread_character_counter.beVisibleIf(config.showCharacterCounter)
        thread_character_counter.setTextSize(TypedValue.COMPLEX_UNIT_PX, getTextSize())

        thread_type_message.setTextSize(TypedValue.COMPLEX_UNIT_PX, getTextSize())
        thread_send_message.setOnClickListener {
            sendMessage()
        }

        thread_send_message.setOnLongClickListener {
            if (!isScheduledMessage) {
                launchScheduleSendDialog()
            }
            true
        }

        thread_send_message.isClickable = false
        thread_type_message.onTextChangeListener {
            messageToResend = null
            checkSendMessageAvailability()
            val messageString = if (config.useSimpleCharacters) {
                it.normalizeString()
            } else {
                it
            }
            val messageLength = SmsMessage.calculateLength(messageString, false)
            thread_character_counter.text = "${messageLength[2]}/${messageLength[0]}"
        }

        if (config.sendOnEnter) {
            thread_type_message.inputType = EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
            thread_type_message.imeOptions = EditorInfo.IME_ACTION_SEND

            thread_type_message.setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEND) {
                    dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                    return@setOnEditorActionListener true
                }
                false
            }

            thread_type_message.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                    sendMessage()
                    return@setOnKeyListener true
                }
                false
            }
        }

        confirm_manage_contacts.setOnClickListener {
            hideKeyboard()
            thread_add_contacts.beGone()

            val numbers = HashSet<String>()
            participants.forEach { contact ->
                contact.phoneNumbers.forEach {
                    numbers.add(it.normalizedNumber)
                }
            }
            val newThreadId = getThreadId(numbers)
            if (threadId != newThreadId) {
                hideKeyboard()
                Intent(this, ThreadActivity::class.java).apply {
                    putExtra(THREAD_ID, newThreadId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(this)
                }
            }
        }

        thread_type_message.setText(intent.getStringExtra(THREAD_TEXT))
        thread_add_attachment.setOnClickListener {
            if (attachment_picker_holder.isVisible()) {
                isAttachmentPickerVisible = false
                WindowCompat.getInsetsController(window, thread_type_message).show(WindowInsetsCompat.Type.ime())
            } else {
                isAttachmentPickerVisible = true
                showOrHideAttachmentPicker()
                WindowCompat.getInsetsController(window, thread_type_message).hide(WindowInsetsCompat.Type.ime())
            }
            window.decorView.requestApplyInsets()
        }

        if (intent.extras?.containsKey(THREAD_ATTACHMENT_URI) == true) {
            val uri = Uri.parse(intent.getStringExtra(THREAD_ATTACHMENT_URI))
            addAttachment(uri)
        } else if (intent.extras?.containsKey(THREAD_ATTACHMENT_URIS) == true) {
            (intent.getSerializableExtra(THREAD_ATTACHMENT_URIS) as? ArrayList<Uri>)?.forEach {
                addAttachment(it)
            }
        }
        scroll_to_bottom_fab.setOnClickListener {
            scrollToBottom()
        }
        scroll_to_bottom_fab.backgroundTintList = ColorStateList.valueOf(getBottomBarColor())
        scroll_to_bottom_fab.applyColorFilter(textColor)

        setupScheduleSendUi()
    }

    private fun askForExactAlarmPermissionIfNeeded(callback: () -> Unit = {}) {
        if (isSPlus()) {
            val alarmManager: AlarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (alarmManager.canScheduleExactAlarms()) {
                callback()
            } else {
                PermissionRequiredDialog(
                    activity = this,
                    textId = R.string.allow_alarm_scheduled_messages,
                    positiveActionCallback = {
                        openRequestExactAlarmSettings(BuildConfig.APPLICATION_ID)
                    },
                )
            }
        } else {
            callback()
        }
    }

    private fun setupAttachmentSizes() {
        messages.filter { it.attachment != null }.forEach { message ->
            message.attachment!!.attachments.forEach {
                try {
                    if (it.mimetype.startsWith("image/")) {
                        val fileOptions = BitmapFactory.Options()
                        fileOptions.inJustDecodeBounds = true
                        BitmapFactory.decodeStream(contentResolver.openInputStream(it.getUri()), null, fileOptions)
                        it.width = fileOptions.outWidth
                        it.height = fileOptions.outHeight
                    } else if (it.mimetype.startsWith("video/")) {
                        val metaRetriever = MediaMetadataRetriever()
                        metaRetriever.setDataSource(this, it.getUri())
                        it.width = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
                        it.height = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
                    }

                    if (it.width < 0) {
                        it.width = 0
                    }

                    if (it.height < 0) {
                        it.height = 0
                    }
                } catch (ignored: Exception) {
                }
            }
        }
    }

    private fun setupParticipants() {
        if (participants.isEmpty()) {
            participants = if (messages.isEmpty()) {
                val intentNumbers = getPhoneNumbersFromIntent()
                val participants = getThreadParticipants(threadId, null)
                fixParticipantNumbers(participants, intentNumbers)
            } else {
                messages.first().participants
            }
            runOnUiThread {
                maybeDisableShortCodeReply()
            }
        }
    }

    private fun isSpecialNumber(): Boolean {
        val addresses = participants.getAddresses()
        return addresses.any { isShortCodeWithLetters(it) }
    }

    private fun maybeDisableShortCodeReply() {
        if (isSpecialNumber() && !isRecycleBin) {
            thread_send_message_holder.beGone()
            reply_disabled_info_holder.beVisible()
            val textColor = getProperTextColor()
            reply_disabled_text.setTextColor(textColor)
            reply_disabled_info.apply {
                applyColorFilter(textColor)
                setOnClickListener {
                    InvalidNumberDialog(
                        activity = this@ThreadActivity,
                        text = getString(R.string.invalid_short_code_desc)
                    )
                }
                if (isOreoPlus()) {
                    tooltipText = getString(R.string.more_info)
                }
            }
        }
    }

    private fun setupThreadTitle() {
        val title = conversation?.title
        thread_toolbar.title = if (!title.isNullOrEmpty()) {
            title
        } else {
            participants.getThreadTitle()
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupSIMSelector() {
        val availableSIMs = subscriptionManagerCompat().activeSubscriptionInfoList ?: return
        if (availableSIMs.size > 1) {
            availableSIMs.forEachIndexed { index, subscriptionInfo ->
                var label = subscriptionInfo.displayName?.toString() ?: ""
                if (subscriptionInfo.number?.isNotEmpty() == true) {
                    label += " (${subscriptionInfo.number})"
                }
                val simCard = SIMCard(index + 1, subscriptionInfo.subscriptionId, label)
                availableSIMCards.add(simCard)
            }

            val numbers = ArrayList<String>()
            participants.forEach { contact ->
                contact.phoneNumbers.forEach {
                    numbers.add(it.normalizedNumber)
                }
            }

            if (numbers.isEmpty()) {
                return
            }

            currentSIMCardIndex = getProperSimIndex(availableSIMs, numbers)
            thread_select_sim_icon.applyColorFilter(getProperTextColor())
            thread_select_sim_icon.beVisible()
            thread_select_sim_number.beVisible()

            if (availableSIMCards.isNotEmpty()) {
                thread_select_sim_icon.setOnClickListener {
                    currentSIMCardIndex = (currentSIMCardIndex + 1) % availableSIMCards.size
                    val currentSIMCard = availableSIMCards[currentSIMCardIndex]
                    thread_select_sim_number.text = currentSIMCard.id.toString()
                    val currentSubscriptionId = currentSIMCard.subscriptionId
                    numbers.forEach {
                        config.saveUseSIMIdAtNumber(it, currentSubscriptionId)
                    }
                    toast(currentSIMCard.label)
                }
            }

            thread_select_sim_number.setTextColor(getProperTextColor().getContrastColor())
            try {
                thread_select_sim_number.text = (availableSIMCards[currentSIMCardIndex].id).toString()
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getProperSimIndex(availableSIMs: MutableList<SubscriptionInfo>, numbers: List<String>): Int {
        val userPreferredSimId = config.getUseSIMIdAtNumber(numbers.first())
        val userPreferredSimIdx = availableSIMs.indexOfFirstOrNull { it.subscriptionId == userPreferredSimId }

        val lastMessage = messages.lastOrNull()
        val senderPreferredSimIdx = if (lastMessage?.isReceivedMessage() == true) {
            availableSIMs.indexOfFirstOrNull { it.subscriptionId == lastMessage.subscriptionId }
        } else {
            null
        }

        val defaultSmsSubscriptionId = SmsManager.getDefaultSmsSubscriptionId()
        val systemPreferredSimIdx = if (defaultSmsSubscriptionId >= 0) {
            availableSIMs.indexOfFirstOrNull { it.subscriptionId == defaultSmsSubscriptionId }
        } else {
            null
        }

        return userPreferredSimIdx ?: senderPreferredSimIdx ?: systemPreferredSimIdx ?: 0
    }

    private fun tryBlocking() {
        if (isOrWasThankYouInstalled()) {
            blockNumber()
        } else {
            FeatureLockedDialog(this) { }
        }
    }

    private fun blockNumber() {
        val numbers = participants.getAddresses()
        val numbersString = TextUtils.join(", ", numbers)
        val question = String.format(resources.getString(R.string.block_confirmation), numbersString)

        ConfirmationDialog(this, question) {
            ensureBackgroundThread {
                numbers.forEach {
                    addBlockedNumber(it)
                }
                refreshMessages()
                finish()
            }
        }
    }

    private fun askConfirmDelete() {
        val confirmationMessage = R.string.delete_whole_conversation_confirmation
        ConfirmationDialog(this, getString(confirmationMessage)) {
            ensureBackgroundThread {
                if (isRecycleBin) {
                    emptyMessagesRecycleBinForConversation(threadId)
                } else {
                    deleteConversation(threadId)
                }
                runOnUiThread {
                    refreshMessages()
                    finish()
                }
            }
        }
    }

    private fun askConfirmRestoreAll() {
        ConfirmationDialog(this, getString(R.string.restore_confirmation)) {
            ensureBackgroundThread {
                restoreAllMessagesFromRecycleBinForConversation(threadId)
                runOnUiThread {
                    refreshMessages()
                    finish()
                }
            }
        }
    }

    private fun archiveConversation() {
        ensureBackgroundThread {
            updateConversationArchivedStatus(threadId, true)
            runOnUiThread {
                refreshMessages()
                finish()
            }
        }
    }

    private fun unarchiveConversation() {
        ensureBackgroundThread {
            updateConversationArchivedStatus(threadId, false)
            runOnUiThread {
                refreshMessages()
                finish()
            }
        }
    }

    private fun dialNumber() {
        val phoneNumber = participants.first().phoneNumbers.first().normalizedNumber
        dialNumber(phoneNumber)
    }

    private fun managePeople() {
        if (thread_add_contacts.isVisible()) {
            hideKeyboard()
            thread_add_contacts.beGone()
        } else {
            showSelectedContacts()
            thread_add_contacts.beVisible()
            add_contact_or_number.requestFocus()
            showKeyboard(add_contact_or_number)
        }
    }

    private fun showSelectedContacts() {
        val properPrimaryColor = getProperPrimaryColor()

        val views = ArrayList<View>()
        participants.forEach { contact ->
            layoutInflater.inflate(R.layout.item_selected_contact, null).apply {
                val selectedContactBg = resources.getDrawable(R.drawable.item_selected_contact_background)
                (selectedContactBg as LayerDrawable).findDrawableByLayerId(R.id.selected_contact_bg).applyColorFilter(properPrimaryColor)
                selected_contact_holder.background = selectedContactBg

                selected_contact_name.text = contact.name
                selected_contact_name.setTextColor(properPrimaryColor.getContrastColor())
                selected_contact_remove.applyColorFilter(properPrimaryColor.getContrastColor())

                selected_contact_remove.setOnClickListener {
                    if (contact.rawId != participants.first().rawId) {
                        removeSelectedContact(contact.rawId)
                    }
                }
                views.add(this)
            }
        }
        showSelectedContact(views)
    }

    private fun addSelectedContact(contact: SimpleContact) {
        add_contact_or_number.setText("")
        if (participants.map { it.rawId }.contains(contact.rawId)) {
            return
        }

        participants.add(contact)
        showSelectedContacts()
        updateMessageType()
    }

    private fun markAsUnread() {
        ensureBackgroundThread {
            conversationsDB.markUnread(threadId)
            markThreadMessagesUnread(threadId)
            runOnUiThread {
                finish()
                bus?.post(Events.RefreshMessages())
            }
        }
    }

    private fun addNumberToContact() {
        val phoneNumber = participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.normalizedNumber ?: return
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, phoneNumber)
            launchActivityIntent(this)
        }
    }

    private fun renameConversation() {
        RenameConversationDialog(this, conversation!!) { title ->
            ensureBackgroundThread {
                conversation = renameConversation(conversation!!, newTitle = title)
                runOnUiThread {
                    setupThreadTitle()
                }
            }
        }
    }

    private fun showConversationDetails() {
        Intent(this, ConversationDetailsActivity::class.java).apply {
            putExtra(THREAD_ID, threadId)
            startActivity(this)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getThreadItems(): ArrayList<ThreadItem> {
        val items = ArrayList<ThreadItem>()
        if (isFinishing) {
            return items
        }

        messages.sortBy { it.date }

        val subscriptionIdToSimId = HashMap<Int, String>()
        subscriptionIdToSimId[-1] = "?"
        subscriptionManagerCompat().activeSubscriptionInfoList?.forEachIndexed { index, subscriptionInfo ->
            subscriptionIdToSimId[subscriptionInfo.subscriptionId] = "${index + 1}"
        }

        var prevDateTime = 0
        var prevSIMId = -2
        var hadUnreadItems = false
        val cnt = messages.size
        for (i in 0 until cnt) {
            val message = messages.getOrNull(i) ?: continue
            // do not show the date/time above every message, only if the difference between the 2 messages is at least MIN_DATE_TIME_DIFF_SECS,
            // or if the message is sent from a different SIM
            val isSentFromDifferentKnownSIM = prevSIMId != -1 && message.subscriptionId != -1 && prevSIMId != message.subscriptionId
            if (message.date - prevDateTime > MIN_DATE_TIME_DIFF_SECS || isSentFromDifferentKnownSIM) {
                val simCardID = subscriptionIdToSimId[message.subscriptionId] ?: "?"
                items.add(ThreadDateTime(message.date, simCardID))
                prevDateTime = message.date
            }
            items.add(message)

            if (message.type == Telephony.Sms.MESSAGE_TYPE_FAILED) {
                items.add(ThreadError(message.id, message.body))
            }

            if (message.type == Telephony.Sms.MESSAGE_TYPE_OUTBOX) {
                items.add(ThreadSending(message.id))
            }

            if (!message.read) {
                hadUnreadItems = true
                markMessageRead(message.id, message.isMMS)
                conversationsDB.markRead(threadId)
            }

            if (i == cnt - 1 && (message.type == Telephony.Sms.MESSAGE_TYPE_SENT)) {
                items.add(ThreadSent(message.id, delivered = message.status == Telephony.Sms.STATUS_COMPLETE))
            }
            prevSIMId = message.subscriptionId
        }

        if (hadUnreadItems) {
            bus?.post(Events.RefreshMessages())
        }

        if (!allMessagesFetched && messages.size >= MESSAGES_LIMIT) {
            val threadLoading = ThreadLoading(generateRandomId())
            items.add(0, threadLoading)
        }

        return items
    }

    private fun launchActivityForResult(intent: Intent, requestCode: Int, @StringRes error: Int = R.string.no_app_found) {
        hideKeyboard()
        try {
            startActivityForResult(intent, requestCode)
        } catch (e: ActivityNotFoundException) {
            showErrorToast(getString(error))
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun getAttachmentsDir(): File {
        return File(cacheDir, "attachments").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private fun launchCapturePhotoIntent() {
        val imageFile = File.createTempFile("attachment_", ".jpg", getAttachmentsDir())
        capturedImageUri = getMyFileUri(imageFile)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, capturedImageUri)
        }
        launchActivityForResult(intent, CAPTURE_PHOTO_INTENT)
    }

    private fun launchCaptureVideoIntent() {
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        launchActivityForResult(intent, CAPTURE_VIDEO_INTENT)
    }

    private fun launchCaptureAudioIntent() {
        val intent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
        launchActivityForResult(intent, CAPTURE_AUDIO_INTENT)
    }

    private fun launchGetContentIntent(mimeTypes: Array<String>, requestCode: Int) {
        Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            launchActivityForResult(this, requestCode)
        }
    }

    private fun launchPickContactIntent() {
        Intent(Intent.ACTION_PICK).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            launchActivityForResult(this, PICK_CONTACT_INTENT)
        }
    }

    private fun addContactAttachment(contactUri: Uri) {
        ensureBackgroundThread {
            val contact = ContactsHelper(this).getContactFromUri(contactUri)
            if (contact != null) {
                val outputFile = File(getAttachmentsDir(), "${contact.contactId}.vcf")
                val outputStream = outputFile.outputStream()

                VcfExporter().exportContacts(
                    activity = this,
                    outputStream = outputStream,
                    contacts = arrayListOf(contact),
                    showExportingToast = false,
                ) {
                    if (it == ExportResult.EXPORT_OK) {
                        val vCardUri = getMyFileUri(outputFile)
                        runOnUiThread {
                            addAttachment(vCardUri)
                        }
                    } else {
                        toast(R.string.unknown_error_occurred)
                    }
                }
            } else {
                toast(R.string.unknown_error_occurred)
            }
        }
    }

    private fun getAttachmentsAdapter(): AttachmentsAdapter? {
        val adapter = thread_attachments_recyclerview.adapter
        return adapter as? AttachmentsAdapter
    }

    private fun getAttachmentSelections() = getAttachmentsAdapter()?.attachments ?: emptyList()

    private fun addAttachment(uri: Uri) {
        val id = uri.toString()
        if (getAttachmentSelections().any { it.id == id }) {
            toast(R.string.duplicate_item_warning)
            return
        }

        val mimeType = contentResolver.getType(uri)
        if (mimeType == null) {
            toast(R.string.unknown_error_occurred)
            return
        }
        val isImage = mimeType.isImageMimeType()
        val isGif = mimeType.isGifMimeType()
        if (isGif || !isImage) {
            // is it assumed that images will always be compressed below the max MMS size limit
            val fileSize = getFileSizeFromUri(uri)
            val mmsFileSizeLimit = config.mmsFileSizeLimit
            if (mmsFileSizeLimit != FILE_SIZE_NONE && fileSize > mmsFileSizeLimit) {
                toast(R.string.attachment_sized_exceeds_max_limit, length = Toast.LENGTH_LONG)
                return
            }
        }

        var adapter = getAttachmentsAdapter()
        if (adapter == null) {
            adapter = AttachmentsAdapter(
                activity = this,
                recyclerView = thread_attachments_recyclerview,
                onAttachmentsRemoved = {
                    thread_attachments_recyclerview.beGone()
                    checkSendMessageAvailability()
                },
                onReady = { checkSendMessageAvailability() }
            )
            thread_attachments_recyclerview.adapter = adapter
        }

        thread_attachments_recyclerview.beVisible()
        val attachment = AttachmentSelection(
            id = id,
            uri = uri,
            mimetype = mimeType,
            filename = getFilenameFromUri(uri),
            isPending = isImage && !isGif
        )
        adapter.addAttachment(attachment)
        checkSendMessageAvailability()
    }

    private fun saveAttachment(resultData: Intent) {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        applicationContext.contentResolver.takePersistableUriPermission(resultData.data!!, takeFlags)
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            inputStream = contentResolver.openInputStream(Uri.parse(lastAttachmentUri))
            outputStream = contentResolver.openOutputStream(Uri.parse(resultData.dataString!!), "rwt")
            inputStream!!.copyTo(outputStream!!)
            outputStream.flush()
            toast(R.string.file_saved)
        } catch (e: Exception) {
            showErrorToast(e)
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
        lastAttachmentUri = null
    }

    private fun checkSendMessageAvailability() {
        if (thread_type_message.text!!.isNotEmpty() || (getAttachmentSelections().isNotEmpty() && !getAttachmentSelections().any { it.isPending })) {
            thread_send_message.isEnabled = true
            thread_send_message.isClickable = true
            thread_send_message.alpha = 0.9f
        } else {
            thread_send_message.isEnabled = false
            thread_send_message.isClickable = false
            thread_send_message.alpha = 0.4f
        }
        updateMessageType()
    }

    private fun sendMessage() {
        var text = thread_type_message.value
        if (text.isEmpty() && getAttachmentSelections().isEmpty()) {
            showErrorToast(getString(R.string.unknown_error_occurred))
            return
        }
        scrollToBottom()

        text = removeDiacriticsIfNeeded(text)

        val subscriptionId = availableSIMCards.getOrNull(currentSIMCardIndex)?.subscriptionId ?: SmsManager.getDefaultSmsSubscriptionId()

        if (isScheduledMessage) {
            sendScheduledMessage(text, subscriptionId)
        } else {
            sendNormalMessage(text, subscriptionId)
        }
    }

    private fun sendScheduledMessage(text: String, subscriptionId: Int) {
        if (scheduledDateTime.millis < System.currentTimeMillis() + 1000L) {
            toast(R.string.must_pick_time_in_the_future)
            launchScheduleSendDialog(scheduledDateTime)
            return
        }

        refreshedSinceSent = false
        try {
            ensureBackgroundThread {
                val messageId = scheduledMessage?.id ?: generateRandomId()
                val message = buildScheduledMessage(text, subscriptionId, messageId)
                if (messages.isEmpty()) {
                    // create a temporary thread until a real message is sent
                    threadId = message.threadId
                    createTemporaryThread(message, message.threadId, conversation)
                }
                val conversation = conversationsDB.getConversationWithThreadId(threadId)
                if (conversation != null) {
                    val nowSeconds = (System.currentTimeMillis() / 1000).toInt()
                    conversationsDB.insertOrUpdate(conversation.copy(date = nowSeconds, snippet = message.body))
                }
                scheduleMessage(message)
                insertOrUpdateMessage(message)

                runOnUiThread {
                    clearCurrentMessage()
                    hideScheduleSendUi()
                    scheduledMessage = null
                }
            }
        } catch (e: Exception) {
            showErrorToast(e.localizedMessage ?: getString(R.string.unknown_error_occurred))
        }
    }

    private fun sendNormalMessage(text: String, subscriptionId: Int) {
        val addresses = participants.getAddresses()
        val attachments = buildMessageAttachments()

        try {
            refreshedSinceSent = false
            sendMessageCompat(text, addresses, subscriptionId, attachments, messageToResend)
            ensureBackgroundThread {
                val messageIds = messages.map { it.id }
                val messages = getMessages(threadId, getImageResolutions = true, limit = maxOf(1, attachments.size))
                    .filter { it.id !in messageIds }
                for (message in messages) {
                    insertOrUpdateMessage(message)
                }
            }
            clearCurrentMessage()

        } catch (e: Exception) {
            showErrorToast(e)
        } catch (e: Error) {
            showErrorToast(e.localizedMessage ?: getString(R.string.unknown_error_occurred))
        }
    }

    private fun clearCurrentMessage() {
        thread_type_message.setText("")
        getAttachmentsAdapter()?.clear()
        checkSendMessageAvailability()
    }

    private fun insertOrUpdateMessage(message: Message) {
        if (messages.map { it.id }.contains(message.id)) {
            val messageToReplace = messages.find { it.id == message.id }
            messages[messages.indexOf(messageToReplace)] = message
        } else {
            messages.add(message)
        }

        val newItems = getThreadItems()
        runOnUiThread {
            getOrCreateThreadAdapter().updateMessages(newItems, newItems.lastIndex)
            if (!refreshedSinceSent) {
                refreshMessages()
            }
        }
        messagesDB.insertOrUpdate(message)
        updateConversationArchivedStatus(message.threadId, false)
    }

    // show selected contacts, properly split to new lines when appropriate
    // based on https://stackoverflow.com/a/13505029/1967672
    private fun showSelectedContact(views: ArrayList<View>) {
        selected_contacts.removeAllViews()
        var newLinearLayout = LinearLayout(this)
        newLinearLayout.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        newLinearLayout.orientation = LinearLayout.HORIZONTAL

        val sideMargin = (selected_contacts.layoutParams as RelativeLayout.LayoutParams).leftMargin
        val mediumMargin = resources.getDimension(R.dimen.medium_margin).toInt()
        val parentWidth = realScreenSize.x - sideMargin * 2
        val firstRowWidth = parentWidth - resources.getDimension(R.dimen.normal_icon_size).toInt() + sideMargin / 2
        var widthSoFar = 0
        var isFirstRow = true

        for (i in views.indices) {
            val layout = LinearLayout(this)
            layout.orientation = LinearLayout.HORIZONTAL
            layout.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            layout.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            views[i].measure(0, 0)

            var params = LayoutParams(views[i].measuredWidth, LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, mediumMargin, 0)
            layout.addView(views[i], params)
            layout.measure(0, 0)
            widthSoFar += views[i].measuredWidth + mediumMargin

            val checkWidth = if (isFirstRow) firstRowWidth else parentWidth
            if (widthSoFar >= checkWidth) {
                isFirstRow = false
                selected_contacts.addView(newLinearLayout)
                newLinearLayout = LinearLayout(this)
                newLinearLayout.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                newLinearLayout.orientation = LinearLayout.HORIZONTAL
                params = LayoutParams(layout.measuredWidth, layout.measuredHeight)
                params.topMargin = mediumMargin
                newLinearLayout.addView(layout, params)
                widthSoFar = layout.measuredWidth
            } else {
                if (!isFirstRow) {
                    (layout.layoutParams as LayoutParams).topMargin = mediumMargin
                }
                newLinearLayout.addView(layout)
            }
        }
        selected_contacts.addView(newLinearLayout)
    }

    private fun removeSelectedContact(id: Int) {
        participants = participants.filter { it.rawId != id }.toMutableList() as ArrayList<SimpleContact>
        showSelectedContacts()
        updateMessageType()
    }

    private fun getPhoneNumbersFromIntent(): ArrayList<String> {
        val numberFromIntent = intent.getStringExtra(THREAD_NUMBER)
        val numbers = ArrayList<String>()

        if (numberFromIntent != null) {
            if (numberFromIntent.startsWith('[') && numberFromIntent.endsWith(']')) {
                val type = object : TypeToken<List<String>>() {}.type
                numbers.addAll(Gson().fromJson(numberFromIntent, type))
            } else {
                numbers.add(numberFromIntent)
            }
        }
        return numbers
    }

    private fun fixParticipantNumbers(participants: ArrayList<SimpleContact>, properNumbers: ArrayList<String>): ArrayList<SimpleContact> {
        for (number in properNumbers) {
            for (participant in participants) {
                participant.phoneNumbers = participant.phoneNumbers.map {
                    val numberWithoutPlus = number.replace("+", "")
                    if (numberWithoutPlus == it.normalizedNumber.trim()) {
                        if (participant.name == it.normalizedNumber) {
                            participant.name = number
                        }
                        PhoneNumber(number, 0, "", number)
                    } else {
                        PhoneNumber(it.normalizedNumber, 0, "", it.normalizedNumber)
                    }
                } as ArrayList<PhoneNumber>
            }
        }

        return participants
    }

    fun saveMMS(mimeType: String, path: String) {
        hideKeyboard()
        lastAttachmentUri = path
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = mimeType
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, path.split("/").last())
            launchActivityForResult(this, PICK_SAVE_FILE_INTENT, error = R.string.system_service_disabled)
        }
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun refreshMessages(event: Events.RefreshMessages) {
        if (isRecycleBin) {
            return
        }

        refreshedSinceSent = true
        allMessagesFetched = false
        oldestMessageDate = -1

        if (isActivityVisible) {
            notificationManager.cancel(threadId.hashCode())
        }

        val lastMaxId = messages.filterNot { it.isScheduled }.maxByOrNull { it.id }?.id ?: 0L
        val newThreadId = getThreadId(participants.getAddresses().toSet())
        val newMessages = getMessages(newThreadId, getImageResolutions = true, includeScheduledMessages = false)

        if (messages.isNotEmpty() && messages.all { it.isScheduled } && newMessages.isNotEmpty()) {
            // update scheduled messages with real thread id
            threadId = newThreadId
            updateScheduledMessagesThreadId(messages = messages.filter { it.threadId != threadId }, threadId)
        }

        messages = newMessages.apply {
            val scheduledMessages = messagesDB.getScheduledThreadMessages(threadId)
                .filterNot { it.isScheduled && it.millis() < System.currentTimeMillis() }
            addAll(scheduledMessages)
            if (config.useRecycleBin) {
                val recycledMessages = messagesDB.getThreadMessagesFromRecycleBin(threadId).toSet()
                removeAll(recycledMessages)
            }
        }

        messages.filter { !it.isScheduled && !it.isReceivedMessage() && it.id > lastMaxId }.forEach { latestMessage ->
            messagesDB.insertOrIgnore(latestMessage)
        }

        setupAdapter()
        runOnUiThread {
            setupSIMSelector()
        }
    }

    private fun isMmsMessage(text: String): Boolean {
        val isGroupMms = participants.size > 1 && config.sendGroupMessageMMS
        val isLongMmsMessage = isLongMmsMessage(text)
        return getAttachmentSelections().isNotEmpty() || isGroupMms || isLongMmsMessage
    }

    private fun updateMessageType() {
        val text = thread_type_message.text.toString()
        val stringId = if (isMmsMessage(text)) {
            R.string.mms
        } else {
            R.string.sms
        }
        thread_send_message.setText(stringId)
    }

    private fun showScheduledMessageInfo(message: Message) {
        val items = arrayListOf(
            RadioItem(TYPE_EDIT, getString(R.string.update_message)),
            RadioItem(TYPE_SEND, getString(R.string.send_now)),
            RadioItem(TYPE_DELETE, getString(R.string.delete))
        )
        RadioGroupDialog(activity = this, items = items, titleId = R.string.scheduled_message) { any ->
            when (any as Int) {
                TYPE_DELETE -> cancelScheduledMessageAndRefresh(message.id)
                TYPE_EDIT -> editScheduledMessage(message)
                TYPE_SEND -> {
                    messages.removeAll { message.id == it.id }
                    extractAttachments(message)
                    sendNormalMessage(message.body, message.subscriptionId)
                    cancelScheduledMessageAndRefresh(message.id)
                }
            }
        }
    }

    private fun extractAttachments(message: Message) {
        val messageAttachment = message.attachment
        if (messageAttachment != null) {
            for (attachment in messageAttachment.attachments) {
                addAttachment(attachment.getUri())
            }
        }
    }

    private fun editScheduledMessage(message: Message) {
        scheduledMessage = message
        clearCurrentMessage()
        thread_type_message.setText(message.body)
        extractAttachments(message)
        scheduledDateTime = DateTime(message.millis())
        showScheduleMessageDialog()
    }

    private fun cancelScheduledMessageAndRefresh(messageId: Long) {
        ensureBackgroundThread {
            deleteScheduledMessage(messageId)
            cancelScheduleSendPendingIntent(messageId)
            refreshMessages()
        }
    }

    private fun launchScheduleSendDialog(originalDateTime: DateTime? = null) {
        askForExactAlarmPermissionIfNeeded {
            ScheduleMessageDialog(this, originalDateTime) { newDateTime ->
                if (newDateTime != null) {
                    scheduledDateTime = newDateTime
                    showScheduleMessageDialog()
                }
            }
        }
    }

    private fun setupScheduleSendUi() {
        val textColor = getProperTextColor()
        scheduled_message_holder.background.applyColorFilter(getProperPrimaryColor().darkenColor())
        scheduled_message_icon.applyColorFilter(textColor)
        scheduled_message_button.apply {
            setTextColor(textColor)
            setOnClickListener {
                launchScheduleSendDialog(scheduledDateTime)
            }
        }

        discard_scheduled_message.apply {
            applyColorFilter(textColor)
            setOnClickListener {
                hideScheduleSendUi()
                if (scheduledMessage != null) {
                    cancelScheduledMessageAndRefresh(scheduledMessage!!.id)
                    scheduledMessage = null
                }
            }
        }
    }

    private fun showScheduleMessageDialog() {
        isScheduledMessage = true
        updateSendButtonDrawable()
        scheduled_message_holder.beVisible()

        val dateTime = scheduledDateTime
        val millis = dateTime.millis
        scheduled_message_button.text = if (dateTime.yearOfCentury().get() > DateTime.now().yearOfCentury().get()) {
            millis.formatDate(this)
        } else {
            val flags = FORMAT_SHOW_TIME or FORMAT_SHOW_DATE or FORMAT_NO_YEAR
            DateUtils.formatDateTime(this, millis, flags)
        }
    }

    private fun hideScheduleSendUi() {
        isScheduledMessage = false
        scheduled_message_holder.beGone()
        updateSendButtonDrawable()
    }

    private fun updateSendButtonDrawable() {
        val drawableResId = if (isScheduledMessage) {
            R.drawable.ic_schedule_send_vector
        } else {
            R.drawable.ic_send_vector
        }
        ResourcesCompat.getDrawable(resources, drawableResId, theme)?.apply {
            applyColorFilter(getProperTextColor())
            thread_send_message.setCompoundDrawablesWithIntrinsicBounds(null, this, null, null)
        }
    }

    private fun buildScheduledMessage(text: String, subscriptionId: Int, messageId: Long): Message {
        val threadId = if (messages.isEmpty()) messageId else threadId
        return Message(
            id = messageId,
            body = text,
            type = MESSAGE_TYPE_QUEUED,
            status = STATUS_NONE,
            participants = participants,
            date = (scheduledDateTime.millis / 1000).toInt(),
            read = false,
            threadId = threadId,
            isMMS = isMmsMessage(text),
            attachment = MessageAttachment(messageId, text, buildMessageAttachments(messageId)),
            senderPhoneNumber = "",
            senderName = "",
            senderPhotoUri = "",
            subscriptionId = subscriptionId,
            isScheduled = true
        )
    }

    private fun buildMessageAttachments(messageId: Long = -1L) = getAttachmentSelections()
        .map { Attachment(null, messageId, it.uri.toString(), it.mimetype, 0, 0, it.filename) }
        .toArrayList()

    private fun setupAttachmentPickerView() {
        val buttonColors = arrayOf(
            R.color.md_red_500,
            R.color.md_brown_500,
            R.color.md_pink_500,
            R.color.md_purple_500,
            R.color.md_teal_500,
            R.color.md_green_500,
            R.color.md_indigo_500,
            R.color.md_blue_500
        ).map { ResourcesCompat.getColor(resources, it, theme) }
        arrayOf(
            choose_photo_icon,
            choose_video_icon,
            take_photo_icon,
            record_video_icon,
            record_audio_icon,
            pick_file_icon,
            pick_contact_icon,
            schedule_message_icon
        ).forEachIndexed { index, icon ->
            val iconColor = buttonColors[index]
            icon.background.applyColorFilter(iconColor)
            icon.applyColorFilter(iconColor.getContrastColor())
        }

        val textColor = getProperTextColor()
        arrayOf(
            choose_photo_text,
            choose_video_text,
            take_photo_text,
            record_video_text,
            record_audio_text,
            pick_file_text,
            pick_contact_text,
            schedule_message_text
        ).forEach { it.setTextColor(textColor) }

        choose_photo.setOnClickListener {
            launchGetContentIntent(arrayOf("image/*"), PICK_PHOTO_INTENT)
        }
        choose_video.setOnClickListener {
            launchGetContentIntent(arrayOf("video/*"), PICK_VIDEO_INTENT)
        }
        take_photo.setOnClickListener {
            launchCapturePhotoIntent()
        }
        record_video.setOnClickListener {
            launchCaptureVideoIntent()
        }
        record_audio.setOnClickListener {
            launchCaptureAudioIntent()
        }
        pick_file.setOnClickListener {
            launchGetContentIntent(arrayOf("*/*"), PICK_DOCUMENT_INTENT)
        }
        pick_contact.setOnClickListener {
            launchPickContactIntent()
        }
        schedule_message.setOnClickListener {
            if (isScheduledMessage) {
                launchScheduleSendDialog(scheduledDateTime)
            } else {
                launchScheduleSendDialog()
            }
        }
    }

    private fun showAttachmentPicker() {
        attachment_picker_divider.showWithAnimation()
        attachment_picker_holder.showWithAnimation()
        animateAttachmentButton(rotation = -135f)
    }

    private fun maybeSetupRecycleBinView() {
        if (isRecycleBin) {
            thread_send_message_holder.beGone()
        }
    }

    private fun hideAttachmentPicker() {
        attachment_picker_divider.beGone()
        attachment_picker_holder.apply {
            beGone()
            updateLayoutParams<ConstraintLayout.LayoutParams> {
                height = config.keyboardHeight
            }
        }
        animateAttachmentButton(rotation = 0f)
    }

    private fun animateAttachmentButton(rotation: Float) {
        thread_add_attachment.animate()
            .rotation(rotation)
            .setDuration(500L)
            .setInterpolator(OvershootInterpolator())
            .start()
    }

    private fun setupKeyboardListener() {
        window.decorView.setOnApplyWindowInsetsListener { view, insets ->
            showOrHideAttachmentPicker()
            view.onApplyWindowInsets(insets)
        }

        val callback = object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                super.onPrepare(animation)
                showOrHideAttachmentPicker()
            }

            override fun onProgress(insets: WindowInsetsCompat, runningAnimations: MutableList<WindowInsetsAnimationCompat>) = insets
        }
        ViewCompat.setWindowInsetsAnimationCallback(window.decorView, callback)
    }

    private fun showOrHideAttachmentPicker() {
        val type = WindowInsetsCompat.Type.ime()
        val insets = ViewCompat.getRootWindowInsets(window.decorView) ?: return
        val isKeyboardVisible = insets.isVisible(type)

        if (isKeyboardVisible) {
            val keyboardHeight = insets.getInsets(type).bottom
            val bottomBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            // check keyboard height just to be sure, 150 seems like a good middle ground between ime and navigation bar
            config.keyboardHeight = if (keyboardHeight > 150) {
                keyboardHeight - bottomBarHeight
            } else {
                getDefaultKeyboardHeight()
            }
            hideAttachmentPicker()
        } else if (isAttachmentPickerVisible) {
            showAttachmentPicker()
        }
    }

    private fun getBottomBarColor() = if (baseConfig.isUsingSystemTheme) {
        resources.getColor(R.color.you_bottom_bar_color)
    } else {
        getBottomNavigationBackgroundColor()
    }
}
