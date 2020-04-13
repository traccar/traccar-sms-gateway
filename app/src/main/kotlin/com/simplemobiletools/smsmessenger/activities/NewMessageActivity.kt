package com.simplemobiletools.smsmessenger.activities

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.PERMISSION_READ_CONTACTS
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.ContactsAdapter
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.THREAD_ID
import com.simplemobiletools.smsmessenger.helpers.THREAD_TEXT
import com.simplemobiletools.smsmessenger.helpers.THREAD_TITLE
import com.simplemobiletools.smsmessenger.models.Contact
import kotlinx.android.synthetic.main.activity_new_message.*
import kotlinx.android.synthetic.main.item_suggested_contact.view.*

class NewMessageActivity : SimpleActivity() {
    private var allContacts = ArrayList<Contact>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_message)
        title = getString(R.string.new_conversation)
        updateTextColors(new_message_holder)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        new_message_to.requestFocus()

        // READ_CONTACTS permission is not mandatory, but without it we won't be able to show any suggestions during typing
        handlePermission(PERMISSION_READ_CONTACTS) {
            initContacts()
        }
    }

    private fun initContacts() {
        if (isThirdPartyIntent()) {
            return
        }

        fillSuggestedContacts {
            getAvailableContacts {
                allContacts = it
                runOnUiThread {
                    setupAdapter(allContacts)
                }
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

    private fun isThirdPartyIntent(): Boolean {
        if (intent.action == Intent.ACTION_SENDTO && intent.dataString != null) {
            val number = intent.dataString!!.removePrefix("sms:").removePrefix("smsto:").removePrefix("mms").removePrefix("mmsto:").trim()
            launchThreadActivity(number, "")
            return true
        }
        return false
    }

    private fun setupAdapter(contacts: ArrayList<Contact>) {
        ContactsAdapter(this, contacts, contacts_list, null) {
            hideKeyboard()
            launchThreadActivity((it as Contact).phoneNumber, it.name)
        }.apply {
            contacts_list.adapter = this
        }
    }

    private fun fillSuggestedContacts(callback: () -> Unit) {
        ensureBackgroundThread {
            val suggestions = getSuggestedContacts()
            runOnUiThread {
                suggestions_holder.removeAllViews()
                if (suggestions.isEmpty()) {
                    suggestions_label.beGone()
                    suggestions_scrollview.beGone()
                } else {
                    suggestions.forEach {
                        val contact = it
                        layoutInflater.inflate(R.layout.item_suggested_contact, null).apply {
                            suggested_contact_name.text = contact.name
                            loadImage(contact.photoUri, suggested_contact_image, contact.name)
                            suggestions_holder.addView(this)
                            setOnClickListener {
                                launchThreadActivity(contact.phoneNumber, contact.name)
                            }
                        }
                    }
                }
                callback()
            }
        }
    }

    private fun launchThreadActivity(phoneNumber: String, name: String) {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, getThreadId(phoneNumber).toInt())
            putExtra(THREAD_TITLE, name)
            putExtra(THREAD_TEXT, text)
            startActivity(this)
        }
    }
}
