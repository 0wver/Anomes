package com.example.anomess.network

import android.util.Log
import com.example.anomess.data.Message
import com.example.anomess.data.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import kotlin.math.abs

class P2PConnectionManager(
    private val torManager: TorManager,
    private val repository: MessageRepository,
    private val securityManager: com.example.anomess.security.SecurityManager,
    private val context: android.content.Context
) {
    private var serverSocket: ServerSocket? = null
    private var isListening = false
    
    // Track failed destinations to trigger circuit renewal
    private val failedDestinations = mutableSetOf<String>()

    companion object {
        private const val TAG = "P2PConnectionManager"
        private const val LOCAL_PORT = 8080 // Must match TorManager local port
        private const val MAX_SEND_RETRIES = 3 // Back to 3 for better resilience
        private const val INITIAL_TIMEOUT_MS = 20000 // 20 seconds (reasonable for Tor)
        private const val RETRY_TIMEOUT_MS = 15000 // 15 seconds for retries
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val actNw = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                actNw.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> true
                actNw.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                actNw.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            val nwInfo = connectivityManager.activeNetworkInfo ?: return false
            return nwInfo.isConnected
        }
    }

    fun startListening() {
        if (isListening) return
        isListening = true
        CoroutineScope(Dispatchers.IO).launch {
            var retries = 0
            while (serverSocket == null && isListening) {
                try {
                    // SECURE BINDING: Bind only to local loopback interface
                    // This prevents other devices on the LAN from connecting directly to this port.
                    // Only the local Tor process (proxying from hidden service) can connect.
                    serverSocket = ServerSocket(LOCAL_PORT, 50, InetAddress.getByName("127.0.0.1"))
                    serverSocket?.reuseAddress = true 
                    TorManager.log("Listening for P2P connections on port $LOCAL_PORT")
                    
                    while (isListening) {
                        try {
                            val clientSocket = serverSocket?.accept()
                            // Set timeout to prevent hanging connections (DoS mitigation)
                            clientSocket?.soTimeout = 30_000 // 30 seconds
                            clientSocket?.let { handleIncomingConnection(it) }
                        } catch (e: Exception) {
                            if (isListening) {
                                TorManager.error("Error accepting connection", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    TorManager.error("Error binding server socket (Attempt ${retries + 1}): ${e.message}")
                    retries++
                    try {
                        kotlinx.coroutines.delay(2000) // Wait 2 seconds before retrying
                    } catch (ie: InterruptedException) {
                        // ignore
                    }
                    if (retries > 10) {
                        TorManager.error("Failed to bind server socket after 10 attempts. Incoming messages will fail.")
                        isListening = false
                        break
                    }
                }
            }
        }
    }

    private fun handleIncomingConnection(socket: Socket) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TorManager.log("Accepted connection from ${socket.remoteSocketAddress}")
                
                // Read using BinaryProtocol
                val payload = BinaryProtocol.read(socket.getInputStream())
                
                TorManager.log("Received payload type: ${payload.type}, size: ${payload.data.size}")
                
                // --- SECURITY VERIFICATION ---
                if (!verifyMessage(payload)) {
                    TorManager.error("Security verification failed for message from ${payload.metadata["sender"]}")
                    socket.close()
                    return@launch
                }
                
                processIncomingMessage(payload)
                
                socket.close()
            } catch (e: Exception) {
                TorManager.error("Error handling incoming connection", e)
            }
        }
    }
    
    private suspend fun verifyMessage(payload: MessagePayload): Boolean {
        try {
            val senderOnion = payload.metadata["sender"] ?: return false
            val signature = payload.signature ?: return false
            val publicKey = payload.senderPublicKey ?: return false
            val rawMetadata = payload.rawMetadata ?: return false // Must have raw bytes for verification
            
            // 1. Verify Signature
            val verifier = securityManager.getVerifierForPeer(publicKey)
            val dataToVerify = rawMetadata + payload.data
            try {
                verifier.verify(signature, dataToVerify)
            } catch (e: Exception) {
                TorManager.error("Signature invalid for $senderOnion", e)
                return false
            }
            
            // 2. Timestamp Verification (Anti-Replay)
            // Enforce 5-minute window
            val msgTime = payload.metadata["timestamp"]?.toLongOrNull() ?: 0L
            val currentTime = System.currentTimeMillis()
            if (abs(currentTime - msgTime) > 5 * 60 * 1000) { // 5 minutes
                 TorManager.error("Security: Message timestamp rejected (Replay Attack Protection). Delta: ${abs(currentTime - msgTime)}ms")
                 return false
            }
            
            // 3. TOFU (Trust On First Use) / Key Pinning
            val existingContact = repository.getContact(senderOnion)
            if (existingContact != null) {
                if (existingContact.publicKey != null) {
                    val encodedKey = android.util.Base64.encodeToString(publicKey, android.util.Base64.NO_WRAP)
                    if (existingContact.publicKey != encodedKey) {
                        TorManager.error("SECURITY ALERT: Man-in-the-Middle detected! Key mismatch for $senderOnion. stored=${existingContact.publicKey}, received=$encodedKey")
                        return false
                    }
                } else {
                    // First time seeing a key for this contact (Migration)
                    TorManager.log("Migrating contact $senderOnion to secure key.")
                    val encodedKey = android.util.Base64.encodeToString(publicKey, android.util.Base64.NO_WRAP)
                    repository.updateContactKey(senderOnion, encodedKey)
                }
            } else {
                 TorManager.log("New contact $senderOnion. Trusting key (TOFU).")
            }
            
            return true
        } catch (e: Exception) {
            TorManager.error("Verification error", e)
            return false
        }
    }

    private suspend fun processIncomingMessage(payload: MessagePayload) {
        try {
            val sender = payload.metadata["sender"] ?: "unknown"
            val senderTs = payload.metadata["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()
            
            // Extract Public Key for saving contact securely
            val publicKeyBytes = payload.senderPublicKey
            val publicKeyStr = publicKeyBytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
            
            if (payload.type == Message.TYPE_TEXT) {
                val content = String(payload.data, Charsets.UTF_8)
                
                if (content.startsWith("CMD:READ:")) {
                    // Handle Read Receipt
                    val timestampStr = content.removePrefix("CMD:READ:")
                    val timestamp = timestampStr.toLongOrNull()
                    if (timestamp != null) {
                        repository.markMessageAsReadByPeer(timestamp)
                        TorManager.log("Received READ receipt for message at $timestamp")
                    }
                    return
                }

                // Regular Text Message
                val myAddress = torManager.getOnionHostname() ?: "unknown"
                val replyContent = payload.metadata["replyToContent"] // Extract reply context
                
                // Resolve Reply ID if exists
                var replyToId: Int? = null
                val replyToTimestampStr = payload.metadata["replyToTimestamp"]
                val replyToSender = payload.metadata["replyToSender"]
                
                if (replyToTimestampStr != null && replyToSender != null) {
                    val ts = replyToTimestampStr.toLongOrNull()
                    if (ts != null) {
                        replyToId = repository.findMessageId(replyToSender, ts)
                        if (replyToId == null) {
                            TorManager.log("Could not find original message for reply from $sender (ts=$ts)")
                        }
                    }
                }
                
                val message = Message(
                    senderOnionAddress = sender,
                    receiverOnionAddress = myAddress,
                    content = content,
                    timestamp = System.currentTimeMillis(), // Our local time for ordering
                    isMine = false,
                    type = Message.TYPE_TEXT,
                    senderTimestamp = senderTs, // Store for receipt
                    replyToContent = replyContent, // Include reply context if present
                    replyToMessageId = replyToId
                )
                repository.sendMessage(message, publicKeyStr) // Updated to save key
                TorManager.log("Message saved from $sender")
            } else if (payload.type == Message.TYPE_IMAGE) {
                // Handle Image
                val filename = payload.metadata["filename"] ?: "image_${System.currentTimeMillis()}.jpg"
                val path = repository.saveIncomingMedia(filename, payload.data)
                
                val myAddress = torManager.getOnionHostname() ?: "unknown"
                val message = Message(
                    senderOnionAddress = sender,
                    receiverOnionAddress = myAddress,
                    content = "📷 Image",
                    timestamp = System.currentTimeMillis(),
                    isMine = false,
                    type = Message.TYPE_IMAGE,
                    mediaPath = path,
                    senderTimestamp = senderTs
                )
                repository.sendMessage(message, publicKeyStr)
                TorManager.log("Image received and saved from $sender at $path")
            } else if (payload.type == Message.TYPE_AUDIO) {
                 // Handle Audio
                val filename = payload.metadata["filename"] ?: "voice_${System.currentTimeMillis()}.m4a"
                val path = repository.saveIncomingMedia(filename, payload.data)
                
                val myAddress = torManager.getOnionHostname() ?: "unknown"
                val message = Message(
                    senderOnionAddress = sender,
                    receiverOnionAddress = myAddress,
                    content = "🎤 Voice Message",
                    timestamp = System.currentTimeMillis(),
                    isMine = false,
                    type = Message.TYPE_AUDIO,
                    mediaPath = path,
                    senderTimestamp = senderTs
                )
                repository.sendMessage(message, publicKeyStr)

                TorManager.log("Voice message received and saved from $sender at $path")
            } else if (payload.type == 3) { // TYPE_FILE
                 // Handle Generic File
                val filename = payload.metadata["filename"] ?: "file_${System.currentTimeMillis()}"
                val path = repository.saveIncomingMedia(filename, payload.data)
                
                val myAddress = torManager.getOnionHostname() ?: "unknown"
                val message = Message(
                    senderOnionAddress = sender,
                    receiverOnionAddress = myAddress,
                    content = "📎 $filename", // Replicate content format
                    timestamp = System.currentTimeMillis(),
                    isMine = false,
                    type = 3, // TYPE_FILE
                    mediaPath = path,
                    senderTimestamp = senderTs
                )
                repository.sendMessage(message, publicKeyStr)
                TorManager.log("File received and saved from $sender at $path")
            } else {
                TorManager.log("Received non-text message type: ${payload.type} (Not fully supported yet)")
            }
        } catch (e: Exception) {
            TorManager.error("Error processing message", e)
        }
    }
    
    suspend fun sendReadReceipt(recipientOnion: String, messageTimestamp: Long) {
        sendMessage(recipientOnion, "CMD:READ:$messageTimestamp")
    }

    suspend fun sendMessage(recipientOnion: String, content: String, replyToContent: String? = null, replyToTimestamp: Long? = null, replyToSender: String? = null): Boolean {
        val myAddress = torManager.getOnionHostname() ?: "unknown"
        val metadata = mutableMapOf(
            "sender" to myAddress,
            "timestamp" to System.currentTimeMillis().toString()
        )
        // Add reply content if this is a reply
        if (replyToContent != null) {
            metadata["replyToContent"] = replyToContent
        }
        if (replyToTimestamp != null) {
            metadata["replyToTimestamp"] = replyToTimestamp.toString()
        }
        if (replyToSender != null) {
            metadata["replyToSender"] = replyToSender
        }
        
        // Serialize metadata deterministically for signing
        val gson = com.google.gson.Gson()
        val metadataBytes = gson.toJson(metadata).toByteArray(Charsets.UTF_8)
        
        val data = content.toByteArray(Charsets.UTF_8)
        
        // --- SIGNING ---
        val signature = try {
            securityManager.signer.sign(metadataBytes + data)
        } catch (e: Exception) {
            TorManager.error("Signing failed", e)
            return false
        }
        val myKey = securityManager.getMyPublicKey()
        
        val payload = MessagePayload(Message.TYPE_TEXT, metadata, data, signature, myKey, metadataBytes)
        
        return sendMessagePayload(recipientOnion, payload)
    }

    suspend fun sendMessagePayload(recipientOnion: String, payload: MessagePayload): Boolean {
        return withContext(Dispatchers.IO) {
            // 0a. Wait if Tor is restarting - don't try to send during restart!
            var waitCount = 0
            while (torManager.isRestarting && waitCount < 60) { // Max 60 seconds wait
                TorManager.log("Tor is restarting, waiting before send... (${waitCount}s)")
                kotlinx.coroutines.delay(1000)
                waitCount++
            }
            
            // 0b. Fail-Fast: Check Internet Connection
            if (!isNetworkAvailable()) {
                TorManager.error("No internet connection available. Aborting send to $recipientOnion")
                return@withContext false
            }

            // Clean the address once
            var cleanAddress = recipientOnion.replace("\\s".toRegex(), "")
            if (cleanAddress.endsWith(".onion.onion")) {
                cleanAddress = cleanAddress.removeSuffix(".onion")
            }
            if (!cleanAddress.endsWith(".onion")) {
                cleanAddress = "$cleanAddress.onion"
            }
            
            // Check if this destination previously failed - request new circuit first
            if (failedDestinations.contains(cleanAddress)) {
                TorManager.log("Destination $cleanAddress previously failed, requesting new circuit...")
                torManager.requestNewCircuit()
                failedDestinations.remove(cleanAddress)
                kotlinx.coroutines.delay(1000) // Reduced wait
            }
            
            var lastException: Exception? = null
            
            for (attempt in 1..MAX_SEND_RETRIES) {
                // Check network again before each attempt (user might have toggle wifi)
                if (!isNetworkAvailable()) {
                     TorManager.error("Internet lost during retry. Aborting.")
                     return@withContext false
                }
                
                try {
                    val timeoutMs = if (attempt == 1) INITIAL_TIMEOUT_MS else RETRY_TIMEOUT_MS
                    val result = attemptSend(cleanAddress, payload, timeoutMs)
                    if (result) {
                        // Success - clear any failure tracking
                        failedDestinations.remove(cleanAddress)
                        return@withContext true
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    lastException = e
                    TorManager.log("Attempt $attempt/$MAX_SEND_RETRIES timed out for $cleanAddress")
                    
                    if (attempt < MAX_SEND_RETRIES) {
                        // Request new circuit before retry
                        TorManager.log("Requesting new Tor circuit before retry...")
                        torManager.requestNewCircuit()
                        kotlinx.coroutines.delay(2000)
                    }
                } catch (e: java.net.SocketException) {
                    lastException = e
                    val msg = e.message ?: ""
                    TorManager.log("Attempt $attempt/$MAX_SEND_RETRIES failed for $cleanAddress: $msg")
                    
                    // SOCKS failures indicate routing issues - request new circuit
                    if (msg.contains("general failure") || msg.contains("Host unreachable")) {
                        TorManager.log("SOCKS failure - requesting new circuit (no restart)")
                        torManager.requestNewCircuit()
                        // Wait a bit longer for new circuit
                        kotlinx.coroutines.delay(5000)
                    }
                    
                    if (attempt < MAX_SEND_RETRIES) {
                        kotlinx.coroutines.delay(2000)
                    }
                } catch (e: Exception) {
                    lastException = e
                    TorManager.log("Attempt $attempt/$MAX_SEND_RETRIES failed for $cleanAddress: ${e.message}")
                    
                    if (attempt < MAX_SEND_RETRIES) {
                        kotlinx.coroutines.delay(1000)
                    }
                }
            }
            
            // All retries failed - mark destination as problematic for next time
            failedDestinations.add(cleanAddress)
            TorManager.error("All $MAX_SEND_RETRIES attempts failed for $cleanAddress", lastException)
            false
        }
    }
    
    private fun attemptSend(cleanAddress: String, payload: MessagePayload, timeoutMs: Int): Boolean {
        val socksPort = torManager.getSocksPort()
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
        
        TorManager.log("Connecting to $cleanAddress via SOCKS proxy on $socksPort (timeout: ${timeoutMs}ms)")
        
        val socket = Socket(proxy)
        
        try {
            // Create UNRESOLVED socket address to force proxy resolution (Remote DNS)
            val destAddr = InetSocketAddress.createUnresolved(cleanAddress, 80)
            
            socket.connect(destAddr, timeoutMs)
            
            // Write using BinaryProtocol
            BinaryProtocol.write(socket.getOutputStream(), payload)
            
            socket.close()
            TorManager.log("Payload sent successfully to $cleanAddress")
            return true
        } catch (e: Exception) {
            try { socket.close() } catch (_: Exception) {}
            throw e
        }
    }

    fun stopListening() {
        isListening = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        }
    }
}
