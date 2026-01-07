package com.example.anomess.data

import kotlinx.coroutines.flow.Flow

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class MessageRepository(
    private val context: Context,
    private val messageDao: MessageDao, 
    private val contactDao: ContactDao
) {
    private val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
    private val mediaCrypter = com.example.anomess.security.MediaCrypter(context)

    fun saveIncomingMedia(fileName: String, data: ByteArray): String {
        // SECURITY: Sanitize filename to prevent Path Traversal
        val safeFileName = File(fileName).name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val file = File(mediaDir, safeFileName)
        
        // Use MediaCrypter for encryption
        mediaCrypter.getOutputStream(file).use { it.write(data) }
        
        return file.absolutePath
    }

    fun saveOutgoingMedia(uri: Uri): String {
        val extension = context.contentResolver.getType(uri)?.split("/")?.lastOrNull() ?: "bin"
        val fileName = "${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension"
        val destFile = File(mediaDir, fileName)
        
        context.contentResolver.openInputStream(uri)?.use { input ->
            mediaCrypter.getOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        return destFile.absolutePath
    }

    fun readMediaBytes(path: String): ByteArray {
        val file = File(path)
        if (!file.exists()) return ByteArray(0)
        
        // Try reading as encrypted first
        return try {
            mediaCrypter.getInputStream(file).use { it.readBytes() }
        } catch (e: Exception) {
            // Fallback for legacy unencrypted files
            file.readBytes()
        }
    }

    fun getDecryptedInputStream(path: String): InputStream? {
        val file = File(path)
        if (!file.exists()) return null
        
        return try {
            mediaCrypter.getInputStream(file)
        } catch (e: Exception) {
            com.example.anomess.network.TorManager.error("Decryption failed for $path", e)
            // CRITICAL: Do NOT fallback to plain stream if encryption expected. 
            // This prevents saving corrupted (encrypted) files to Gallery.
            throw e 
        }
    }

    fun getMessages(peerAddress: String): Flow<List<Message>> {
        return messageDao.getMessagesForConversation(peerAddress)
    }

    fun getLastMessage(peerAddress: String): Flow<Message?> {
        return messageDao.getLastMessage(peerAddress)
    }

    suspend fun sendMessage(message: Message): Long {
        return messageDao.insertMessage(message)
    }

    fun getAllMessages(): Flow<List<Message>> {
        return messageDao.getAllMessages()
    }
    
    // Contact Operations
    fun getAllContacts(): Flow<List<Contact>> {
        return contactDao.getAllContacts()
    }
    
    suspend fun addContact(name: String, address: String) {
        contactDao.insertContact(Contact(address, name))
    }

    suspend fun deleteContact(contact: Contact) {
        contactDao.deleteContact(contact)
    }
    
    suspend fun getContactName(address: String): String? {
        return contactDao.getContact(address)?.name
    }

    suspend fun markAsRead(contactAddress: String) {
        messageDao.markMessagesAsRead(contactAddress)
    }

    fun getUnreadCount(contactAddress: String): Flow<Int> {
        return messageDao.getUnreadCount(contactAddress)
    }

    suspend fun markMessageAsReadByPeer(timestamp: Long) {
        messageDao.markMessageAsReadByPeer(timestamp)
    }

    suspend fun clearConversation(address: String) {
        messageDao.clearConversation(address)
    }

    suspend fun deleteMessages(ids: List<Int>) {
        messageDao.deleteMessages(ids)
    }

    suspend fun updateMessageStatus(id: Int, status: Int) {
        messageDao.updateMessageStatus(id, status)
    }

    suspend fun getPendingMessages(): List<Message> {
        return messageDao.getPendingMessages()
    }

    suspend fun getContact(address: String): Contact? {
        return contactDao.getContact(address)
    }

    suspend fun updateContactKey(address: String, publicKey: String) {
        contactDao.updatePublicKey(address, publicKey)
    }
    
    suspend fun sendMessage(message: Message, publicKey: String? = null): Long {
        val id = messageDao.insertMessage(message)
        
        // If it's an incoming message and we have a key, ensure contact exists/updated
        if (!message.isMine && publicKey != null) {
            val existing = contactDao.getContact(message.senderOnionAddress)
            if (existing == null) {
                // Auto-create contact? Or just leave it?
                // Usually better to have a contact entry even if name is unknown
                val newContact = Contact(message.senderOnionAddress, "Unknown_${message.senderOnionAddress.take(6)}", publicKey)
                contactDao.insertContact(newContact)
            } else if (existing.publicKey == null) {
                // Trust first key seen
                contactDao.updatePublicKey(message.senderOnionAddress, publicKey)
            }
        }
        return id
    }

    suspend fun findMessageId(senderAddress: String, timestamp: Long): Int? {
        return messageDao.findMessageId(senderAddress, timestamp)
    }

    suspend fun updateContactName(address: String, newName: String) {
        contactDao.updateContactName(address, newName)
    }
}
