package com.simplemobiletools.smsmessenger.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
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
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.RelativeLayout
import androidx.core.content.res.ResourcesCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.PhoneNumber
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.AutoCompleteTextViewAdapter
import com.simplemobiletools.smsmessenger.adapters.ThreadAdapter
import com.simplemobiletools.smsmessenger.dialogs.ScheduleMessageDialog
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.*
import com.simplemobiletools.smsmessenger.models.*
import kotlinx.android.synthetic.main.activity_thread.*
import kotlinx.android.synthetic.main.item_attachment.view.*
import kotlinx.android.synthetic.main.item_selected_contact.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.joda.time.DateTime
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class ThreadActivity : SimpleActivity() {
    private val MIN_DATE_TIME_DIFF_SECS = 300
    private val PICK_ATTACHMENT_INTENT = 1
    private val PICK_SAVE_FILE_INTENT = 11
    private val TAKE_PHOTO_INTENT = 42

    private val TYPE_TAKE_PHOTO = 12
    private val TYPE_CHOOSE_PHOTO = 13

    private val TYPE_EDIT = 14
    private val TYPE_SEND = 15
    private val TYPE_DELETE = 16

    private var threadId = 0L
    private var currentSIMCardIndex = 0
    private var isActivityVisible = false
    private var refreshedSinceSent = false
    private var threadItems = ArrayList<ThreadItem>()
    private var bus: EventBus? = null
    private var participants = ArrayList<SimpleContact>()
    private var privateContacts = ArrayList<SimpleContact>()
    private var messages = ArrayList<Message>()
    private val availableSIMCards = ArrayList<SIMCard>()
    private var attachmentSelections = mutableMapOf<String, AttachmentSelection>()
    private val imageCompressor by lazy { ImageCompressor(this) }
    private var lastAttachmentUri: String? = null
    private var capturedImageUri: Uri? = null
    private var loadingOlderMessages = false
    private var allMessagesFetched = false
    private var oldestMessageDate = -1

    private var isScheduledMessage: Boolean = false
    private var scheduledMessage: Message? = null
    private lateinit var scheduledDateTime: DateTime

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thread)
        setupOptionsMenu()
        refreshMenuItems()

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

        bus = EventBus.getDefault()
        bus!!.register(this)
        handlePermission(PERMISSION_READ_PHONE_STATE) {
            if (it) {
                setupButtons()
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
                }
            } else {
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(thread_toolbar, NavigationIcon.Arrow)

        val smsDraft = getSmsDraft(threadId)
        if (smsDraft != null) {
            thread_type_message.setText(smsDraft)
        }
        isActivityVisible = true
    }

    override fun onPause() {
        super.onPause()

        if (thread_type_message.value != "" && attachmentSelections.isEmpty()) {
            saveSmsDraft(thread_type_message.value, threadId)
        } else {
            deleteSmsDraft(threadId)
        }

        bus?.post(Events.RefreshMessages())

        isActivityVisible = false
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    private fun refreshMenuItems() {
        val firstPhoneNumber = participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.value
        thread_toolbar.menu.apply {
            findItem(R.id.delete).isVisible = threadItems.isNotEmpty()
            findItem(R.id.block_number).isVisible = isNougatPlus()
            findItem(R.id.dial_number).isVisible = participants.size == 1
            findItem(R.id.mark_as_unread).isVisible = threadItems.isNotEmpty()

            // allow saving number in cases when we dont have it stored yet and it is a casual readable number
            findItem(R.id.add_number_to_contact).isVisible = participants.size == 1 && participants.first().name == firstPhoneNumber && firstPhoneNumber.any {
                it.isDigit()
            }
        }
    }

    private fun setupOptionsMenu() {
        thread_toolbar.setOnMenuItemClickListener { menuItem ->
            if (participants.isEmpty()) {
                return@setOnMenuItemClickListener true
            }

            when (menuItem.itemId) {
                R.id.block_number -> blockNumber()
                R.id.delete -> askConfirmDelete()
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

        if (requestCode == TAKE_PHOTO_INTENT) {
            addAttachment(capturedImageUri!!)
        } else if (requestCode == PICK_ATTACHMENT_INTENT && resultData != null && resultData.data != null) {
            addAttachment(resultData.data!!)
        } else if (requestCode == PICK_SAVE_FILE_INTENT && resultData != null && resultData.data != null) {
            saveAttachment(resultData)
        }
    }

    private fun onHomePressed() {
        hideKeyboard()
        Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(this)
        }
        finish()
    }

    private fun setupCachedMessages(callback: () -> Unit) {
        ensureBackgroundThread {
            messages = try {
                messagesDB.getThreadMessages(threadId).toMutableList() as ArrayList<Message>
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
        val privateCursor = getMyContactsCursor(false, true)
        ensureBackgroundThread {
            privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)

            val cachedMessagesCode = messages.clone().hashCode()
            messages = getMessages(threadId, true)

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

            messages.chunked(30).forEach { currentMessages ->
                messagesDB.insertMessages(*currentMessages.toTypedArray())
            }

            setupAttachmentSizes()
            setupAdapter()
            runOnUiThread {
                setupThreadTitle()
                setupSIMSelector()
            }
        }
    }

    private fun setupAdapter() {
        threadItems = getThreadItems()

        runOnUiThread {
            refreshMenuItems()

            val currAdapter = thread_messages_list.adapter
            if (currAdapter == null) {
                ThreadAdapter(
                    activity = this,
                    messages = threadItems,
                    recyclerView = thread_messages_list,
                    itemClick = { handleItemClick(it) },
                    onThreadIdUpdate = { threadId = it }
                ).apply {
                    thread_messages_list.adapter = this
                }

                thread_messages_list.endlessScrollListener = object : MyRecyclerView.EndlessScrollListener {
                    override fun updateBottom() {}

                    override fun updateTop() {
                        fetchNextMessages()
                    }
                }
            } else {
                (currAdapter as ThreadAdapter).updateMessages(threadItems)
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

        confirm_inserted_number?.setOnClickListener {
            val number = add_contact_or_number.value
            val phoneNumber = PhoneNumber(number, 0, "", number)
            val contact = SimpleContact(number.hashCode(), number.hashCode(), number, "", arrayListOf(phoneNumber), ArrayList(), ArrayList())
            addSelectedContact(contact)
        }
    }

    private fun handleItemClick(any: Any) {
        when {
            any is Message && any.isScheduled -> showScheduledMessageInfo(any)
            any is ThreadError -> thread_type_message.setText(any.messageText)
        }
    }

    private fun fetchNextMessages() {
        if (messages.isEmpty() || allMessagesFetched || loadingOlderMessages) {
            return
        }

        val dateOfFirstItem = messages.first().date
        if (oldestMessageDate == dateOfFirstItem) {
            allMessagesFetched = true
            return
        }

        oldestMessageDate = dateOfFirstItem
        loadingOlderMessages = true

        ensureBackgroundThread {
            val firstItem = messages.first()
            val olderMessages = getMessages(threadId, true, oldestMessageDate)
                .filter { message -> !messages.contains(message) }

            messages.addAll(0, olderMessages)
            threadItems = getThreadItems()

            allMessagesFetched = olderMessages.size < MESSAGES_LIMIT || olderMessages.isEmpty()

            runOnUiThread {
                loadingOlderMessages = false
                val itemAtRefreshIndex = threadItems.indexOfFirst { it == firstItem }
                (thread_messages_list.adapter as ThreadAdapter).apply {
                    updateMessages(threadItems, itemAtRefreshIndex)
                }
            }
        }
    }

    private fun setupButtons() {
        updateTextColors(thread_holder)
        val textColor = getProperTextColor()
        thread_send_message.apply {
            setTextColor(textColor)
            compoundDrawables.forEach { it?.applyColorFilter(textColor) }
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
            checkSendMessageAvailability()
            val messageString = if (config.useSimpleCharacters) it.normalizeString() else it
            val messageLength = SmsMessage.calculateLength(messageString, false)
            thread_character_counter.text = "${messageLength[2]}/${messageLength[0]}"
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
            takeOrPickPhotoVideo()
        }

        if (intent.extras?.containsKey(THREAD_ATTACHMENT_URI) == true) {
            val uri = Uri.parse(intent.getStringExtra(THREAD_ATTACHMENT_URI))
            addAttachment(uri)
        } else if (intent.extras?.containsKey(THREAD_ATTACHMENT_URIS) == true) {
            (intent.getSerializableExtra(THREAD_ATTACHMENT_URIS) as? ArrayList<Uri>)?.forEach {
                addAttachment(it)
            }
        }

        setupScheduleSendUi()
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
        }
    }

    private fun setupThreadTitle() {
        val threadTitle = participants.getThreadTitle()
        if (threadTitle.isNotEmpty()) {
            thread_toolbar.title = participants.getThreadTitle()
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
            thread_select_sim_number.text = (availableSIMCards[currentSIMCardIndex].id).toString()
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
        ConfirmationDialog(this, getString(R.string.delete_whole_conversation_confirmation)) {
            ensureBackgroundThread {
                deleteConversation(threadId)
                runOnUiThread {
                    refreshMessages()
                    finish()
                }
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

        return items
    }

    private fun takeOrPickPhotoVideo() {
        val items = arrayListOf(
            RadioItem(TYPE_TAKE_PHOTO, getString(R.string.take_photo)),
            RadioItem(TYPE_CHOOSE_PHOTO, getString(R.string.choose_photo))
        )
        RadioGroupDialog(this, items = items) {
            val checkedId = it as Int
            if (checkedId == TYPE_TAKE_PHOTO) {
                launchTakePhotoIntent()
            } else if (checkedId == TYPE_CHOOSE_PHOTO) {
                launchPickPhotoVideoIntent()
            }
        }
    }

    private fun launchTakePhotoIntent() {
        val imageFile = createImageFile()
        capturedImageUri = getMyFileUri(imageFile)
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, capturedImageUri)
            }
            startActivityForResult(intent, TAKE_PHOTO_INTENT)
        } catch (e: ActivityNotFoundException) {
            showErrorToast(getString(R.string.no_app_found))
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun launchPickPhotoVideoIntent() {
        hideKeyboard()
        val mimeTypes = arrayOf("image/*", "video/*")
        Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)

            try {
                startActivityForResult(this, PICK_ATTACHMENT_INTENT)
            } catch (e: ActivityNotFoundException) {
                showErrorToast(getString(R.string.no_app_found))
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private fun addAttachment(uri: Uri) {
        val originalUriString = uri.toString()
        if (attachmentSelections.containsKey(originalUriString)) {
            return
        }

        attachmentSelections[originalUriString] = AttachmentSelection(uri, false)
        val attachmentView = addAttachmentView(originalUriString, uri)
        val mimeType = contentResolver.getType(uri) ?: return

        if (mimeType.isImageMimeType() && config.mmsFileSizeLimit != FILE_SIZE_NONE) {
            val selection = attachmentSelections[originalUriString]
            attachmentSelections[originalUriString] = selection!!.copy(isPending = true)
            checkSendMessageAvailability()
            attachmentView.thread_attachment_progress.beVisible()
            imageCompressor.compressImage(uri, config.mmsFileSizeLimit) { compressedUri ->
                runOnUiThread {
                    if (compressedUri != null) {
                        attachmentSelections[originalUriString] = AttachmentSelection(compressedUri, false)
                        loadAttachmentPreview(attachmentView, compressedUri)
                    } else {
                        toast(R.string.compress_error)
                        removeAttachment(attachmentView, originalUriString)
                    }
                    checkSendMessageAvailability()
                    attachmentView.thread_attachment_progress.beGone()
                }
            }
        }
    }

    private fun addAttachmentView(originalUri: String, uri: Uri): View {
        thread_attachments_holder.beVisible()
        val attachmentView = layoutInflater.inflate(R.layout.item_attachment, null).apply {
            thread_attachments_wrapper.addView(this)
            thread_remove_attachment.setOnClickListener {
                removeAttachment(this, originalUri)
            }
        }

        loadAttachmentPreview(attachmentView, uri)
        return attachmentView
    }

    private fun loadAttachmentPreview(attachmentView: View, uri: Uri) {
        if (isDestroyed || isFinishing) {
            return
        }

        val roundedCornersRadius = resources.getDimension(R.dimen.medium_margin).toInt()
        val options = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .transform(CenterCrop(), RoundedCorners(roundedCornersRadius))

        Glide.with(attachmentView.thread_attachment_preview)
            .load(uri)
            .transition(DrawableTransitionOptions.withCrossFade())
            .apply(options)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                    attachmentView.thread_attachment_preview.beGone()
                    attachmentView.thread_remove_attachment.beGone()
                    return false
                }

                override fun onResourceReady(dr: Drawable?, a: Any?, t: Target<Drawable>?, d: DataSource?, i: Boolean): Boolean {
                    attachmentView.thread_attachment_preview.beVisible()
                    attachmentView.thread_remove_attachment.beVisible()
                    checkSendMessageAvailability()
                    return false
                }
            })
            .into(attachmentView.thread_attachment_preview)
    }

    private fun removeAttachment(attachmentView: View, originalUri: String) {
        thread_attachments_wrapper.removeView(attachmentView)
        attachmentSelections.remove(originalUri)
        if (attachmentSelections.isEmpty()) {
            thread_attachments_holder.beGone()
        }
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
        if (thread_type_message.text!!.isNotEmpty() || (attachmentSelections.isNotEmpty() && !attachmentSelections.values.any { it.isPending })) {
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
        if (text.isEmpty() && attachmentSelections.isEmpty()) {
            showErrorToast(getString(R.string.unknown_error_occurred))
            return
        }

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
                    createTemporaryThread(message, message.threadId)
                }
                messagesDB.insertOrUpdate(message)
                val conversation = conversationsDB.getConversationWithThreadId(threadId)
                if (conversation != null) {
                    val nowSeconds = (System.currentTimeMillis() / 1000).toInt()
                    conversationsDB.insertOrUpdate(conversation.copy(date = nowSeconds))
                }
                scheduleMessage(message)
            }
            clearCurrentMessage()
            hideScheduleSendUi()
            scheduledMessage = null

            if (!refreshedSinceSent) {
                refreshMessages()
            }
        } catch (e: Exception) {
            showErrorToast(e.localizedMessage ?: getString(R.string.unknown_error_occurred))
        }
    }

    private fun sendNormalMessage(text: String, subscriptionId: Int) {
        val addresses = participants.getAddresses()
        val attachments = attachmentSelections.values
            .map { it.uri }

        try {
            refreshedSinceSent = false
            sendMessage(text, addresses, subscriptionId, attachments)
            clearCurrentMessage()

            if (!refreshedSinceSent) {
                refreshMessages()
            }
        } catch (e: Exception) {
            showErrorToast(e)
        } catch (e: Error) {
            showErrorToast(e.localizedMessage ?: getString(R.string.unknown_error_occurred))
        }
    }

    private fun clearCurrentMessage() {
        thread_type_message.setText("")
        attachmentSelections.clear()
        thread_attachments_holder.beGone()
        thread_attachments_wrapper.removeAllViews()
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

    fun startContactDetailsIntent(contact: SimpleContact) {
        val simpleContacts = "com.simplemobiletools.contacts.pro"
        val simpleContactsDebug = "com.simplemobiletools.contacts.pro.debug"
        if (contact.rawId > 1000000 && contact.contactId > 1000000 && contact.rawId == contact.contactId &&
            (isPackageInstalled(simpleContacts) || isPackageInstalled(simpleContactsDebug))
        ) {
            Intent().apply {
                action = Intent.ACTION_VIEW
                putExtra(CONTACT_ID, contact.rawId)
                putExtra(IS_PRIVATE, true)
                setPackage(if (isPackageInstalled(simpleContacts)) simpleContacts else simpleContactsDebug)
                setDataAndType(ContactsContract.Contacts.CONTENT_LOOKUP_URI, "vnd.android.cursor.dir/person")
                launchActivityIntent(this)
            }
        } else {
            ensureBackgroundThread {
                val lookupKey = SimpleContactsHelper(this).getContactLookupKey((contact).rawId.toString())
                val publicUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
                runOnUiThread {
                    launchViewContactIntent(publicUri)
                }
            }
        }
    }

    fun saveMMS(mimeType: String, path: String) {
        hideKeyboard()
        lastAttachmentUri = path
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = mimeType
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, path.split("/").last())

            try {
                startActivityForResult(this, PICK_SAVE_FILE_INTENT)
            } catch (e: ActivityNotFoundException) {
                showErrorToast(getString(R.string.system_service_disabled))
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun refreshMessages(event: Events.RefreshMessages) {
        refreshedSinceSent = true
        allMessagesFetched = false
        oldestMessageDate = -1

        if (isActivityVisible) {
            notificationManager.cancel(threadId.hashCode())
        }

        val newThreadId = getThreadId(participants.getAddresses().toSet())
        val newMessages = getMessages(newThreadId, false)
        messages = if (messages.all { it.isScheduled } && newMessages.isNotEmpty()) {
            threadId = newThreadId
            // update scheduled messages with real thread id
            updateScheduledMessagesThreadId(messages, newThreadId)
            getMessages(newThreadId, true)
        } else {
            getMessages(threadId, true)
        }

        val lastMaxId = messages.filterNot { it.isScheduled }.maxByOrNull { it.id }?.id ?: 0L

        messages.filter { !it.isReceivedMessage() && it.id > lastMaxId }.forEach { latestMessage ->
            // subscriptionIds seem to be not filled out at sending with multiple SIM cards, so fill it manually
            if ((subscriptionManagerCompat().activeSubscriptionInfoList?.size ?: 0) > 1) {
                val subscriptionId = availableSIMCards.getOrNull(currentSIMCardIndex)?.subscriptionId
                if (subscriptionId != null) {
                    updateMessageSubscriptionId(latestMessage.id, subscriptionId)
                    latestMessage.subscriptionId = subscriptionId
                }
            }

            messagesDB.insertOrIgnore(latestMessage)
        }

        setupAdapter()
        setupSIMSelector()
    }

    private fun isMmsMessage(text: String): Boolean {
        val isGroupMms = participants.size > 1 && config.sendGroupMessageMMS
        val isLongMmsMessage = isLongMmsMessage(text) && config.sendLongMessageMMS
        return attachmentSelections.isNotEmpty() || isGroupMms || isLongMmsMessage
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

    private fun createImageFile(): File {
        val outputDirectory = File(cacheDir, "captured").apply {
            if (!exists()) {
                mkdirs()
            }
        }
        return File.createTempFile("IMG_", ".jpg", outputDirectory)
    }

    private fun showScheduledMessageInfo(message: Message) {
        val items = arrayListOf(
            RadioItem(TYPE_EDIT, getString(R.string.update_message)),
            RadioItem(TYPE_SEND, getString(R.string.send_now)),
            RadioItem(TYPE_DELETE, getString(R.string.delete))
        )
        RadioGroupDialog(activity = this, items = items, titleId = R.string.scheduled_message) {
            when (it as Int) {
                TYPE_DELETE -> cancelScheduledMessageAndRefresh(message.id)
                TYPE_EDIT -> editScheduledMessage(message)
                TYPE_SEND -> {
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
        ScheduleMessageDialog(this, originalDateTime) { newDateTime ->
            if (newDateTime != null) {
                scheduledDateTime = newDateTime
                showScheduleMessageDialog()
            }
        }
    }

    private fun setupScheduleSendUi() {
        val textColor = getProperTextColor()
        scheduled_message_holder.background.applyColorFilter(getProperBackgroundColor().getContrastColor())
        scheduled_message_button.apply {
            val clockDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_clock_vector, theme)?.apply { applyColorFilter(textColor) }
            setCompoundDrawablesWithIntrinsicBounds(clockDrawable, null, null, null)
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
            attachment = buildMessageAttachment(text, messageId),
            senderName = "",
            senderPhotoUri = "",
            subscriptionId = subscriptionId,
            isScheduled = true
        )
    }

    private fun buildMessageAttachment(text: String, messageId: Long): MessageAttachment {
        val attachments = attachmentSelections.values
            .map { Attachment(null, messageId, it.uri.toString(), contentResolver.getType(it.uri) ?: "*/*", 0, 0, "") }
            .toArrayList()

        return MessageAttachment(messageId, text, attachments)
    }
}
