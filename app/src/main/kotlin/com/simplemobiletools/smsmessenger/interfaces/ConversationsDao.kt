package com.simplemobiletools.smsmessenger.interfaces

import androidx.room.*
import com.simplemobiletools.smsmessenger.models.ArchivedConversation
import com.simplemobiletools.smsmessenger.models.Conversation

@Dao
interface ConversationsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(conversation: Conversation): Long

    @Query("SELECT conversations.* FROM conversations LEFT OUTER JOIN archived_conversations ON conversations.thread_id = archived_conversations.thread_id WHERE archived_conversations.deleted_ts is NULL")
    fun getNonArchived(): List<Conversation>

    @Query("SELECT conversations.* FROM archived_conversations INNER JOIN conversations ON conversations.thread_id = archived_conversations.thread_id")
    fun getAllArchived(): List<Conversation>

    @Query("SELECT COUNT(*) FROM archived_conversations")
    fun getArchivedCount(): Int

    @Query("SELECT * FROM archived_conversations WHERE deleted_ts < :timestamp")
    fun getOldArchived(timestamp: Long): List<ArchivedConversation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun archiveConversation(archivedConversation: ArchivedConversation)

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

    @Query("DELETE FROM conversations WHERE thread_id = :threadId")
    fun deleteThreadFromConversations(threadId: Long)

    @Query("DELETE FROM archived_conversations WHERE thread_id = :threadId")
    fun deleteThreadFromArchivedConversations(threadId: Long)

    @Transaction
    fun deleteThreadId(threadId: Long) {
        deleteThreadFromConversations(threadId)
        deleteThreadFromArchivedConversations(threadId)
    }
}
