package com.simplemobiletools.smsmessenger.activities

import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.text.TextUtils
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import com.simplemobiletools.commons.extensions.beVisible
import com.simplemobiletools.commons.extensions.getIntValue
import com.simplemobiletools.commons.extensions.getStringValue
import com.simplemobiletools.commons.extensions.updateTextColors
import com.simplemobiletools.commons.helpers.PERMISSION_READ_CONTACTS
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.AutoCompleteTextViewAdapter
import com.simplemobiletools.smsmessenger.models.Contact
import kotlinx.android.synthetic.main.activity_new_message.*
import kotlinx.android.synthetic.main.item_selected_contact.view.*

class NewMessageActivity : SimpleActivity() {
    private var contacts = ArrayList<Contact>()
    private var selectedContacts = ArrayList<Contact>()

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
        val names = getNames()
        contacts = getPhoneNumbers()
        contacts.forEach {
            val contactId = it.contactId
            val contact = names.firstOrNull { it.contactId == contactId }
            val name = contact?.name
            if (name != null) {
                it.name = name
            }

            val photoUri = contact?.photoUri
            if (photoUri != null) {
                it.photoUri = photoUri
            }
        }

        contacts = contacts.distinctBy {
            val startIndex = Math.max(0, it.phoneNumber.length - 9)
            it.phoneNumber.substring(startIndex)
        }.toMutableList() as ArrayList<Contact>

        val adapter = AutoCompleteTextViewAdapter(this, contacts)
        new_message_to.setAdapter(adapter)
        new_message_to.imeOptions = EditorInfo.IME_ACTION_NEXT
        new_message_to.setOnItemClickListener { parent, view, position, id ->
            val currContacts = (new_message_to.adapter as AutoCompleteTextViewAdapter).resultList
            val selectedContact = currContacts[position]
            addSelectedContact(selectedContact)
        }
    }

    private fun addSelectedContact(contact: Contact) {
        selectedContacts.add(contact)
        selected_contacts.beVisible()
        layoutInflater.inflate(R.layout.item_selected_contact, null).apply {
            selected_contact_name.text = contact.name
            selected_contacts.addView(this)
        }
    }

    private fun getNames(): List<Contact> {
        val contacts = ArrayList<Contact>()
        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            CommonDataKinds.StructuredName.PREFIX,
            CommonDataKinds.StructuredName.GIVEN_NAME,
            CommonDataKinds.StructuredName.MIDDLE_NAME,
            CommonDataKinds.StructuredName.FAMILY_NAME,
            CommonDataKinds.StructuredName.SUFFIX,
            CommonDataKinds.StructuredName.PHOTO_THUMBNAIL_URI,
            CommonDataKinds.Organization.COMPANY,
            CommonDataKinds.Organization.TITLE,
            ContactsContract.Data.MIMETYPE
        )

        val selection = "${ContactsContract.Data.MIMETYPE} = ? OR ${ContactsContract.Data.MIMETYPE} = ?"
        val selectionArgs = arrayOf(
            CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            CommonDataKinds.Organization.CONTENT_ITEM_TYPE
        )

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor?.moveToFirst() == true) {
                do {
                    val id = cursor.getIntValue(ContactsContract.Data.CONTACT_ID)
                    val mimetype = cursor.getStringValue(ContactsContract.Data.MIMETYPE)
                    val photoUri = cursor.getStringValue(CommonDataKinds.StructuredName.PHOTO_THUMBNAIL_URI) ?: ""
                    val isPerson = mimetype == CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                    if (isPerson) {
                        val prefix = cursor.getStringValue(CommonDataKinds.StructuredName.PREFIX) ?: ""
                        val firstName = cursor.getStringValue(CommonDataKinds.StructuredName.GIVEN_NAME) ?: ""
                        val middleName = cursor.getStringValue(CommonDataKinds.StructuredName.MIDDLE_NAME) ?: ""
                        val familyName = cursor.getStringValue(CommonDataKinds.StructuredName.FAMILY_NAME) ?: ""
                        val suffix = cursor.getStringValue(CommonDataKinds.StructuredName.SUFFIX) ?: ""
                        if (firstName.isNotEmpty() || middleName.isNotEmpty() || familyName.isNotEmpty()) {
                            val names = arrayOf(prefix, firstName, middleName, familyName, suffix).filter { it.isNotEmpty() }
                            val fullName = TextUtils.join(" ", names)
                            val contact = Contact(id, fullName, photoUri, "")
                            contacts.add(contact)
                        }
                    }

                    val isOrganization = mimetype == CommonDataKinds.Organization.CONTENT_ITEM_TYPE
                    if (isOrganization) {
                        val company = cursor.getStringValue(CommonDataKinds.Organization.COMPANY) ?: ""
                        val jobTitle = cursor.getStringValue(CommonDataKinds.Organization.TITLE) ?: ""
                        if (company.isNotEmpty() || jobTitle.isNotEmpty()) {
                            val fullName = "$company $jobTitle".trim()
                            val contact = Contact(id, fullName, photoUri, "")
                            contacts.add(contact)
                        }
                    }
                } while (cursor.moveToNext())
            }
        } catch (ignored: Exception) {
        } finally {
            cursor?.close()
        }
        return contacts
    }

    private fun getPhoneNumbers(): ArrayList<Contact> {
        val contacts = ArrayList<Contact>()
        val uri = CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            CommonDataKinds.Phone.NORMALIZED_NUMBER
        )

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, null, null, null)
            if (cursor?.moveToFirst() == true) {
                do {
                    val id = cursor.getIntValue(ContactsContract.Data.CONTACT_ID)
                    val phoneNumber = cursor.getStringValue(CommonDataKinds.Phone.NORMALIZED_NUMBER) ?: continue
                    val contact = Contact(id, "", "", phoneNumber)
                    contacts.add(contact)
                } while (cursor.moveToNext())
            }
        } catch (ignored: Exception) {
        } finally {
            cursor?.close()
        }
        return contacts
    }
}
