package com.simplemobiletools.smsmessenger.activities

import android.os.Bundle
import android.view.WindowManager
import com.simplemobiletools.commons.extensions.updateTextColors
import com.simplemobiletools.smsmessenger.R
import kotlinx.android.synthetic.main.activity_new_message.*

class NewMessageActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_message)
        title = getString(R.string.create_new_message)
        updateTextColors(new_message_holder)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        new_message_to.requestFocus()
    }
}
