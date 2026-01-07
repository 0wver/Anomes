package com.example.anomess.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.anomess.AnomessApp
import com.example.anomess.data.Message
import com.example.anomess.data.MessageRepository
import com.example.anomess.network.P2PConnectionManager
import com.example.anomess.network.TorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

class ChatViewModel(
    private val torManager: TorManager,
    private val connectionManager: P2PConnectionManager,
    private val repository: MessageRepository
) : ViewModel() {

    private val _torStatus = MutableStateFlow("Initializing...")
    val torStatus: StateFlow<String> = _torStatus.asStateFlow()
    
    private val _onionAddress = MutableStateFlow<String?>(null)
    val onionAddress: StateFlow<String?> = _onionAddress.asStateFlow()

    val debugLogs: StateFlow<String> = TorManager.logFlow

    // For simplicity, we just fetch all messages for now. 
    // In a real app we'd filter by conversation.
    // However, Flow<List<Message>> can be exposed directly from Repository if we want all.
    // Let's assume we are chatting with one person or just showing a global log for this MVP.
    // The DAO has getAllMessages()
    // The DAO has getAllMessages()
    // For simplicity, we just fetch all messages for now. 
    // In a real app we'd filter by conversation.
    // Optimized filtered flow for a specific contact
    // Optimized filtered flow for a specific contact
    fun getMessagesForContact(contactAddress: String): Flow<List<Message>> {
        // Use direct SQL query for O(1) performance instead of in-memory filtering
        return repository.getMessages(contactAddress)
    }
    
    val contacts = repository.getAllContacts()
    
    fun getUnreadCount(contactAddress: String): Flow<Int> = repository.getUnreadCount(contactAddress)
    
    fun markAsRead(contactAddress: String) {
        viewModelScope.launch {
            repository.markAsRead(contactAddress.trim())
            // Correct Logic: Send receipt using the timestamp the SENDER gave us
            try {
                val lastMsg = repository.getLastMessage(contactAddress.trim()).first()
                if (lastMsg != null && !lastMsg.isMine) {
                     val tsToSend = lastMsg.senderTimestamp ?: lastMsg.timestamp
                     connectionManager.sendReadReceipt(contactAddress.trim(), tsToSend)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addContact(name: String, address: String) {
        viewModelScope.launch {
            repository.addContact(name, address.trim())
        }
    }

    fun deleteContact(contact: com.example.anomess.data.Contact) {
        viewModelScope.launch {
            repository.deleteContact(contact)
        }
    }
    
    fun getLastMessage(contactAddress: String): Flow<Message?> = repository.getLastMessage(contactAddress)
    
    fun getContactFlow(address: String): Flow<com.example.anomess.data.Contact?> {
        // We can use getAllContacts and filter, or add single contact flow to DAO
        // For efficiency, let's just filter getAllContacts for now if DAO doesn't have flow
        // But DAO has getAllContacts flow.
        return repository.getAllContacts().map { list ->
            list.find { it.onionAddress == address }
        }
    }

    fun getMyPublicKey(): String? {
        return try {
            val keyBytes = com.example.anomess.security.SecurityManager(com.example.anomess.AnomessApp.instance).getMyPublicKey()
            android.util.Base64.encodeToString(keyBytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null 
        }
    }

    init {
        startTor()
    }

    // Track message IDs currently being retried to prevent duplicate sends
    private val messagesBeingRetried = mutableSetOf<Int>()
    private val retryLock = kotlinx.coroutines.sync.Mutex()

    // Check for pending messages periodically or when online
    private fun checkPendingMessages() {
        viewModelScope.launch {
            while(true) {
                // Skip retries while Tor is restarting (TorService handles it)
                if (torManager.isRestarting) {
                    TorManager.log("Skipping retry - Tor is restarting...")
                    kotlinx.coroutines.delay(5000)
                    continue
                }
                
                val pending = repository.getPendingMessages()
                if (pending.isNotEmpty()) {
                    // Filter out messages already being retried
                    val toRetry = retryLock.withLock {
                        pending.filter { msg -> !messagesBeingRetried.contains(msg.id) }
                    }
                    if (toRetry.isNotEmpty()) {
                        TorManager.log("Found ${toRetry.size} pending messages to retry (${pending.size - toRetry.size} already in progress)")
                        toRetry.forEach { msg ->
                            retryMessage(msg)
                        }
                    }
                }
                // Retry every 30 seconds for faster recovery after reconnect
                kotlinx.coroutines.delay(30_000)
            }
        }
    }

    private fun startTor() {
        viewModelScope.launch {
            _torStatus.value = "Starting Tor..."
            val success = torManager.startTor()
            
            if (success) {
                _torStatus.value = "Tor Connected"
                _onionAddress.value = torManager.getOnionHostname()
                connectionManager.startListening()
                
                // Retry pending messages once connected
                checkPendingMessages()
            } else {
                _torStatus.value = "Tor Failed"
            }
        }
    }
    
    fun updateContactName(address: String, newName: String) {
        viewModelScope.launch {
            repository.updateContactName(address, newName)
        }
    }

    fun sendMessage(recipientOnion: String, content: String, replyToId: Int? = null, replyToContent: String? = null, replyToTimestamp: Long? = null, replyToSender: String? = null) {
        viewModelScope.launch {
            if (content.isBlank()) return@launch
            
            val myAddress = onionAddress.value ?: "unknown"
            val message = Message(
                senderOnionAddress = myAddress,
                receiverOnionAddress = recipientOnion,
                content = content,
                timestamp = System.currentTimeMillis(),
                isMine = true,
                isRead = false,
                status = Message.STATUS_SENDING,
                replyToMessageId = replyToId,
                replyToContent = replyToContent
            )
            // Capture ID from insert
            val msgId = repository.sendMessage(message).toInt()
            
            // Send over network (include reply content)
            launch(Dispatchers.IO) {
                val sent = connectionManager.sendMessage(recipientOnion, content, replyToContent, replyToTimestamp, replyToSender)
                if (sent) {
                    repository.updateMessageStatus(msgId, Message.STATUS_SENT)
                } else {
                    repository.updateMessageStatus(msgId, Message.STATUS_FAILED)
                }
            }
        }
    }
    
    // ...
    // ...
    
    fun retryMessage(message: Message) {
         viewModelScope.launch(Dispatchers.IO) {
             // Check and mark as being retried
             val canRetry = retryLock.withLock {
                 if (messagesBeingRetried.contains(message.id)) {
                     false // Already being retried
                 } else {
                     messagesBeingRetried.add(message.id)
                     true
                 }
             }
             
             if (!canRetry) {
                 TorManager.log("Message ${message.id} already being retried, skipping")
                 return@launch
             }
             
             try {
                 // 1. Update text to "Sending"
                 repository.updateMessageStatus(message.id, Message.STATUS_SENDING)
                 
                 // 2. Try network
                 val success = if (message.type == Message.TYPE_TEXT) {
                     connectionManager.sendMessage(message.receiverOnionAddress, message.content)
                 } else {
                     if (message.mediaPath != null) {
                         val bytes = repository.readMediaBytes(message.mediaPath)
                         val filename = if (message.type == Message.TYPE_IMAGE) "retry_img.jpg" else "retry_voice.m4a"
                         val metadata = mapOf(
                             "sender" to message.senderOnionAddress, 
                             "filename" to filename, 
                             "timestamp" to message.timestamp.toString()
                         )
                         val payload = com.example.anomess.network.MessagePayload(message.type, metadata, bytes)
                         connectionManager.sendMessagePayload(message.receiverOnionAddress, payload)
                     } else {
                         false 
                     }
                 }

                 // 3. Update status
                 if (success) {
                     repository.updateMessageStatus(message.id, Message.STATUS_SENT)
                 } else {
                     repository.updateMessageStatus(message.id, Message.STATUS_FAILED)
                 }
             } finally {
                 // Always remove from tracking when done
                 retryLock.withLock {
                     messagesBeingRetried.remove(message.id)
                 }
             }
         }
    }

    fun sendImage(context: android.content.Context, uri: android.net.Uri, recipientOnion: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Read raw bytes directly (NO Compression for Full Quality)
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                
                if (bytes != null) {
                    // Use simple filename or try to get from content resolver
                    val filename = "img_${System.currentTimeMillis()}.jpg" // Could improve to get real name
                    val path = repository.saveIncomingMedia(filename, bytes)
                    
                    val myAddress = onionAddress.value ?: "unknown"
                    
                    // 2. Create Message Entity
                    val message = Message(
                        senderOnionAddress = myAddress,
                        receiverOnionAddress = recipientOnion,
                        content = "📷 Image",
                        timestamp = System.currentTimeMillis(),
                        isMine = true,
                        isRead = false,
                        type = Message.TYPE_IMAGE,
                        mediaPath = path,
                        status = Message.STATUS_SENDING
                    )
                    val msgId = repository.sendMessage(message).toInt()
                    
                    // 3. Send over Network
                    val metadata = mapOf(
                        "sender" to myAddress,
                        "filename" to filename,
                        "timestamp" to message.timestamp.toString()
                    )
                    
                    // Sign the payload
                    val gson = com.google.gson.Gson()
                    val metadataBytes = gson.toJson(metadata).toByteArray(Charsets.UTF_8)
                    val signature = try {
                        (context.applicationContext as com.example.anomess.AnomessApp).let { app ->
                            val securityManager = com.example.anomess.security.SecurityManager(app)
                            securityManager.signer.sign(metadataBytes + bytes)
                        }
                    } catch (e: Exception) { null }
                    
                    val publicKey = try {
                        (context.applicationContext as com.example.anomess.AnomessApp).let { app ->
                            val securityManager = com.example.anomess.security.SecurityManager(app)
                            securityManager.getMyPublicKey()
                        }
                    } catch (e: Exception) { null }
                    
                    val payload = com.example.anomess.network.MessagePayload(
                        type = Message.TYPE_IMAGE,
                        metadata = metadata,
                        data = bytes,
                        signature = signature,
                        senderPublicKey = publicKey,
                        rawMetadata = metadataBytes
                    )
                    
                    val sent = connectionManager.sendMessagePayload(recipientOnion, payload)
                    if (sent) {
                        repository.updateMessageStatus(msgId, Message.STATUS_SENT)
                    } else {
                        repository.updateMessageStatus(msgId, Message.STATUS_FAILED)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendFile(context: android.content.Context, uri: android.net.Uri, recipientOnion: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Get filename and mime type
                var filename = "file_${System.currentTimeMillis()}"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                     if (cursor.moveToFirst()) {
                         val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                         if (nameIndex != -1) {
                             filename = cursor.getString(nameIndex)
                         }
                     }
                }

                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                if (bytes != null) {
                    val path = repository.saveIncomingMedia(filename, bytes)
                    val myAddress = onionAddress.value ?: "unknown"

                    // Message Entity
                    val message = Message(
                        senderOnionAddress = myAddress,
                        receiverOnionAddress = recipientOnion,
                        content = "📎 $filename", // Show filename in content
                        timestamp = System.currentTimeMillis(),
                        isMine = true,
                        isRead = false,
                        type = Message.TYPE_FILE,
                        mediaPath = path,
                        status = Message.STATUS_SENDING
                    )
                    val msgId = repository.sendMessage(message).toInt()

                    // Network Payload
                    val metadata = mapOf(
                        "sender" to myAddress,
                        "filename" to filename, // Critical for receiver to save correctly
                        "timestamp" to message.timestamp.toString()
                    )

                    // Sign
                    val gson = com.google.gson.Gson()
                    val metadataBytes = gson.toJson(metadata).toByteArray(Charsets.UTF_8)
                    val signature = try {
                        (context.applicationContext as com.example.anomess.AnomessApp).let { app ->
                            val securityManager = com.example.anomess.security.SecurityManager(app)
                            securityManager.signer.sign(metadataBytes + bytes)
                        }
                    } catch (e: Exception) { null }
                    
                    val publicKey = try {
                        (context.applicationContext as com.example.anomess.AnomessApp).let { app ->
                            val securityManager = com.example.anomess.security.SecurityManager(app)
                            securityManager.getMyPublicKey()
                        }
                    } catch (e: Exception) { null }

                    val payload = com.example.anomess.network.MessagePayload(
                        type = Message.TYPE_FILE, // 3
                        metadata = metadata,
                        data = bytes,
                        signature = signature,
                        senderPublicKey = publicKey,
                        rawMetadata = metadataBytes
                    )

                    val sent = connectionManager.sendMessagePayload(recipientOnion, payload)
                    if (sent) {
                        repository.updateMessageStatus(msgId, Message.STATUS_SENT)
                    } else {
                        repository.updateMessageStatus(msgId, Message.STATUS_FAILED)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveMediaToGallery(context: android.content.Context, path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = java.io.File(path)
                if (!file.exists()) return@launch

                // Use simple mime type detection or default
                val ext = file.extension
                val mimeType = when(ext.lowercase()) {
                   "jpg", "jpeg" -> "image/jpeg"
                   "png" -> "image/png"
                   "mp4" -> "video/mp4"
                   "mkv" -> "video/x-matroska"
                   "webm" -> "video/webm"
                   else -> "image/jpeg" 
                }
                
                // CRITICAL: Choose correct table based on type
                val contentUri = if (mimeType.startsWith("video/")) {
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "Anomess_${System.currentTimeMillis()}.$ext")
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        // Videos might need to go to Movies if STRICT
                        val dir = if (mimeType.startsWith("video/")) android.os.Environment.DIRECTORY_MOVIES else android.os.Environment.DIRECTORY_PICTURES
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "$dir/Anomes")
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(contentUri, values)

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        // CRITICAL: Read decrypted stream!
                        repository.getDecryptedInputStream(path)?.use { input ->
                            input.copyTo(out)
                        }
                    }

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        values.clear()
                        values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                    }
                    
                    launch(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Saved to Gallery", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                     android.widget.Toast.makeText(context, "Error saving: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    suspend fun prepareFileForViewing(context: android.content.Context, path: String): android.net.Uri? {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val file = java.io.File(path)
                if (!file.exists()) return@withContext null
                
                // Keep extension
                val ext = file.extension
                val tempFile = java.io.File(context.cacheDir, "view_${file.name}")
                
                // Decrypt to temp file
                repository.getDecryptedInputStream(path)?.use { input ->
                    java.io.FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Return URI
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    tempFile
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }



    // Audio Logic
    private var audioRecorder: com.example.anomess.media.AudioRecorder? = null
    private val audioPlayer by lazy { 
        com.example.anomess.media.AudioPlayer(
             com.example.anomess.AnomessApp.instance, 
             com.example.anomess.security.MediaCrypter(com.example.anomess.AnomessApp.instance)
        ) 
    }
    
    // Track currently playing message ID to update UI state
    private val _playingMessageId = MutableStateFlow<Int?>(null)
    val playingMessageId: StateFlow<Int?> = _playingMessageId.asStateFlow()

    fun playAudio(messageId: Int, path: String) {
        // If clicking same message, toggle stop
        if (_playingMessageId.value == messageId) {
            stopAudio()
            return
        }
        
        _playingMessageId.value = messageId
        audioPlayer.play(path) {
            _playingMessageId.value = null
        }
    }

    fun stopAudio() {
        audioPlayer.stop()
        _playingMessageId.value = null
    }

    private var currentAudioFile: java.io.File? = null
    
    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
    }
    
    fun startRecording(context: android.content.Context) {
        if (audioRecorder == null) {
            audioRecorder = com.example.anomess.media.AudioRecorder(context)
        }
        val file = java.io.File(context.filesDir, "audio_temp.m4a")
        currentAudioFile = file
        audioRecorder?.start(file)
    }

    fun stopRecording(recipientOnion: String, autoSend: Boolean = true): java.io.File? {
        audioRecorder?.stop()
        val file = currentAudioFile
        if (file != null && file.exists()) {
            if (autoSend) {
                sendVoiceMessage(file, recipientOnion)
            }
            return file
        }
        return null
    }
    
    fun sendVoiceMessage(file: java.io.File, recipientOnion: String) {
        sendAudio(file, recipientOnion)
    }
    
    private fun sendAudio(file: java.io.File, recipientOnion: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Save locally (move temp to media dir)
                val bytes = file.readBytes()
                val filename = "voice_${System.currentTimeMillis()}.m4a"
                val path = repository.saveIncomingMedia(filename, bytes) // Reusing save incoming for simplicity
                
                val myAddress = onionAddress.value ?: "unknown"
                
                val message = Message(
                    senderOnionAddress = myAddress,
                    receiverOnionAddress = recipientOnion,
                    content = "🎤 Voice Message",
                    timestamp = System.currentTimeMillis(),
                    isMine = true,
                    isRead = false,
                    type = Message.TYPE_AUDIO,
                    mediaPath = path,
                    status = Message.STATUS_SENDING
                )
                val msgId = repository.sendMessage(message).toInt()
                
                val metadata = mapOf(
                    "sender" to myAddress,
                    "filename" to filename,
                    "timestamp" to message.timestamp.toString()
                )
                
                // Sign the payload
                val gson = com.google.gson.Gson()
                val metadataBytes = gson.toJson(metadata).toByteArray(Charsets.UTF_8)
                val signature = try {
                    val app = com.example.anomess.AnomessApp.instance
                    val securityManager = com.example.anomess.security.SecurityManager(app)
                    securityManager.signer.sign(metadataBytes + bytes)
                } catch (e: Exception) { null }
                
                val publicKey = try {
                    val app = com.example.anomess.AnomessApp.instance
                    val securityManager = com.example.anomess.security.SecurityManager(app)
                    securityManager.getMyPublicKey()
                } catch (e: Exception) { null }
                
                val payload = com.example.anomess.network.MessagePayload(
                    type = Message.TYPE_AUDIO,
                    metadata = metadata,
                    data = bytes,
                    signature = signature,
                    senderPublicKey = publicKey,
                    rawMetadata = metadataBytes
                )
                
                val sent = connectionManager.sendMessagePayload(recipientOnion, payload)
                if (sent) {
                    repository.updateMessageStatus(msgId, Message.STATUS_SENT)
                } else {
                    repository.updateMessageStatus(msgId, Message.STATUS_FAILED)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearChat(contactAddress: String) {
        viewModelScope.launch {
            repository.clearConversation(contactAddress)
        }
    }
    fun deleteMessages(ids: List<Int>) {
        viewModelScope.launch {
            repository.deleteMessages(ids)
        }
    }
}

class ChatViewModelFactory(private val app: AnomessApp) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(app.torManager, app.p2pConnectionManager, app.repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
