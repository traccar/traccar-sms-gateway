package com.simplemobiletools.smsmessenger.interfaces

import androidx.room.Dao
import androidx.room.Query
import com.simplemobiletools.smsmessenger.models.Message

@Dao
interface MessagesDao {
    @Query("SELECT * FROM messages")
    fun getAll(): List<Message>
}
