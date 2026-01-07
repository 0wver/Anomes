package com.example.anomess.data

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val senderOnionAddress: String, // The .onion address of the sender
    val receiverOnionAddress: String, // The .onion address of the receiver (myself or them)
    val content: String,
    val timestamp: Long,
    val isMine: Boolean, // True if I sent it
    val isRead: Boolean = isMine, // My messages are read by default, incoming are unread (false)
    val type: Int = TYPE_TEXT,
    val mediaPath: String? = null,
    val senderTimestamp: Long? = null, // The timestamp from the sender's clock (for receipts)
    val status: Int = STATUS_SENDING, // 0=Sending, 1=Sent, 2=Failed
    val replyToMessageId: Int? = null, // ID of message being replied to
    val replyToContent: String? = null // Preview of replied message (denormalized for perf)
) {
    companion object {
        const val TYPE_TEXT = 0
        const val TYPE_IMAGE = 1
        const val TYPE_AUDIO = 2
        const val TYPE_FILE = 3
        
        const val STATUS_SENDING = 0
        const val STATUS_SENT = 1
        const val STATUS_FAILED = 2
        const val STATUS_READ = 3 // Optional: if we want to merge read/status later
    }
}
