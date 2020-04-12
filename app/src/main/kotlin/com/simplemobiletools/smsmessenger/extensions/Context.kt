package com.simplemobiletools.smsmessenger.extensions

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.PhoneLookup
import android.provider.Telephony.*
import android.text.TextUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.ThreadActivity
import com.simplemobiletools.smsmessenger.helpers.Config
import com.simplemobiletools.smsmessenger.helpers.THREAD_ID
import com.simplemobiletools.smsmessenger.helpers.letterBackgroundColors
import com.simplemobiletools.smsmessenger.models.*
import java.util.*
import kotlin.collections.ArrayList

val Context.config: Config get() = Config.newInstance(applicationContext)

fun Context.getMessages(threadId: Int): ArrayList<Message> {
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

    val selection = "${Sms.THREAD_ID} = ?"
    val selectionArgs = arrayOf(threadId.toString())

    var messages = ArrayList<Message>()
    queryCursor(uri, projection, selection, selectionArgs, showErrors = true) { cursor ->
        val senderNumber = cursor.getStringValue(Sms.ADDRESS)
        if (isNumberBlocked(senderNumber)) {
            return@queryCursor
        }

        val id = cursor.getIntValue(Sms._ID)
        val body = cursor.getStringValue(Sms.BODY)
        val type = cursor.getIntValue(Sms.TYPE)
        val namePhoto = getNameAndPhotoFromPhoneNumber(senderNumber)
        val senderName = namePhoto?.name ?: ""
        val photoUri = namePhoto?.photoUri ?: ""
        val date = (cursor.getLongValue(Sms.DATE) / 1000).toInt()
        val read = cursor.getIntValue(Sms.READ) == 1
        val thread = cursor.getIntValue(Sms.THREAD_ID)
        val participant = Contact(0, senderName, photoUri, senderNumber)
        val isMMS = false
        val message = Message(id, body, type, arrayListOf(participant), date, read, thread, isMMS, null, senderName, photoUri)
        messages.add(message)
    }

    messages.addAll(getMMS(threadId))
    messages = messages.filter { it.participants.isNotEmpty() }
        .sortedByDescending { it.date }.toMutableList() as ArrayList<Message>

    return messages
}

// as soon as a message contains multiple recipients it count as an MMS instead of SMS
fun Context.getMMS(threadId: Int? = null, sortOrder: String? = null): ArrayList<Message> {
    val uri = Mms.CONTENT_URI
    val projection = arrayOf(
        Mms._ID,
        Mms.DATE,
        Mms.READ,
        Mms.MESSAGE_BOX,
        Mms.THREAD_ID
    )

    val selection = if (threadId == null) {
        "1 == 1) GROUP BY (${Mms.THREAD_ID}"
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
    queryCursor(uri, projection, selection, selectionArgs, sortOrder, showErrors = true) { cursor ->
        val mmsId = cursor.getIntValue(Mms._ID)
        val type = cursor.getIntValue(Mms.MESSAGE_BOX)
        val date = cursor.getLongValue(Mms.DATE).toInt()
        val read = cursor.getIntValue(Mms.READ) == 1
        val threadId = cursor.getIntValue(Mms.THREAD_ID)
        val participants = getThreadParticipants(threadId, contactsMap)
        val isMMS = true
        val attachment = getMmsAttachment(mmsId)
        val body = attachment?.text ?: ""
        var senderName = ""
        var senderPhotoUri = ""

        if (type != Mms.MESSAGE_BOX_SENT && type != Mms.MESSAGE_BOX_FAILED) {
            val number = getMMSSender(mmsId)
            val namePhoto = getNameAndPhotoFromPhoneNumber(number)
            if (namePhoto != null) {
                senderName = namePhoto.name
                senderPhotoUri = namePhoto.photoUri ?: ""
            }
        }

        val message = Message(mmsId, body, type, participants, date, read, threadId, isMMS, attachment, senderName, senderPhotoUri)
        messages.add(message)

        participants.forEach {
            contactsMap.put(it.id, it)
        }
    }

    return messages
}

fun Context.getMMSSender(msgId: Int): String {
    val uri = Uri.parse("${Mms.CONTENT_URI}/$msgId/addr")
    val projection = arrayOf(
        Mms.Addr.ADDRESS
    )

    try {
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getStringValue(Mms.Addr.ADDRESS)
            }
        }
    } catch (ignored: Exception) {
    }
    return ""
}

