package com.simplemobiletools.smsmessenger.extensions

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.Telephony
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import android.text.TextUtils
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.PERMISSION_READ_CONTACTS
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isMarshmallowPlus
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.smsmessenger.helpers.Config
import com.simplemobiletools.smsmessenger.models.Contact
import com.simplemobiletools.smsmessenger.models.MMS
import com.simplemobiletools.smsmessenger.models.Message
import com.simplemobiletools.smsmessenger.models.MessagingThread
import java.util.*
import kotlin.collections.ArrayList

val Context.config: Config get() = Config.newInstance(applicationContext)

fun Context.getMessages(threadId: Int? = null): ArrayList<Message> {
    val hasContactsPermission = hasPermission(PERMISSION_READ_CONTACTS)
    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms._ID,
        Sms.SUBJECT,
        Sms.BODY,
        Sms.TYPE,
        Sms.ADDRESS,
        Sms.PERSON,
        Sms.DATE,
        Sms.READ,
        Sms.THREAD_ID
    )

    val selection = if (threadId == null) {
        "1 == 1) GROUP BY (${Sms.THREAD_ID}"
    } else {
        "${Sms.THREAD_ID} = ?"
    }

    val selectionArgs = if (threadId == null) {
        null
    } else {
        arrayOf(threadId.toString())
    }

    var messages = ArrayList<Message>()
    queryCursor(uri, projection, selection, selectionArgs, showErrors = true) { cursor ->
        val id = cursor.getIntValue(Sms._ID)
        val subject = cursor.getStringValue(Sms.SUBJECT) ?: ""
        val body = cursor.getStringValue(Sms.BODY)
        val type = cursor.getIntValue(Sms.TYPE)
        var senderName = cursor.getStringValue(Sms.ADDRESS)
        val senderNumber = senderName
        val date = (cursor.getLongValue(Sms.DATE) / 1000).toInt()
        val read = cursor.getIntValue(Sms.READ) == 1
        val person = cursor.getIntValue(Sms.PERSON)
        val thread = cursor.getIntValue(Sms.THREAD_ID)

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
    }

    messages.addAll(getMMS(threadId))
    messages = messages.sortedByDescending { it.date }.toMutableList() as ArrayList<Message>
    if (threadId == null) {
        messages = messages.distinctBy { it.thread }.toMutableList() as ArrayList<Message>
    }

    messages = messages.filter { !isNumberBlocked(it.senderNumber) }.toMutableList() as ArrayList<Message>
    return messages
}

fun Context.getMMS(threadId: Int? = null): ArrayList<Message> {
    val hasContactsPermission = hasPermission(PERMISSION_READ_CONTACTS)
    val uri = Mms.CONTENT_URI
    val projection = arrayOf(
        Mms._ID,
        Mms.DATE,
        Mms.READ,
        Mms.SUBJECT,
        Mms.MESSAGE_BOX,
        Mms.THREAD_ID
    )

    val selection = if (threadId == null) {
        null
    } else {
        "${Mms.THREAD_ID} = ?"
    }

    val selectionArgs = if (threadId == null) {
        null
    } else {
        arrayOf(threadId.toString())
    }

    val messages = ArrayList<Message>()
    queryCursor(uri, projection, selection, selectionArgs, showErrors = true) { cursor ->
        val id = cursor.getIntValue(Mms._ID)
        val subject = cursor.getStringValue(Mms.SUBJECT) ?: ""
        val type = cursor.getIntValue(Mms.MESSAGE_BOX)
        val date = cursor.getLongValue(Mms.DATE).toInt()
        val read = cursor.getIntValue(Mms.READ) == 1
        val thread = cursor.getIntValue(Mms.THREAD_ID)
        var senderName = getMmsAddress(id)
        val senderNumber = senderName
        val mms = getMmsContent(id)

        if (hasContactsPermission) {
            val contactId = getNameFromPhoneNumber(senderName)
            if (contactId != null) {
                senderName = getPersonsName(contactId) ?: senderName
            }
        }

        val message = Message(id, subject, mms?.text ?: "", type, senderName, senderNumber, date, read, thread)
        messages.add(message)
    }
    return messages
}

// based on https://stackoverflow.com/a/6446831/1967672
@SuppressLint("NewApi")
fun Context.getMmsContent(id: Int): MMS? {
    val uri = if (isQPlus()) {
        Mms.Part.CONTENT_URI
    } else {
        Uri.parse("content://mms/part")
    }

    val projection = arrayOf(
        Mms._ID,
        Mms.Part.CONTENT_TYPE,
        Mms.Part.TEXT
    )
    val selection = "${Mms.Part.MSG_ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    val mms = MMS(id, "", null)

    queryCursor(uri, projection, selection, selectionArgs, showErrors = true) { cursor ->
        val partId = cursor.getStringValue(Mms._ID)
        val type = cursor.getStringValue(Mms.Part.CONTENT_TYPE)
        if (type == "text/plain") {
            mms.text = cursor.getStringValue(Mms.Part.TEXT) ?: ""
        } else if (type.startsWith("image/")) {
            mms.image = getMmsImage(uri, partId)
        }
    }

    return mms
}

