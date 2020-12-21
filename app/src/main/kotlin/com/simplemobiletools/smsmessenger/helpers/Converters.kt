package com.simplemobiletools.smsmessenger.helpers

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.smsmessenger.models.Attachment

class Converters {
    private val gson = Gson()
    private val attachmentType = object : TypeToken<List<Attachment>>() {}.type

    @TypeConverter
    fun jsonToAttachmentList(value: String) = gson.fromJson<ArrayList<Attachment>>(value, attachmentType)

    @TypeConverter
    fun attachmentListToJson(list: ArrayList<Attachment>) = gson.toJson(list)
}
