package com.simplemobiletools.smsmessenger.helpers

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.smsmessenger.models.Attachment
import com.simplemobiletools.smsmessenger.models.MessageAttachment

class Converters {
    private val gson = Gson()
    private val attachmentType = object : TypeToken<List<Attachment>>() {}.type
    private val simpleContactType = object : TypeToken<List<SimpleContact>>() {}.type
    private val messageAttachmentType = object : TypeToken<MessageAttachment?>() {}.type

    @TypeConverter
    fun jsonToAttachmentList(value: String?): ArrayList<Attachment> =
        if (value == null) arrayListOf() else gson.fromJson(value, attachmentType)

    @TypeConverter
    fun attachmentListToJson(list: ArrayList<Attachment>) = gson.toJson(list)

    @TypeConverter
    fun jsonToSimpleContactList(value: String?): ArrayList<SimpleContact> =
        if (value == null) arrayListOf() else gson.fromJson(value, simpleContactType)

    @TypeConverter
    fun simpleContactListToJson(list: ArrayList<SimpleContact>) = gson.toJson(list)

    @TypeConverter
    fun jsonToMessageAttachment(value: String?): MessageAttachment? =
        if (value == null) null else gson.fromJson(value, messageAttachmentType)

    @TypeConverter
    fun messageAttachmentToJson(messageAttachment: MessageAttachment?) = gson.toJson(messageAttachment)
}
