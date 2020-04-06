package com.simplemobiletools.smsmessenger.activities

import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.text.TextUtils
import android.view.WindowManager
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.PERMISSION_READ_CONTACTS
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.ContactsAdapter
import com.simplemobiletools.smsmessenger.extensions.getAvailableContacts
import com.simplemobiletools.smsmessenger.extensions.getContactNames
import com.simplemobiletools.smsmessenger.extensions.getContactPhoneNumbers
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
        allContacts = getAvailableContacts()
        setupAdapter(allContacts)

        new_message_to.onTextChangeListener {
            val searchString = it
            val filteredContacts = ArrayList<Contact>()
            allContacts.forEach {
                if (it.phoneNumber.contains(searchString, true) || it.name.contains(searchString, true)) {
                    filteredContacts.add(it)
                }
            }

            setupAdapter(filteredContacts)
        }
    }

    private fun setupAdapter(contacts: ArrayList<Contact>) {
        ContactsAdapter(this, contacts, suggestions_list, null) {
            hideKeyboard()
            Intent(this, ThreadActivity::class.java).apply {
                putExtra(THREAD_ID, getThreadId((it as Contact).phoneNumber).toInt())
                putExtra(THREAD_NAME, it.name)
                putExtra(THREAD_NUMBER, it.phoneNumber)
                startActivity(this)
            }
        }.apply {
            suggestions_list.adapter = this
        }
    }
}
