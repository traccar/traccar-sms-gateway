package com.simplemobiletools.smsmessenger.activities

import android.os.Bundle
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.ThreadAdapter
import com.simplemobiletools.smsmessenger.extensions.getMessages
import com.simplemobiletools.smsmessenger.helpers.THREAD_ID
import com.simplemobiletools.smsmessenger.helpers.THREAD_NAME
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

        ThreadAdapter(this, items, thread_messages_list, thread_messages_fastscroller) {

        }.apply {
            thread_messages_list.adapter = this
        }
    }
}
