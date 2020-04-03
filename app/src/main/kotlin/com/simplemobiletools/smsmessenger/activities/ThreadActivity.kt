package com.simplemobiletools.smsmessenger.activities

import android.os.Bundle
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.ThreadAdapter
import com.simplemobiletools.smsmessenger.extensions.getMessages
import com.simplemobiletools.smsmessenger.helpers.THREAD_ID
import kotlinx.android.synthetic.main.activity_thread.*

class ThreadActivity : SimpleActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thread)
        val threadID = intent.getIntExtra(THREAD_ID, 0)
        val messages = getMessages(threadID)
        messages.sortBy { it.id }

        ThreadAdapter(this, messages, thread_messages_list, thread_messages_fastscroller) {

        }.apply {
            thread_messages_list.adapter = this
        }
    }
}
