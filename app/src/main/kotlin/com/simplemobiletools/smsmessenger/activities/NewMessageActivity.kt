package com.simplemobiletools.smsmessenger.activities

import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.RelativeLayout
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.PERMISSION_READ_CONTACTS
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.ContactsAdapter
import com.simplemobiletools.smsmessenger.extensions.getThreadId
import com.simplemobiletools.smsmessenger.extensions.launchThreadActivity
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
            val contactId = it.id
            val contact = names.firstOrNull { it.id == contactId }
            val name = contact?.name
            if (name != null) {
                it.name = name
            }

            val photoUri = contact?.photoUri
            if (photoUri != null) {
                it.photoUri = photoUri
            }

            it.isOrganization = contact?.isOrganization ?: false
        }

        contacts = contacts.filter { it.name.isNotEmpty() }.distinctBy {
            val startIndex = Math.max(0, it.phoneNumber.length - 9)
            it.phoneNumber.substring(startIndex)
        }.toMutableList() as ArrayList<Contact>

        contacts.sortBy { it.name.normalizeString().toLowerCase() }

        ContactsAdapter(this, contacts, suggestions_list, null) {
            hideKeyboard()
            launchThreadActivity(getThreadId((it as Contact).phoneNumber).toInt())
        }.apply {
            suggestions_list.adapter = this
        }
    }

    private fun addSelectedContact(contact: Contact) {
        new_message_to.setText("")
        if (selectedContacts.map { it.id }.contains(contact.id)) {
            return
        }

        selectedContacts.add(contact)
        showSelectedContacts()
    }

    private fun showSelectedContacts() {
        selected_contacts.beVisibleIf(selectedContacts.isNotEmpty())
        message_divider_one.beVisibleIf(selectedContacts.isNotEmpty())

        val views = ArrayList<View>()
        selectedContacts.forEach {
            val contact = it
            layoutInflater.inflate(R.layout.item_selected_contact, null).apply {
                selected_contact_name.text = contact.name
                selected_contact_remove.setOnClickListener {
                    removeSelectedContact(contact.id)
                }
                views.add(this)
            }
        }

        showSelectedContact(views)
    }

    // show selected contacts, properly split to new lines when appropriate
    // based on https://stackoverflow.com/a/13505029/1967672
    private fun showSelectedContact(views: ArrayList<View>) {
        selected_contacts.removeAllViews()
        var newLinearLayout = LinearLayout(this)
        newLinearLayout.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        newLinearLayout.orientation = LinearLayout.HORIZONTAL

        val sideMargin = (selected_contacts.layoutParams as RelativeLayout.LayoutParams).leftMargin
        val parentWidth = realScreenSize.x - sideMargin * 2
        val mediumMargin = resources.getDimension(R.dimen.medium_margin).toInt()
        var widthSoFar = 0
        var isFirstRow = true
        for (i in views.indices) {
            val LL = LinearLayout(this)
            LL.orientation = LinearLayout.HORIZONTAL
            LL.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            LL.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            views[i].measure(0, 0)

            var params = LayoutParams(views[i].measuredWidth, LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, mediumMargin, 0)
            LL.addView(views[i], params)
            LL.measure(0, 0)
            widthSoFar += views[i].measuredWidth + mediumMargin

            if (widthSoFar >= parentWidth) {
                isFirstRow = false
                selected_contacts.addView(newLinearLayout)
                newLinearLayout = LinearLayout(this)
                newLinearLayout.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                newLinearLayout.orientation = LinearLayout.HORIZONTAL
                params = LayoutParams(LL.measuredWidth, LL.measuredHeight)
                params.topMargin = mediumMargin
                newLinearLayout.addView(LL, params)
                widthSoFar = LL.measuredWidth
            } else {
                if (!isFirstRow) {
                    (LL.layoutParams as LayoutParams).topMargin = mediumMargin
                }
                newLinearLayout.addView(LL)
            }
        }
        selected_contacts.addView(newLinearLayout)
    }

    private fun removeSelectedContact(id: Int) {
        selectedContacts = selectedContacts.filter { it.id != id }.toMutableList() as ArrayList<Contact>
        showSelectedContacts()
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
                            val contact = Contact(id, fullName, photoUri, "", false)
                            contacts.add(contact)
                        }
                    }

                    val isOrganization = mimetype == CommonDataKinds.Organization.CONTENT_ITEM_TYPE
                    if (isOrganization) {
                        val company = cursor.getStringValue(CommonDataKinds.Organization.COMPANY) ?: ""
                        val jobTitle = cursor.getStringValue(CommonDataKinds.Organization.TITLE) ?: ""
                        if (company.isNotEmpty() || jobTitle.isNotEmpty()) {
                            val fullName = "$company $jobTitle".trim()
                            val contact = Contact(id, fullName, photoUri, "", true)
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
                    val contact = Contact(id, "", "", phoneNumber, false)
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
