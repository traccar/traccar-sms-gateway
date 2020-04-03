package com.simplemobiletools.smsmessenger.activities

import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.Telephony
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.PERMISSION_READ_CONTACTS
import com.simplemobiletools.commons.helpers.PERMISSION_READ_SMS
import com.simplemobiletools.commons.models.FAQItem
import com.simplemobiletools.smsmessenger.BuildConfig
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.MessagesAdapter
import com.simplemobiletools.smsmessenger.models.Message
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : SimpleActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)

        if (checkAppSideloading()) {
            return
        }

        // while READ_SMS permission is mandatory, READ_CONTACTS is optional. If we don't have it, we just won't be able to show the contact name in some cases
        handlePermission(PERMISSION_READ_SMS) {
            if (it) {
                handlePermission(PERMISSION_READ_CONTACTS) {
                    initMessenger()
                }
            } else {
                finish()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings -> launchSettings()
            R.id.about -> launchAbout()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun initMessenger() {
        val messages = getMessages()
        MessagesAdapter(this, messages, messages_list, messages_fastscroller) {

        }.apply {
            messages_list.adapter = this
        }
    }

    private fun getMessages(): ArrayList<Message> {
        val messages = ArrayList<Message>()
        val hasContactsPermission = hasPermission(PERMISSION_READ_CONTACTS)
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.SUBJECT,
            Telephony.Sms.BODY,
            Telephony.Sms.TYPE,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.PERSON,
            Telephony.Sms.DATE,
            Telephony.Sms.READ
        )

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, null, null, null)
            if (cursor?.moveToFirst() == true) {
                do {
                    val id = cursor.getIntValue(Telephony.Sms._ID)
                    val subject = cursor.getStringValue(Telephony.Sms.SUBJECT) ?: ""
                    val body = cursor.getStringValue(Telephony.Sms.BODY)
                    val type = cursor.getIntValue(Telephony.Sms.TYPE)
                    var address = cursor.getStringValue(Telephony.Sms.ADDRESS)
                    val date = (cursor.getLongValue(Telephony.Sms.DATE) / 1000).toInt()
                    val read = cursor.getIntValue(Telephony.Sms.READ) == 1
                    val person = cursor.getIntValue(Telephony.Sms.PERSON)
                    if (address != null && person != 0 && hasContactsPermission) {
                        address = getPersonsName(person) ?: address
                    }

                    val message = Message(id, subject, body, type, address, date, read)
                    messages.add(message)
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            showErrorToast(e)
        } finally {
            cursor?.close()
        }
        return messages
    }

    private fun getPersonsName(id: Int): String? {
        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(
            CommonDataKinds.StructuredName.GIVEN_NAME,
            CommonDataKinds.StructuredName.MIDDLE_NAME,
            CommonDataKinds.StructuredName.FAMILY_NAME,
            CommonDataKinds.Organization.COMPANY,
            CommonDataKinds.Organization.TITLE,
            ContactsContract.Data.MIMETYPE
        )

        val selection =
            "(${ContactsContract.Data.MIMETYPE} = ? OR ${ContactsContract.Data.MIMETYPE} = ?) AND ${ContactsContract.Data.CONTACT_ID} = ?"
        val selectionArgs = arrayOf(
            CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
            id.toString()
        )

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor?.moveToFirst() == true) {
                do {
                    val mimetype = cursor.getStringValue(ContactsContract.Data.MIMETYPE)
                    val isPerson = mimetype == CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                    if (isPerson) {
                        val firstName = cursor.getStringValue(CommonDataKinds.StructuredName.GIVEN_NAME) ?: ""
                        val middleName = cursor.getStringValue(CommonDataKinds.StructuredName.MIDDLE_NAME) ?: ""
                        val familyName = cursor.getStringValue(CommonDataKinds.StructuredName.FAMILY_NAME) ?: ""
                        if (firstName.isNotEmpty() || middleName.isNotEmpty() || familyName.isNotEmpty()) {
                            val names = arrayOf(firstName, middleName, familyName).filter { it.isNotEmpty() }
                            return TextUtils.join(" ", names)
                        }
                    }

                    val isOrganization = mimetype == CommonDataKinds.Organization.CONTENT_ITEM_TYPE
                    if (isOrganization) {
                        val company = cursor.getStringValue(CommonDataKinds.Organization.COMPANY) ?: ""
                        val jobTitle = cursor.getStringValue(CommonDataKinds.Organization.TITLE) ?: ""
                        if (company.isNotEmpty() || jobTitle.isNotEmpty()) {
                            return "$company $jobTitle".trim()
                        }
                    }
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            showErrorToast(e)
        } finally {
            cursor?.close()
        }

        return null
    }

    private fun launchSettings() {
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = 0

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons),
            FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons)
        )

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }
}
