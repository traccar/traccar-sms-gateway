package com.simplemobiletools.smsmessenger.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simplemobiletools.smsmessenger.models.Conversation

@Dao
interface ConversationsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(conversation: Conversation): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(conversations: List<Conversation>)

    @Query("SELECT * FROM conversations")
    fun getAll(): List<Conversation>
}