fun Context.getConversations(): ArrayList<Conversation> {
    val uri = Uri.parse("${Threads.CONTENT_URI}?simple=true")
    val projection = arrayOf(
        Threads._ID,
        Threads.SNIPPET,
        Threads.DATE,
        Threads.READ,
        Threads.RECIPIENT_IDS
    )

    val selection = "${Threads.ARCHIVED} = ? AND ${Threads.MESSAGE_COUNT} > ?"
    val selectionArgs = arrayOf("0", "0")
    val conversations = ArrayList<Conversation>()
    queryCursor(uri, projection, selection, selectionArgs, showErrors = true) { cursor ->
        val id = cursor.getIntValue(Threads._ID)
        var snippet = cursor.getStringValue(Threads.SNIPPET) ?: ""
        if (snippet.isEmpty()) {
            snippet = getThreadSnippet(id)
        }

        var date = cursor.getLongValue(Threads.DATE)
        if (date.toString().length > 10) {
            date /= 1000
        }

        val read = cursor.getIntValue(Threads.READ) == 1
        val rawIds = cursor.getStringValue(Threads.RECIPIENT_IDS)
        val recipientIds = rawIds.split(" ").filter { it.areDigitsOnly() }.map { it.toInt() }.toMutableList()
        val phoneNumbers = getThreadPhoneNumbers(recipientIds)
        val names = getThreadContactNames(phoneNumbers)
        val title = TextUtils.join(", ", names.toTypedArray())
        val photoUri = if (phoneNumbers.size == 1) getPhotoUriFromPhoneNumber(phoneNumbers.first()) else ""
        val isGroupConversation = phoneNumbers.size > 1
        val conversation = Conversation(id, snippet, date.toInt(), read, title, photoUri, isGroupConversation)
        conversations.add(conversation)
    }
    return conversations
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
    val messageAttachment = MessageAttachment(id, "", arrayListOf())

    queryCursor(uri, projection, selection, selectionArgs, showErrors = true) { cursor ->
        val partId = cursor.getStringValue(Mms._ID)
        val type = cursor.getStringValue(Mms.Part.CONTENT_TYPE)
        if (type == "text/plain") {
            messageAttachment.text = cursor.getStringValue(Mms.Part.TEXT) ?: ""
        } else if (type.startsWith("image/") || type.startsWith("video/")) {
            val attachment = Attachment(Uri.withAppendedPath(uri, partId), type, 0, 0)
            messageAttachment.attachments.add(attachment)
        }
    }

    return messageAttachment
}

fun Context.getLatestMMS(): Message? {
    val sortOrder = "${Mms.DATE} DESC LIMIT 1"
    return getMMS(sortOrder = sortOrder).firstOrNull()
}

fun Context.getThreadSnippet(threadId: Int): String {
    val sortOrder = "${Mms.DATE} DESC LIMIT 1"
    val latestMms = getMMS(threadId, sortOrder).firstOrNull()
    var snippet = latestMms?.body ?: ""

    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms.BODY
    )

    val selection = "${Sms.THREAD_ID} = ? AND ${Sms.DATE} > ?"
    val selectionArgs = arrayOf(
        threadId.toString(),
        latestMms?.date?.toString() ?: "0"
    )
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        cursor?.use {
            if (cursor.moveToFirst()) {
                snippet = cursor.getStringValue(Sms.BODY)
            }
        }
    } catch (ignored: Exception) {
    }
    return snippet
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
                    val namePhoto = getNameAndPhotoFromPhoneNumber(phoneNumber)
                    val name = namePhoto?.name ?: ""
                    val photoUri = namePhoto?.photoUri ?: ""
                    val contact = Contact(addressId, name, photoUri, phoneNumber)
                    participants.add(contact)
                }
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }
    return participants
}

fun Context.getThreadPhoneNumbers(recipientIds: List<Int>): ArrayList<String> {
    val numbers = ArrayList<String>()
    recipientIds.forEach {
        numbers.add(getPhoneNumberFromAddressId(it))
    }
    return numbers
}

fun Context.getThreadContactNames(phoneNumbers: List<String>): ArrayList<String> {
    val names = ArrayList<String>()
    phoneNumbers.forEach {
        names.add(getNameFromPhoneNumber(it))
    }
    return names
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

fun Context.getSuggestedContacts(): ArrayList<Contact> {
    val contacts = ArrayList<Contact>()
    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms.ADDRESS
    )

    val selection = "1 == 1) GROUP BY (${Sms.ADDRESS}"
    val selectionArgs = null
    val sortOrder = "${Sms.DATE} DESC LIMIT 20"

    queryCursor(uri, projection, selection, selectionArgs, sortOrder, showErrors = true) { cursor ->
        val senderNumber = cursor.getStringValue(Sms.ADDRESS)
        val namePhoto = getNameAndPhotoFromPhoneNumber(senderNumber)
        if (namePhoto == null || namePhoto.name == senderNumber) {
            return@queryCursor
        }

        val senderName = namePhoto.name
        val photoUri = namePhoto.photoUri ?: ""
        val contact = Contact(0, senderName, photoUri, senderNumber)
        contacts.add(contact)
    }

    return contacts
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
        }

        allContacts = allContacts.filter { it.name.isNotEmpty() }.distinctBy {
            val startIndex = Math.max(0, it.phoneNumber.length - 9)
            it.phoneNumber.substring(startIndex)
        }.toMutableList() as ArrayList<Contact>

        allContacts.sortBy { it.name.normalizeString().toLowerCase(Locale.getDefault()) }
        callback(allContacts)
    }
}

