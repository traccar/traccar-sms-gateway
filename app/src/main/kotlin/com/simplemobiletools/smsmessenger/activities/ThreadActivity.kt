package com.simplemobiletools.smsmessenger.activities

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import com.simplemobiletools.commons.extensions.applyColorFilter
import com.simplemobiletools.commons.extensions.onTextChangeListener
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.extensions.value
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.ThreadAdapter
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.extensions.getMessages
import com.simplemobiletools.smsmessenger.extensions.getThreadInfo
import com.simplemobiletools.smsmessenger.extensions.markSMSRead
import com.simplemobiletools.smsmessenger.helpers.*
import com.simplemobiletools.smsmessenger.models.*
import com.simplemobiletools.smsmessenger.receivers.SmsSentReceiver
import kotlinx.android.synthetic.main.activity_thread.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class ThreadActivity : SimpleActivity() {
    private val MIN_DATE_TIME_DIFF_SECS = 300

    private var targetNumber = ""
    private var threadId = 0
    private var bus: EventBus? = null

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
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.add_person -> addPerson()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setupAdapter() {
        val threadId = intent.getIntExtra(THREAD_ID, 0)
        val items = getThreadItems(threadId)

        runOnUiThread {
            val adapter = ThreadAdapter(this, items, thread_messages_list, thread_messages_fastscroller) {}
            thread_messages_list.adapter = adapter
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

    private fun addPerson() {

    }

    private fun getThreadItems(threadID: Int): ArrayList<ThreadItem> {
        val messages = getMessages(threadID)
        messages.sortBy { it.id }

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

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshMessages(event: Events.RefreshMessages) {
        setupAdapter()
    }
}