fun Context.getMmsAddress(id: Int): String {
    val addressUri = Uri.withAppendedPath(Mms.CONTENT_URI, "$id/addr")
    val projection = arrayOf(
        Mms.Addr.ADDRESS
    )
    val selection = "${Mms.Addr.MSG_ID} = ?"
    val selectionArgs = arrayOf(id.toString())

    try {
        val cursor = contentResolver.query(addressUri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getStringValue(Mms.Addr.ADDRESS)
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }
    return ""
}

private fun Context.getMmsImage(uri: Uri, id: String): Bitmap? {
    val partURI = Uri.withAppendedPath(uri, id)
    var bitmap: Bitmap? = null
    try {
        contentResolver.openInputStream(partURI).use {
            bitmap = BitmapFactory.decodeStream(it)
        }
    } catch (ignored: Exception) {
    }
    return bitmap
}

fun Context.getThreadInfo(id: Int): MessagingThread? {
    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms._ID,
        Sms.ADDRESS,
        Sms.PERSON
    )
    val selection = "${Sms.THREAD_ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                val person = cursor.getIntValue(Sms.PERSON)
                val address = cursor.getStringValue(Sms.ADDRESS)
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
        }
    } catch (e: Exception) {
    }
    return null
}

fun Context.getPersonsName(id: Int): String? {
    val uri = ContactsContract.Data.CONTENT_URI
    val projection = arrayOf(
        StructuredName.PREFIX,
        StructuredName.GIVEN_NAME,
        StructuredName.MIDDLE_NAME,
        StructuredName.FAMILY_NAME,
        StructuredName.SUFFIX,
        Organization.COMPANY,
        Organization.TITLE,
        ContactsContract.Data.MIMETYPE
    )

    val selection =
        "(${ContactsContract.Data.MIMETYPE} = ? OR ${ContactsContract.Data.MIMETYPE} = ?) AND ${ContactsContract.Data.CONTACT_ID} = ?"
    val selectionArgs = arrayOf(
        StructuredName.CONTENT_ITEM_TYPE,
        Organization.CONTENT_ITEM_TYPE,
        id.toString()
    )

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                do {
                    val mimetype = cursor.getStringValue(ContactsContract.Data.MIMETYPE)
                    val isPerson = mimetype == StructuredName.CONTENT_ITEM_TYPE
                    if (isPerson) {
                        val prefix = cursor.getStringValue(StructuredName.PREFIX) ?: ""
                        val firstName = cursor.getStringValue(StructuredName.GIVEN_NAME) ?: ""
                        val middleName = cursor.getStringValue(StructuredName.MIDDLE_NAME) ?: ""
                        val familyName = cursor.getStringValue(StructuredName.FAMILY_NAME) ?: ""
                        val suffix = cursor.getStringValue(StructuredName.SUFFIX) ?: ""
                        if (firstName.isNotEmpty() || middleName.isNotEmpty() || familyName.isNotEmpty()) {
                            val names = arrayOf(prefix, firstName, middleName, familyName, suffix).filter { it.isNotEmpty() }
                            return TextUtils.join(" ", names)
                        }
                    }

                    val isOrganization = mimetype == Organization.CONTENT_ITEM_TYPE
                    if (isOrganization) {
                        val company = cursor.getStringValue(Organization.COMPANY) ?: ""
                        val jobTitle = cursor.getStringValue(Organization.TITLE) ?: ""
                        if (company.isNotEmpty() || jobTitle.isNotEmpty()) {
                            return "$company $jobTitle".trim()
                        }
                    }
                } while (cursor.moveToNext())
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }

    return null
}

fun Context.getAvailableContacts(callback: (ArrayList<Contact>) -> Unit) {
    ensureBackgroundThread {
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

        allContacts.sortBy { it.name.normalizeString().toLowerCase(Locale.getDefault()) }
        callback(allContacts)
    }
}