fun Context.getNameAndPhotoFromPhoneNumber(number: String): NamePhoto? {
    if (!hasPermission(PERMISSION_READ_CONTACTS)) {
        return NamePhoto(number, null)
    }

    val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    val projection = arrayOf(
        PhoneLookup.DISPLAY_NAME,
        PhoneLookup.PHOTO_URI
    )

    try {
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor.use {
            if (cursor?.moveToFirst() == true) {
                val name = cursor.getStringValue(PhoneLookup.DISPLAY_NAME)
                val photoUri = cursor.getStringValue(PhoneLookup.PHOTO_URI)
                return NamePhoto(name, photoUri)
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }

    return NamePhoto(number, null)
}

fun Context.getNameFromPhoneNumber(number: String): String {
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

fun Context.getPhotoUriFromPhoneNumber(number: String): String {
    val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    val projection = arrayOf(
        PhoneLookup.PHOTO_URI
    )

    try {
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor.use {
            if (cursor?.moveToFirst() == true) {
                return cursor.getStringValue(PhoneLookup.PHOTO_URI) ?: ""
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }

    return ""
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
                val contact = Contact(id, fullName, photoUri, "")
                contacts.add(contact)
            }
        }

        val isOrganization = mimetype == Organization.CONTENT_ITEM_TYPE
        if (isOrganization) {
            val company = cursor.getStringValue(Organization.COMPANY) ?: ""
            val jobTitle = cursor.getStringValue(Organization.TITLE) ?: ""
            if (company.isNotEmpty() || jobTitle.isNotEmpty()) {
                val fullName = "$company $jobTitle".trim()
                val contact = Contact(id, fullName, photoUri, "")
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
            val contact = Contact(id, "", "", phoneNumber)
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

fun Context.deleteConversation(id: Int) {
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

fun Context.markMessageRead(id: Int, isMMS: Boolean) {
    val uri = if (isMMS) Mms.CONTENT_URI else Sms.CONTENT_URI
    val contentValues = ContentValues().apply {
        put(Sms.READ, 1)
        put(Sms.SEEN, 1)
    }
    val selection = "${Sms._ID} = ?"
    val selectionArgs = arrayOf(id.toString())
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

@SuppressLint("NewApi")
fun Context.showReceivedMessageNotification(address: String, body: String, threadID: Int, bitmap: Bitmap? = null) {
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    val channelId = "simple_sms_messenger"
    if (isOreoPlus()) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
            .build()

        val name = getString(R.string.channel_received_sms)
        val importance = NotificationManager.IMPORTANCE_HIGH
        NotificationChannel(channelId, name, importance).apply {
            setBypassDnd(false)
            enableLights(true)
            setSound(soundUri, audioAttributes)
            enableVibration(true)
            notificationManager.createNotificationChannel(this)
        }
    }

    val intent = Intent(this, ThreadActivity::class.java).apply {
        putExtra(THREAD_ID, threadID)
    }

    val pendingIntent = PendingIntent.getActivity(this, threadID, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    val summaryText = getString(R.string.new_message)
    val sender = getNameAndPhotoFromPhoneNumber(address)?.name ?: ""

    val largeIcon = bitmap ?: getNotificationLetterIcon(sender)
    val builder = NotificationCompat.Builder(this, channelId)
        .setContentTitle(sender)
        .setContentText(body)
        .setSmallIcon(R.drawable.ic_messenger)
        .setLargeIcon(largeIcon)
        .setStyle(NotificationCompat.BigTextStyle().setSummaryText(summaryText).bigText(body))
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setDefaults(Notification.DEFAULT_LIGHTS)
        .setCategory(Notification.CATEGORY_MESSAGE)
        .setAutoCancel(true)
        .setSound(soundUri, AudioManager.STREAM_NOTIFICATION)
        .setChannelId(channelId)

    notificationManager.notify(threadID, builder.build())
}

fun Context.getNotificationLetterIcon(name: String): Bitmap {
    val letter = name.getNameLetter()
    val size = resources.getDimension(R.dimen.notification_large_icon_size).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val view = TextView(this)
    view.layout(0, 0, size, size)

    val circlePaint = Paint().apply {
        color = letterBackgroundColors[Math.abs(name.hashCode()) % letterBackgroundColors.size].toInt()
        isAntiAlias = true
    }

    val wantedTextSize = size / 2f
    val textPaint = Paint().apply {
        color = circlePaint.color.getContrastColor()
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = wantedTextSize
    }

    canvas.drawCircle(size / 2f, size / 2f, size / 2f, circlePaint)

    val xPos = canvas.width / 2f
    val yPos = canvas.height / 2 - (textPaint.descent() + textPaint.ascent()) / 2
    canvas.drawText(letter, xPos, yPos, textPaint)
    view.draw(canvas)
    return bitmap
}

fun Context.loadImage(path: String, imageView: ImageView, placeholderName: String, placeholderImage: Drawable? = null) {
    val placeholder = placeholderImage ?: BitmapDrawable(resources, getNotificationLetterIcon(placeholderName))

    val options = RequestOptions()
        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
        .error(placeholder)
        .centerCrop()

    Glide.with(this)
        .load(path)
        .transition(DrawableTransitionOptions.withCrossFade())
        .placeholder(placeholder)
        .apply(options)
        .apply(RequestOptions.circleCropTransform())
        .into(imageView)
}
