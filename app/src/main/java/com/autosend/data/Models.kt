package com.autosend.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Target messaging app for a scheduled message. */
enum class TargetApp(val packageName: String, val label: String) {
    WHATSAPP("com.whatsapp", "WhatsApp"),
    WHATSAPP_BUSINESS("com.whatsapp.w4b", "WhatsApp Business"),
    MESSENGER("com.facebook.orca", "Messenger");
}

/** Lifecycle of a scheduled message. */
enum class SendStatus {
    PENDING,   // scheduled, not yet fired
    SENDING,   // currently being executed
    SENT,      // confirmed sent
    FAILED,    // could not be sent
    CANCELED   // user canceled before firing
}

/** Kind of an attachment; drives the MIME and the WhatsApp share flow. */
enum class AttachmentKind { IMAGE, FILE }

@Entity(tableName = "scheduled_messages")
data class ScheduledMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetApp: TargetApp = TargetApp.WHATSAPP,
    /** Display name of the contact, used by the accessibility service to pick it in the share sheet. */
    val contactName: String = "",
    /** Phone in E.164 without '+' or spaces (e.g. 5215512345678). Enables the reliable wa.me deep link. */
    val phoneE164: String = "",
    val text: String = "",
    val scheduleAtMillis: Long = 0L,
    val status: SendStatus = SendStatus.PENDING,
    /** true = try to tap Send automatically; false = just open the chat/notification and let the user tap. */
    val autoSend: Boolean = true,
    /** Filled in when status becomes SENT/FAILED. */
    val lastAttemptMillis: Long? = null,
    val failureReason: String? = null,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = ScheduledMessage::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("messageId")]
)
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    /** Absolute path of the copy stored inside the app's files dir. */
    val localPath: String,
    val displayName: String,
    val mimeType: String,
    val kind: AttachmentKind,
)

/** A message together with its attachments, returned by the DAO for the sender/UI. */
data class MessageWithAttachments(
    @Embedded val message: ScheduledMessage,
    @Relation(parentColumn = "id", entityColumn = "messageId")
    val attachments: List<Attachment>,
)
