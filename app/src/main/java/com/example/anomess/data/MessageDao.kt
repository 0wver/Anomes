package com.example.anomess.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE senderOnionAddress = :address OR receiverOnionAddress = :address ORDER BY timestamp ASC")
    fun getMessagesForConversation(address: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE senderOnionAddress = :address OR receiverOnionAddress = :address ORDER BY timestamp DESC LIMIT 1")
    fun getLastMessage(address: String): Flow<Message?>

    @Insert
    suspend fun insertMessage(message: Message): Long
    
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<Message>>

    @Query("UPDATE messages SET isRead = 1 WHERE senderOnionAddress = :contactAddress AND isRead = 0")
    suspend fun markMessagesAsRead(contactAddress: String)

    @Query("SELECT COUNT(*) FROM messages WHERE senderOnionAddress = :contactAddress AND isRead = 0")
    fun getUnreadCount(contactAddress: String): Flow<Int>

    @Query("UPDATE messages SET isRead = 1 WHERE timestamp <= :timestamp AND isMine = 1 AND isRead = 0")
    suspend fun markMessageAsReadByPeer(timestamp: Long)

    @Query("DELETE FROM messages WHERE senderOnionAddress = :address OR receiverOnionAddress = :address")
    suspend fun clearConversation(address: String)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteMessages(ids: List<Int>)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateMessageStatus(id: Int, status: Int)

    @Query("SELECT * FROM messages WHERE status = 2 ORDER BY timestamp ASC") // Only retry FAILED messages (status=2)
    suspend fun getPendingMessages(): List<Message>

    @Query("""
        SELECT id FROM messages 
        WHERE senderOnionAddress = :senderAddress 
        AND (
            (isMine = 1 AND timestamp = :timestamp)
            OR 
            (isMine = 0 AND senderTimestamp = :timestamp)
        )
        LIMIT 1
    """)
    suspend fun findMessageId(senderAddress: String, timestamp: Long): Int?
}
