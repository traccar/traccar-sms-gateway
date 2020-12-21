package com.simplemobiletools.smsmessenger.interfaces

import androidx.room.Dao
import androidx.room.Query
import com.simplemobiletools.smsmessenger.models.Attachment

@Dao
interface AttachmentsDao {
    @Query("SELECT * FROM attachments")
    fun getAll(): List<Attachment>
}
