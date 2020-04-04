package com.simplemobiletools.smsmessenger.activities

import android.os.Bundle
import com.simplemobiletools.commons.extensions.applyColorFilter
import com.simplemobiletools.commons.extensions.onTextChangeListener
import com.simplemobiletools.commons.extensions.value
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.ThreadAdapter
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.extensions.getMessages
import com.simplemobiletools.smsmessenger.helpers.THREAD_ID
import com.simplemobiletools.smsmessenger.helpers.THREAD_NAME
import com.simplemobiletools.smsmessenger.helpers.THREAD_NUMBER
import com.simplemobiletools.smsmessenger.models.ThreadDateTime
import com.simplemobiletools.smsmessenger.models.ThreadItem
import kotlinx.android.synthetic.main.activity_thread.*

class ThreadActivity : SimpleActivity() {
    private val MIN_DATE_TIME_DIFF_SECS = 3600

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thread)
        title = intent.getStringExtra(THREAD_NAME) ?: getString(R.string.app_launcher_name)

        val threadID = intent.getIntExtra(THREAD_ID, 0)
        val targetNumber = intent.getStringExtra(THREAD_NUMBER)
        val items = getThreadItems(threadID)

        val adapter = ThreadAdapter(this, items, thread_messages_list, thread_messages_fastscroller) {}
        thread_messages_list.adapter = adapter
        setupButtons()
    }

    private fun setupButtons() {
        thread_type_message.setColors(config.textColor, config.primaryColor, config.backgroundColor)
        thread_send_message.applyColorFilter(config.textColor)

        thread_send_message.setOnClickListener {
            val msg = thread_type_message.value
            if (msg.isEmpty()) {
                return@setOnClickListener
            }
        }

        thread_send_message.isClickable = false
        thread_type_message.onTextChangeListener {
            thread_send_message.isClickable = it.isNotEmpty()
        }
    }

    private fun getThreadItems(threadID: Int): ArrayList<ThreadItem> {
        val messages = getMessages(threadID)
        messages.sortBy { it.id }

        val items = ArrayList<ThreadItem>()
        var prevDateTime = 0
        messages.forEach {
            // do not show the date/time above every message, only if the difference between the 2 messages is at least MIN_DATE_TIME_DIFF_SECS
            if (it.date - prevDateTime > MIN_DATE_TIME_DIFF_SECS) {
                items.add(ThreadDateTime(it.date))
                prevDateTime = it.date
            }
            items.add(it)
        }

        return items
    }
}
