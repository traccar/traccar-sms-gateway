package com.simplemobiletools.smsmessenger.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations", indices = [(Index(value = ["thread_id"], unique = true))])
data class Conversation(
    @PrimaryKey @ColumnInfo(name = "thread_id") var threadId: Long,
    @ColumnInfo(name = "snippet") var snippet: String,
    @ColumnInfo(name = "date") var date: Int,
    @ColumnInfo(name = "read") var read: Boolean,
    @ColumnInfo(name = "title") var title: String,
    @ColumnInfo(name = "photo_uri") var photoUri: String,
    @ColumnInfo(name = "is_group_conversation") var isGroupConversation: Boolean,
    @ColumnInfo(name = "phone_number") var phoneNumber: String,
    @ColumnInfo(name = "is_scheduled") var isScheduled: Boolean = false
) {

    fun areContentsTheSame(other: Conversation): Boolean {
        return snippet == other.snippet &&
            date == other.date &&
            read == other.read &&
            title == other.title &&
            photoUri == other.photoUri &&
            isGroupConversation == other.isGroupConversation &&
            phoneNumber == other.phoneNumber
    }
}
