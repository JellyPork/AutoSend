package com.autosend.data

import android.content.Context
import android.net.Uri
import com.autosend.scheduler.AlarmScheduler
import com.autosend.util.AttachmentStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Single entry point for all message persistence + scheduling. Keeps the DB, attachment files,
 * and the alarm in sync so callers never touch them separately.
 */
class MessageRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val dao = AppDatabase.get(appContext).messageDao()
    private val scheduler = AlarmScheduler(appContext)

    fun observeAll(): Flow<List<MessageWithAttachments>> = dao.observeAll()

    suspend fun getWithAttachments(id: Long): MessageWithAttachments? =
        withContext(Dispatchers.IO) { dao.getWithAttachments(id) }

    /**
     * Inserts or updates a message, copies any newly picked attachments into private storage,
     * and (re)schedules the alarm when the message is still PENDING.
     */
    suspend fun save(message: ScheduledMessage, newAttachmentUris: List<Uri>): Long =
        withContext(Dispatchers.IO) {
            // Insert when new; update in place when editing. A REPLACE-insert would cascade-delete
            // the existing attachments (FK onDelete=CASCADE), so we must UPDATE, not re-insert.
            val id = if (message.id == 0L) {
                dao.insertMessage(message)
            } else {
                dao.updateMessage(message); message.id
            }
            if (newAttachmentUris.isNotEmpty()) {
                val copied = newAttachmentUris.map { AttachmentStorage.copyIn(appContext, it, id) }
                dao.insertAttachments(copied)
            }
            val saved = message.copy(id = id)
            scheduler.cancel(id)
            if (saved.status == SendStatus.PENDING) scheduler.schedule(saved)
            id
        }

    suspend fun delete(messageId: Long) = withContext(Dispatchers.IO) {
        scheduler.cancel(messageId)
        val existing = dao.getWithAttachments(messageId)
        if (existing != null) AttachmentStorage.deleteFiles(existing.attachments)
        dao.deleteMessage(messageId)   // cascades to attachment rows
    }

    suspend fun markStatus(id: Long, status: SendStatus, reason: String? = null) =
        withContext(Dispatchers.IO) {
            dao.updateStatus(id, status, System.currentTimeMillis(), reason)
        }

    /** Re-arms alarms for everything still PENDING (called after reboot / app update). */
    suspend fun reschedulePending() = withContext(Dispatchers.IO) {
        dao.getByStatus(SendStatus.PENDING).forEach { scheduler.schedule(it) }
    }

    companion object {
        @Volatile private var instance: MessageRepository? = null
        fun get(context: Context): MessageRepository =
            instance ?: synchronized(this) {
                instance ?: MessageRepository(context).also { instance = it }
            }
    }
}
