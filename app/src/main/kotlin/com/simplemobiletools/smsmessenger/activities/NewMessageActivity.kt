package com.simplemobiletools.smsmessenger.activities

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.PERMISSION_READ_CONTACTS
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.ContactsAdapter
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.extensions.getAvailableContacts
import com.simplemobiletools.smsmessenger.extensions.getThreadId
import com.simplemobiletools.smsmessenger.helpers.THREAD_ID
import com.simplemobiletools.smsmessenger.helpers.THREAD_NAME
import com.simplemobiletools.smsmessenger.helpers.THREAD_NUMBER
import com.simplemobiletools.smsmessenger.models.Contact
import kotlinx.android.synthetic.main.activity_new_message.*

class NewMessageActivity : SimpleActivity() {
    private var allContacts = ArrayList<Contact>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_message)
        title = getString(R.string.create_new_message)
        updateTextColors(new_message_holder)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        new_message_to.requestFocus()

        // READ_CONTACTS permission is not mandatory, but without it we won't be able to show any suggestions during typing
        handlePermission(PERMISSION_READ_CONTACTS) {
            initContacts()
        }
    }

    private fun initContacts() {
        getAvailableContacts {
            allContacts = it
            runOnUiThread {
                setupAdapter(allContacts)
            }
        }

        new_message_to.onTextChangeListener {
            val searchString = it
            val filteredContacts = ArrayList<Contact>()
            allContacts.forEach {
                if (it.phoneNumber.contains(searchString, true) || it.name.contains(searchString, true)) {
                    filteredContacts.add(it)
                }
            }

            filteredContacts.sortWith(compareBy { !it.name.startsWith(searchString, true) })
            setupAdapter(filteredContacts)

            new_message_confirm.beVisibleIf(searchString.length > 2)
        }

        new_message_confirm.applyColorFilter(config.textColor)
        new_message_confirm.setOnClickListener {
            val number = new_message_to.value
            launchThreadActivity(number, number)
        }
    }

    private fun setupAdapter(contacts: ArrayList<Contact>) {
        ContactsAdapter(this, contacts, suggestions_list, null) {
            hideKeyboard()
            launchThreadActivity((it as Contact).phoneNumber, it.name)
        }.apply {
            suggestions_list.adapter = this
        }
    }

    private fun launchThreadActivity(phoneNumber: String, name: String) {
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, getThreadId(phoneNumber).toInt())
            putExtra(THREAD_NAME, name)
            putExtra(THREAD_NUMBER, phoneNumber)
            startActivity(this)
        }
    }
}
