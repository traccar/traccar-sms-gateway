package com.simplemobiletools.smsmessenger.extensions

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.PhoneLookup
import android.provider.Telephony.*
import android.text.TextUtils
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.PERMISSION_READ_CONTACTS
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isMarshmallowPlus
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.smsmessenger.helpers.Config
import com.simplemobiletools.smsmessenger.models.Contact
import com.simplemobiletools.smsmessenger.models.Message
import com.simplemobiletools.smsmessenger.models.MessageAttachment
import java.util.*
import kotlin.collections.ArrayList

val Context.config: Config get() = Config.newInstance(applicationContext)

fun Context.getMessages(threadId: Int? = null): ArrayList<Message> {
    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms._ID,
        Sms.BODY,
        Sms.TYPE,
        Sms.ADDRESS,
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
        val body = cursor.getStringValue(Sms.BODY)
        val type = cursor.getIntValue(Sms.TYPE)
        val senderNumber = cursor.getStringValue(Sms.ADDRESS)
        val senderName = getNameFromPhoneNumber(senderNumber)
        val date = (cursor.getLongValue(Sms.DATE) / 1000).toInt()
        val read = cursor.getIntValue(Sms.READ) == 1
        val thread = cursor.getIntValue(Sms.THREAD_ID)
        val participant = Contact(0, senderName, "", senderNumber, false)
        val message = Message(id, body, type, arrayListOf(participant), date, read, thread, null)
        messages.add(message)
    }

    messages.addAll(getMMS(threadId))
    messages = messages.sortedByDescending { it.date }.toMutableList() as ArrayList<Message>
    if (threadId == null) {
        messages = messages.distinctBy { it.thread }.toMutableList() as ArrayList<Message>
    }

    messages = messages.filter { !isNumberBlocked(it.participants.first().phoneNumber) }.toMutableList() as ArrayList<Message>
    return messages
}

// as soon as a message contains multiple recipients it count as an MMS instead of SMS
fun Context.getMMS(threadId: Int? = null): ArrayList<Message> {
    val uri = Mms.CONTENT_URI
    val projection = arrayOf(
        Mms._ID,
        Mms.DATE,
        Mms.READ,
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
    val contactsMap = HashMap<Int, Contact>()
    queryCursor(uri, projection, selection, selectionArgs, showErrors = true) { cursor ->
        val id = cursor.getIntValue(Mms._ID)
        val type = cursor.getIntValue(Mms.MESSAGE_BOX)
        val date = cursor.getLongValue(Mms.DATE).toInt()
        val read = cursor.getIntValue(Mms.READ) == 1
        val thread = cursor.getIntValue(Mms.THREAD_ID)
        val participants = getThreadParticipants(thread, contactsMap)
        val attachment = getMmsAttachment(id)
        val message = Message(id, attachment?.text ?: "", type, participants, date, read, thread, attachment)
        messages.add(message)

        participants.forEach {
            contactsMap.put(it.id, it)
        }
    }
    return messages
}

// based on https://stackoverflow.com/a/6446831/1967672
@SuppressLint("NewApi")
fun Context.getMmsAttachment(id: Int): MessageAttachment? {
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
    val attachment = MessageAttachment(id, "", null, "")

    queryCursor(uri, projection, selection, selectionArgs, showErrors = true) { cursor ->
        val partId = cursor.getStringValue(Mms._ID)
        val type = cursor.getStringValue(Mms.Part.CONTENT_TYPE)
        if (type == "text/plain") {
            attachment.text = cursor.getStringValue(Mms.Part.TEXT) ?: ""
        } else if (type.startsWith("image/") || type.startsWith("video/")) {
            attachment.uri = Uri.withAppendedPath(uri, partId)
            attachment.type = type
        }
    }

    return attachment
}

fun Context.getThreadParticipants(threadId: Int, contactsMap: HashMap<Int, Contact>?): ArrayList<Contact> {
    val uri = Uri.parse("${MmsSms.CONTENT_CONVERSATIONS_URI}?simple=true")
    val projection = arrayOf(
        ThreadsColumns.RECIPIENT_IDS
    )
    val selection = "${Mms._ID} = ?"
    val selectionArgs = arrayOf(threadId.toString())
    val participants = ArrayList<Contact>()
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                val address = cursor.getStringValue(ThreadsColumns.RECIPIENT_IDS)
                address.split(" ").filter { it.areDigitsOnly() }.forEach {
                    val addressId = it.toInt()
                    if (contactsMap?.containsKey(addressId) == true) {
                        participants.add(contactsMap[addressId]!!)
                        return@forEach
                    }

                    val phoneNumber = getPhoneNumberFromAddressId(addressId)
                    val name = getNameFromPhoneNumber(phoneNumber)
                    val contact = Contact(addressId, name, "", phoneNumber, false)
                    participants.add(contact)
                }
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }
    return participants
}

fun Context.getPhoneNumberFromAddressId(canonicalAddressId: Int): String {
    val uri = Uri.withAppendedPath(MmsSms.CONTENT_URI, "canonical-addresses")
    val projection = arrayOf(
        Mms.Addr.ADDRESS
    )

    val selection = "${Mms._ID} = ?"
    val selectionArgs = arrayOf(canonicalAddressId.toString())
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
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

fun Context.getNameFromPhoneNumber(number: String): String {
    if (!hasPermission(PERMISSION_READ_CONTACTS)) {
        return number
    }

    val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    val projection = arrayOf(
        PhoneLookup.DISPLAY_NAME
    )

    try {
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor.use {
            if (cursor?.moveToFirst() == true) {
                return cursor.getStringValue(PhoneLookup.DISPLAY_NAME)
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }
    return number
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
        Threads.getOrCreateThreadId(this, address)
    } else {
        0
    }
}

@SuppressLint("NewApi")
fun Context.getThreadId(addresses: Set<String>): Long {
    return if (isMarshmallowPlus()) {
        Threads.getOrCreateThreadId(this, addresses)
    } else {
        0
    }
}

fun Context.isNumberBlocked(number: String): Boolean {
    val blockedNumbers = getBlockedNumbers()
    val numberToCompare = number.trimToComparableNumber()
    return blockedNumbers.map { it.numberToCompare }.contains(numberToCompare)
}
