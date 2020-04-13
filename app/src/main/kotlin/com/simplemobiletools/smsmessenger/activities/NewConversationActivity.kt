package com.simplemobiletools.smsmessenger.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.PERMISSION_READ_CONTACTS
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.ContactsAdapter
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.*
import com.simplemobiletools.smsmessenger.models.Contact
import kotlinx.android.synthetic.main.activity_conversation.*
import kotlinx.android.synthetic.main.item_suggested_contact.view.*

class NewConversationActivity : SimpleActivity() {
    private var allContacts = ArrayList<Contact>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation)
        title = getString(R.string.new_conversation)
        updateTextColors(new_conversation_holder)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        new_conversation_address.requestFocus()

        // READ_CONTACTS permission is not mandatory, but without it we won't be able to show any suggestions during typing
        handlePermission(PERMISSION_READ_CONTACTS) {
            initContacts()
        }
    }

    override fun onResume() {
        super.onResume()
        no_contacts_placeholder_2.setTextColor(getAdjustedPrimaryColor())
        no_contacts_placeholder_2.underlineText()
    }

    private fun initContacts() {
        if (isThirdPartyIntent()) {
            return
        }

        fetchContacts()
        new_conversation_address.onTextChangeListener {
            val searchString = it
            val filteredContacts = ArrayList<Contact>()
            allContacts.forEach {
                if (it.phoneNumber.contains(searchString, true) || it.name.contains(searchString, true)) {
                    filteredContacts.add(it)
                }
            }

            filteredContacts.sortWith(compareBy { !it.name.startsWith(searchString, true) })
            setupAdapter(filteredContacts)

            new_conversation_confirm.beVisibleIf(searchString.length > 2)
        }

        new_conversation_confirm.applyColorFilter(config.textColor)
        new_conversation_confirm.setOnClickListener {
            val number = new_conversation_address.value
            launchThreadActivity(number, number)
        }

        no_contacts_placeholder_2.setOnClickListener {
            handlePermission(PERMISSION_READ_CONTACTS) {
                if (it) {
                    fetchContacts()
                }
            }
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

    private fun fetchContacts() {
        fillSuggestedContacts {
            getAvailableContacts {
                allContacts = it
                runOnUiThread {
                    setupAdapter(allContacts)
                }
            }
        }
    }

    private fun setupAdapter(contacts: ArrayList<Contact>) {
        val hasContacts = contacts.isNotEmpty()
        contacts_list.beVisibleIf(hasContacts)
        no_contacts_placeholder.beVisibleIf(!hasContacts)
        no_contacts_placeholder_2.beVisibleIf(!hasContacts && !hasPermission(PERMISSION_READ_CONTACTS))

        if (!hasContacts) {
            val placeholderText = if (hasPermission(PERMISSION_READ_CONTACTS)) R.string.no_contacts_found else R.string.no_access_to_contacts
            no_contacts_placeholder.text = getString(placeholderText)
        }

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
                    suggestions_label.beVisible()
                    suggestions_scrollview.beVisible()
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

            if (intent.action == Intent.ACTION_SEND && intent.extras?.containsKey(Intent.EXTRA_STREAM) == true) {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                putExtra(THREAD_ATTACHMENT_URI, uri?.toString())
            } else if (intent.action == Intent.ACTION_SEND_MULTIPLE && intent.extras?.containsKey(Intent.EXTRA_STREAM) == true) {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                putExtra(THREAD_ATTACHMENT_URIS, uris)
            }

            startActivity(this)
        }
    }
}
