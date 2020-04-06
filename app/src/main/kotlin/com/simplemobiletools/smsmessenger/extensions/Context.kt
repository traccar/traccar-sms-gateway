package com.simplemobiletools.smsmessenger.extensions

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.Telephony
import android.text.TextUtils
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.PERMISSION_READ_CONTACTS
import com.simplemobiletools.commons.helpers.isMarshmallowPlus
import com.simplemobiletools.smsmessenger.helpers.Config
import com.simplemobiletools.smsmessenger.models.Contact
import com.simplemobiletools.smsmessenger.models.Message
import com.simplemobiletools.smsmessenger.models.MessagingThread

val Context.config: Config get() = Config.newInstance(applicationContext)

fun Context.getMessages(threadID: Int? = null): ArrayList<Message> {
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

    val selection = if (threadID == null) {
        "1 == 1) GROUP BY (${Telephony.Sms.THREAD_ID}"
    } else {
        "${Telephony.Sms.THREAD_ID} = ?"
    }

    val selectionArgs = if (threadID == null) {
        null
    } else {
        arrayOf(threadID.toString())
    }

    var cursor: Cursor? = null
    try {
        cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        if (cursor?.moveToFirst() == true) {
            do {
                val id = cursor.getIntValue(Telephony.Sms._ID)
                val subject = cursor.getStringValue(Telephony.Sms.SUBJECT) ?: ""
                val body = cursor.getStringValue(Telephony.Sms.BODY)
                val type = cursor.getIntValue(Telephony.Sms.TYPE)
                var senderName = cursor.getStringValue(Telephony.Sms.ADDRESS)
                val senderNumber = senderName
                val date = (cursor.getLongValue(Telephony.Sms.DATE) / 1000).toInt()
                val read = cursor.getIntValue(Telephony.Sms.READ) == 1
                val person = cursor.getIntValue(Telephony.Sms.PERSON)
                val thread = cursor.getIntValue(Telephony.Sms.THREAD_ID)

                if (hasContactsPermission) {
                    if (senderName != null && person != 0) {
                        senderName = getPersonsName(person) ?: senderName
                    } else if (senderName.areDigitsOnly()) {
                        val contactId = getNameFromPhoneNumber(senderName)
                        if (contactId != null) {
                            senderName = getPersonsName(contactId) ?: senderName
                        }
                    }
                }

                val message = Message(id, subject, body, type, senderName, senderNumber, date, read, thread)
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

fun Context.getThreadInfo(id: Int): MessagingThread? {
    val uri = Telephony.Sms.CONTENT_URI
    val projection = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.PERSON
    )
    val selection = "${Telephony.Sms.THREAD_ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    var cursor: Cursor? = null
    try {
        cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        if (cursor?.moveToFirst() == true) {
            val person = cursor.getIntValue(Telephony.Sms.PERSON)
            val address = cursor.getStringValue(Telephony.Sms.ADDRESS)
            var title = address

            if (title != null && person != 0) {
                title = getPersonsName(person) ?: title
            } else if (title.areDigitsOnly()) {
                val contactId = getNameFromPhoneNumber(title)
                if (contactId != null) {
                    title = getPersonsName(contactId) ?: title
                }
            }

            return MessagingThread(id, title, address)
        }
    } catch (e: Exception) {
    } finally {
        cursor?.close()
    }
    return null
}

fun Context.getPersonsName(id: Int): String? {
    val uri = ContactsContract.Data.CONTENT_URI
    val projection = arrayOf(
        CommonDataKinds.StructuredName.PREFIX,
        CommonDataKinds.StructuredName.GIVEN_NAME,
        CommonDataKinds.StructuredName.MIDDLE_NAME,
        CommonDataKinds.StructuredName.FAMILY_NAME,
        CommonDataKinds.StructuredName.SUFFIX,
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
                    val prefix = cursor.getStringValue(CommonDataKinds.StructuredName.PREFIX) ?: ""
                    val firstName = cursor.getStringValue(CommonDataKinds.StructuredName.GIVEN_NAME) ?: ""
                    val middleName = cursor.getStringValue(CommonDataKinds.StructuredName.MIDDLE_NAME) ?: ""
                    val familyName = cursor.getStringValue(CommonDataKinds.StructuredName.FAMILY_NAME) ?: ""
                    val suffix = cursor.getStringValue(CommonDataKinds.StructuredName.SUFFIX) ?: ""
                    if (firstName.isNotEmpty() || middleName.isNotEmpty() || familyName.isNotEmpty()) {
                        val names = arrayOf(prefix, firstName, middleName, familyName, suffix).filter { it.isNotEmpty() }
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

fun Context.getAvailableContacts(): ArrayList<Contact> {
    val names = getContactNames()
    var allContacts = getContactPhoneNumbers()
    allContacts.forEach {
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

    allContacts = allContacts.filter { it.name.isNotEmpty() }.distinctBy {
        val startIndex = Math.max(0, it.phoneNumber.length - 9)
        it.phoneNumber.substring(startIndex)
    }.toMutableList() as ArrayList<Contact>

    allContacts.sortBy { it.name.normalizeString().toLowerCase() }
    return allContacts
}

fun Context.getNameFromPhoneNumber(number: String): Int? {
    val uri = CommonDataKinds.Phone.CONTENT_URI
    val projection = arrayOf(
        ContactsContract.Data.CONTACT_ID
    )

    val selection = "${CommonDataKinds.Phone.NUMBER} = ? OR ${CommonDataKinds.Phone.NORMALIZED_NUMBER} = ?"
    val selectionArgs = arrayOf(number, number)

    var cursor: Cursor? = null
    try {
        cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        if (cursor?.moveToFirst() == true) {
            return cursor.getIntValue(ContactsContract.Data.CONTACT_ID)
        }
    } catch (e: Exception) {
        showErrorToast(e)
    } finally {
        cursor?.close()
    }

    return null
}

fun Context.getContactNames(): List<Contact> {
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

fun Context.getContactPhoneNumbers(): ArrayList<Contact> {
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

fun Context.insertNewSMS(address: String, subject: String, body: String, date: Long, read: Int, threadId: Long, type: Int) {
    val uri = Telephony.Sms.CONTENT_URI
    val contentValues = ContentValues().apply {
        put(Telephony.Sms.ADDRESS, address)
        put(Telephony.Sms.SUBJECT, subject)
        put(Telephony.Sms.BODY, body)
        put(Telephony.Sms.DATE, date)
        put(Telephony.Sms.READ, read)
        put(Telephony.Sms.THREAD_ID, threadId)
        put(Telephony.Sms.TYPE, type)
    }

    contentResolver.insert(uri, contentValues)
}

fun Context.deleteThread(id: Int) {
    val uri = Telephony.Sms.CONTENT_URI
    val selection = "${Telephony.Sms.THREAD_ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    contentResolver.delete(uri, selection, selectionArgs)
}

fun Context.deleteMessage(id: Int) {
    val uri = Telephony.Sms.CONTENT_URI
    val selection = "${Telephony.Sms._ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    contentResolver.delete(uri, selection, selectionArgs)
}

fun Context.markSMSRead(id: Int) {
    val uri = Telephony.Sms.CONTENT_URI
    val contentValues = ContentValues().apply {
        put(Telephony.Sms.READ, 1)
    }
    val selection = "${Telephony.Sms._ID} = ? AND ${Telephony.Sms.READ} = ?"
    val selectionArgs = arrayOf(id.toString(), "0")
    contentResolver.update(uri, contentValues, selection, selectionArgs)
}

@SuppressLint("NewApi")
fun Context.getThreadId(address: String): Long {
    return if (isMarshmallowPlus()) {
        Telephony.Threads.getOrCreateThreadId(this, address)
    } else {
        0
    }
}
