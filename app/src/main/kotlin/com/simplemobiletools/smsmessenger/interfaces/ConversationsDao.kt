package com.simplemobiletools.smsmessenger.interfaces

import androidx.room.*
import com.simplemobiletools.smsmessenger.models.Conversation

@Dao
interface ConversationsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(conversation: Conversation): Long

    @Query("SELECT * FROM conversations WHERE archived = 0")
    fun getNonArchived(): List<Conversation>

    @Query("SELECT * FROM conversations WHERE archived = 1")
    fun getAllArchived(): List<Conversation>

    @Query("SELECT * FROM conversations WHERE (SELECT COUNT(*) FROM messages LEFT OUTER JOIN recycle_bin_messages ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NOT NULL AND messages.thread_id = conversations.thread_id) > 0")
    fun getAllWithMessagesInRecycleBin(): List<Conversation>

    @Query("SELECT * FROM conversations WHERE thread_id = :threadId")
    fun getConversationWithThreadId(threadId: Long): Conversation?

    @Query("SELECT * FROM conversations WHERE read = 0")
    fun getUnreadConversations(): List<Conversation>

    @Query("SELECT * FROM conversations WHERE title LIKE :text")
    fun getConversationsWithText(text: String): List<Conversation>

    @Query("UPDATE conversations SET read = 1 WHERE thread_id = :threadId")
    fun markRead(threadId: Long)

    @Query("UPDATE conversations SET read = 0 WHERE thread_id = :threadId")
    fun markUnread(threadId: Long)

    @Query("UPDATE conversations SET archived = 1 WHERE thread_id = :threadId")
    fun moveToArchive(threadId: Long)

    @Query("UPDATE conversations SET archived = 0 WHERE thread_id = :threadId")
    fun unarchive(threadId: Long)

    @Query("DELETE FROM conversations WHERE thread_id = :threadId")
    fun deleteThreadId(threadId: Long)
}
