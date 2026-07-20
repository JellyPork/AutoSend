package com.autosend.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Transaction
    @Query("SELECT * FROM scheduled_messages ORDER BY scheduleAtMillis ASC")
    fun observeAll(): Flow<List<MessageWithAttachments>>

    @Transaction
    @Query("SELECT * FROM scheduled_messages WHERE id = :id")
    suspend fun getWithAttachments(id: Long): MessageWithAttachments?

    @Query("SELECT * FROM scheduled_messages WHERE status = :status")
    suspend fun getByStatus(status: SendStatus): List<ScheduledMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ScheduledMessage): Long

    @Update
    suspend fun updateMessage(message: ScheduledMessage)

    @Query("UPDATE scheduled_messages SET status = :status, lastAttemptMillis = :attemptMillis, failureReason = :reason WHERE id = :id")
    suspend fun updateStatus(id: Long, status: SendStatus, attemptMillis: Long?, reason: String?)

    @Query("DELETE FROM scheduled_messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<Attachment>)

    @Query("DELETE FROM attachments WHERE messageId = :messageId")
    suspend fun deleteAttachmentsFor(messageId: Long)
}
