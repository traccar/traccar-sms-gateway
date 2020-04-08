package com.simplemobiletools.smsmessenger.activities

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import android.text.TextUtils
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.RelativeLayout
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.AutoCompleteTextViewAdapter
import com.simplemobiletools.smsmessenger.adapters.ThreadAdapter
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.*
import com.simplemobiletools.smsmessenger.models.*
import com.simplemobiletools.smsmessenger.receivers.SmsSentReceiver
import kotlinx.android.synthetic.main.activity_thread.*
import kotlinx.android.synthetic.main.item_selected_contact.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class ThreadActivity : SimpleActivity() {
    private val MIN_DATE_TIME_DIFF_SECS = 300

    private var targetNumber = ""
    private var threadId = 0
    private var threadItems = ArrayList<ThreadItem>()
    private var bus: EventBus? = null
    private var selectedContacts = ArrayList<Contact>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thread)

        val extras = intent.extras
        if (extras == null) {
            toast(R.string.unknown_error_occurred)
            finish()
            return
        }

        threadId = intent.getIntExtra(THREAD_ID, 0)
        var thread = getThreadInfo(threadId)
        if (thread == null) {
            if (extras.containsKey(THREAD_NUMBER)) {
                val threadTitle = extras.getString(THREAD_NAME) ?: getString(R.string.app_launcher_name)
                targetNumber = extras.getString(THREAD_NUMBER)!!
                thread = MessagingThread(threadId, threadTitle, targetNumber)
            } else {
                toast(R.string.unknown_error_occurred)
                finish()
                return
            }
        }

        title = thread.title
        targetNumber = thread.address
        bus = EventBus.getDefault()
        bus!!.register(this)
        val contact = Contact(0, thread.title, "", targetNumber, false)
        selectedContacts.add(contact)

        ensureBackgroundThread {
            setupAdapter()
        }
        setupButtons()
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_thread, menu)
        menu.apply {
            findItem(R.id.manage_people).isVisible = false
            findItem(R.id.delete).isVisible = threadItems.isNotEmpty()
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.block_number -> blockNumber()
            R.id.delete -> askConfirmDelete()
            R.id.manage_people -> managePeople()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setupAdapter() {
        val threadId = intent.getIntExtra(THREAD_ID, 0)
        threadItems = getThreadItems(threadId)
        invalidateOptionsMenu()

        runOnUiThread {
            val adapter = ThreadAdapter(this, threadItems, thread_messages_list, thread_messages_fastscroller) {}
            thread_messages_list.adapter = adapter
        }

        getAvailableContacts {
            runOnUiThread {
                val adapter = AutoCompleteTextViewAdapter(this, it)
                new_message_to.setAdapter(adapter)
                new_message_to.imeOptions = EditorInfo.IME_ACTION_NEXT
                new_message_to.setOnItemClickListener { parent, view, position, id ->
                    val currContacts = (new_message_to.adapter as AutoCompleteTextViewAdapter).resultList
                    val selectedContact = currContacts[position]
                    addSelectedContact(selectedContact)
                }
            }
        }
    }

    private fun setupButtons() {
        thread_type_message.setColors(config.textColor, config.primaryColor, config.backgroundColor)
        thread_send_message.applyColorFilter(config.textColor)

        thread_send_message.setOnClickListener {
            val msg = thread_type_message.value
            if (msg.isEmpty()) {
                return@setOnClickListener
            }

            val intent = Intent(this, SmsSentReceiver::class.java).apply {
                putExtra(MESSAGE_BODY, msg)
                putExtra(MESSAGE_ADDRESS, targetNumber)
            }

            val pendingIntent = PendingIntent.getBroadcast(this, threadId, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(targetNumber, null, msg, pendingIntent, null)
            thread_type_message.setText("")
        }

        thread_send_message.isClickable = false
        thread_type_message.onTextChangeListener {
            thread_send_message.isClickable = it.isNotEmpty()
            thread_send_message.alpha = if (it.isEmpty()) 0.4f else 0.9f
        }
    }

    private fun blockNumber() {
        val baseString = R.string.block_confirmation
        val numbers = selectedContacts.map { it.phoneNumber }.toTypedArray()
        val numbersString = TextUtils.join(", ", numbers)
        val question = String.format(resources.getString(baseString), numbersString)

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
            deleteThread(threadId)
            refreshMessages()
            finish()
        }
    }

    private fun managePeople() {
        if (thread_add_contacts.isVisible()) {
            hideKeyboard()
            thread_add_contacts.beGone()
        } else {
            showSelectedContacts()
            thread_add_contacts.beVisible()
            new_message_to.requestFocus()
            showKeyboard(new_message_to)
        }
    }

    private fun showSelectedContacts() {
        val views = ArrayList<View>()
        selectedContacts.forEach {
            val contact = it
            layoutInflater.inflate(R.layout.item_selected_contact, null).apply {
                selected_contact_name.text = contact.name
                selected_contact_remove.setOnClickListener {
                    if (contact.id != selectedContacts.first().id) {
                        removeSelectedContact(contact.id)
                    }
                }
                views.add(this)
            }
        }
        showSelectedContact(views)
    }

    private fun addSelectedContact(contact: Contact) {
        new_message_to.setText("")
        if (selectedContacts.map { it.id }.contains(contact.id)) {
            return
        }

        selectedContacts.add(contact)
        showSelectedContacts()
    }

    private fun getThreadItems(threadID: Int): ArrayList<ThreadItem> {
        val messages = getMessages(threadID)
        messages.sortBy { it.date }

        val items = ArrayList<ThreadItem>()
        var prevDateTime = 0
        var hadUnreadItems = false
        messages.forEach {
            // do not show the date/time above every message, only if the difference between the 2 messages is at least MIN_DATE_TIME_DIFF_SECS
            if (it.date - prevDateTime > MIN_DATE_TIME_DIFF_SECS) {
                items.add(ThreadDateTime(it.date))
                prevDateTime = it.date
            }
            items.add(it)

            if (it.type == Telephony.Sms.MESSAGE_TYPE_FAILED) {
                items.add(ThreadError(it.id))
            }

            if (!it.read) {
                hadUnreadItems = true
                markSMSRead(it.id)
            }
        }

        if (hadUnreadItems) {
            bus?.post(Events.RefreshMessages())
        }

        return items
    }

    // show selected contacts, properly split to new lines when appropriate
    // based on https://stackoverflow.com/a/13505029/1967672
    private fun showSelectedContact(views: ArrayList<View>) {
        selected_contacts.removeAllViews()
        var newLinearLayout = LinearLayout(this)
        newLinearLayout.layoutParams =
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        newLinearLayout.orientation = LinearLayout.HORIZONTAL

        val sideMargin = (selected_contacts.layoutParams as RelativeLayout.LayoutParams).leftMargin
        val parentWidth = realScreenSize.x - sideMargin * 2
        val mediumMargin = resources.getDimension(R.dimen.medium_margin).toInt()
        var widthSoFar = 0
        var isFirstRow = true
        for (i in views.indices) {
            val LL = LinearLayout(this)
            LL.orientation = LinearLayout.HORIZONTAL
            LL.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            LL.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            views[i].measure(0, 0)

            var params = LinearLayout.LayoutParams(views[i].measuredWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, mediumMargin, 0)
            LL.addView(views[i], params)
            LL.measure(0, 0)
            widthSoFar += views[i].measuredWidth + mediumMargin

            if (widthSoFar >= parentWidth) {
                isFirstRow = false
                selected_contacts.addView(newLinearLayout)
                newLinearLayout = LinearLayout(this)
                newLinearLayout.layoutParams =
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                newLinearLayout.orientation = LinearLayout.HORIZONTAL
                params = LinearLayout.LayoutParams(LL.measuredWidth, LL.measuredHeight)
                params.topMargin = mediumMargin
                newLinearLayout.addView(LL, params)
                widthSoFar = LL.measuredWidth
            } else {
                if (!isFirstRow) {
                    (LL.layoutParams as LinearLayout.LayoutParams).topMargin = mediumMargin
                }
                newLinearLayout.addView(LL)
            }
        }
        selected_contacts.addView(newLinearLayout)
    }

    private fun removeSelectedContact(id: Int) {
        selectedContacts = selectedContacts.filter { it.id != id }.toMutableList() as ArrayList<Contact>
        showSelectedContacts()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshMessages(event: Events.RefreshMessages) {
        setupAdapter()
    }
}