fun Context.getNameFromPhoneNumber(number: String): Int? {
    val uri = CommonDataKinds.Phone.CONTENT_URI
    val projection = arrayOf(
        ContactsContract.Data.CONTACT_ID
    )

    val selection = "${CommonDataKinds.Phone.NUMBER} = ? OR ${CommonDataKinds.Phone.NORMALIZED_NUMBER} = ?"
    val selectionArgs = arrayOf(number, number)
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor.use {
            if (cursor?.moveToFirst() == true) {
                return cursor.getIntValue(ContactsContract.Data.CONTACT_ID)
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }
    return null
}

fun Context.getContactNames(): List<Contact> {
    val contacts = ArrayList<Contact>()
    val uri = ContactsContract.Data.CONTENT_URI
    val projection = arrayOf(
        ContactsContract.Data.CONTACT_ID,
        StructuredName.PREFIX,
        StructuredName.GIVEN_NAME,
        StructuredName.MIDDLE_NAME,
        StructuredName.FAMILY_NAME,
        StructuredName.SUFFIX,
        StructuredName.PHOTO_THUMBNAIL_URI,
        Organization.COMPANY,
        Organization.TITLE,
        ContactsContract.Data.MIMETYPE
    )

    val selection = "${ContactsContract.Data.MIMETYPE} = ? OR ${ContactsContract.Data.MIMETYPE} = ?"
    val selectionArgs = arrayOf(
        StructuredName.CONTENT_ITEM_TYPE,
        Organization.CONTENT_ITEM_TYPE
    )

    queryCursor(uri, projection, selection, selectionArgs) { cursor ->
        val id = cursor.getIntValue(ContactsContract.Data.CONTACT_ID)
        val mimetype = cursor.getStringValue(ContactsContract.Data.MIMETYPE)
        val photoUri = cursor.getStringValue(StructuredName.PHOTO_THUMBNAIL_URI) ?: ""
        val isPerson = mimetype == StructuredName.CONTENT_ITEM_TYPE
        if (isPerson) {
            val prefix = cursor.getStringValue(StructuredName.PREFIX) ?: ""
            val firstName = cursor.getStringValue(StructuredName.GIVEN_NAME) ?: ""
            val middleName = cursor.getStringValue(StructuredName.MIDDLE_NAME) ?: ""
            val familyName = cursor.getStringValue(StructuredName.FAMILY_NAME) ?: ""
            val suffix = cursor.getStringValue(StructuredName.SUFFIX) ?: ""
            if (firstName.isNotEmpty() || middleName.isNotEmpty() || familyName.isNotEmpty()) {
                val names = arrayOf(prefix, firstName, middleName, familyName, suffix).filter { it.isNotEmpty() }
                val fullName = TextUtils.join(" ", names)
                val contact = Contact(id, fullName, photoUri, "", false)
                contacts.add(contact)
            }
        }

        val isOrganization = mimetype == Organization.CONTENT_ITEM_TYPE
        if (isOrganization) {
            val company = cursor.getStringValue(Organization.COMPANY) ?: ""
            val jobTitle = cursor.getStringValue(Organization.TITLE) ?: ""
            if (company.isNotEmpty() || jobTitle.isNotEmpty()) {
                val fullName = "$company $jobTitle".trim()
                val contact = Contact(id, fullName, photoUri, "", true)
                contacts.add(contact)
            }
        }
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

    queryCursor(uri, projection) { cursor ->
        val id = cursor.getIntValue(ContactsContract.Data.CONTACT_ID)
        val phoneNumber = cursor.getStringValue(CommonDataKinds.Phone.NORMALIZED_NUMBER)
        if (phoneNumber != null) {
            val contact = Contact(id, "", "", phoneNumber, false)
            contacts.add(contact)
        }
    }
    return contacts
}

fun Context.insertNewSMS(address: String, subject: String, body: String, date: Long, read: Int, threadId: Long, type: Int) {
    val uri = Sms.CONTENT_URI
    val contentValues = ContentValues().apply {
        put(Sms.ADDRESS, address)
        put(Sms.SUBJECT, subject)
        put(Sms.BODY, body)
        put(Sms.DATE, date)
        put(Sms.READ, read)
        put(Sms.THREAD_ID, threadId)
        put(Sms.TYPE, type)
    }

    contentResolver.insert(uri, contentValues)
}

fun Context.deleteThread(id: Int) {
    val uri = Sms.CONTENT_URI
    val selection = "${Sms.THREAD_ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    contentResolver.delete(uri, selection, selectionArgs)
}

fun Context.deleteMessage(id: Int) {
    val uri = Sms.CONTENT_URI
    val selection = "${Sms._ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    contentResolver.delete(uri, selection, selectionArgs)
}

fun Context.markSMSRead(id: Int) {
    val uri = Sms.CONTENT_URI
    val contentValues = ContentValues().apply {
        put(Sms.READ, 1)
    }
    val selection = "${Sms._ID} = ? AND ${Sms.READ} = ?"
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

@SuppressLint("NewApi")
fun Context.getThreadId(addresses: Set<String>): Long {
    return if (isMarshmallowPlus()) {
        Telephony.Threads.getOrCreateThreadId(this, addresses)
    } else {
        0
    }
}

fun Context.isNumberBlocked(number: String): Boolean {
    val blockedNumbers = getBlockedNumbers()
    val numberToCompare = number.trimToComparableNumber()
    return blockedNumbers.map { it.numberToCompare }.contains(numberToCompare)
}
