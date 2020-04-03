package com.simplemobiletools.smsmessenger.extensions

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.Telephony
import android.text.TextUtils
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.PERMISSION_READ_CONTACTS
import com.simplemobiletools.smsmessenger.helpers.Config
import com.simplemobiletools.smsmessenger.models.Message

val Context.config: Config get() = Config.newInstance(applicationContext)

fun Context.getMessages(): ArrayList<Message> {
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
        Telephony.Sms.READ,
        Telephony.Sms.THREAD_ID
    )

    val selection = "1 == 1) GROUP BY (${Telephony.Sms.THREAD_ID}"
    val selectionArgs = null

    var cursor: Cursor? = null
    try {
        cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
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
                val thread = cursor.getIntValue(Telephony.Sms.THREAD_ID)
                if (address != null && person != 0 && hasContactsPermission) {
                    address = getPersonsName(person) ?: address
                }

                val message = Message(id, subject, body, type, address, date, read, thread)
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

fun Context.getPersonsName(id: Int): String? {
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
