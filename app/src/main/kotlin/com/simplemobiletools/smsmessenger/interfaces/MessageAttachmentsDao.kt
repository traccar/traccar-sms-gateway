package com.simplemobiletools.smsmessenger.interfaces

import androidx.room.Dao
import androidx.room.Query
import com.simplemobiletools.smsmessenger.models.MessageAttachment

@Dao
interface MessageAttachmentsDao {
    @Query("SELECT * FROM message_attachments")
    fun getAll(): List<MessageAttachment>
}
