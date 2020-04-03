package com.simplemobiletools.smsmessenger.activities

import android.os.Bundle
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.extensions.getMessages
import com.simplemobiletools.smsmessenger.helpers.THREAD_ID

class ThreadActivity : SimpleActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thread)
        val threadID = intent.getIntExtra(THREAD_ID, 0)
        val messages = getMessages(threadID)
    }
}
