package com.example.anomess.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.anomess.AnomessApp
import com.example.anomess.MainActivity
import com.example.anomess.R
import com.example.anomess.network.TorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TorService : Service() {

    private val torManager by lazy { (application as AnomessApp).torManager }
    private val connectionManager by lazy { (application as AnomessApp).p2pConnectionManager }
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "TorServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification("Initializing Tor..."))
                startTor()
            }
            ACTION_STOP -> {
                stopTor()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startTor() {
        scope.launch {
            updateNotification("Starting Tor...")
            val success = torManager.startTor()
            if (success) {
                val address = torManager.getOnionHostname()
                updateNotification("Tor Connected: ${address?.take(6)}...")
                connectionManager.startListening()
                
                // Register Network Callback to handle reconnects
                registerNetworkCallback()
            } else {
                updateNotification("Tor Connection Failed")
            }
        }
    }

    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private var wasNetworkLost = false

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val builder = android.net.NetworkRequest.Builder()
        
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                scope.launch {
                    if (wasNetworkLost) {
                        // SOFT RECOVERY: Don't restart Tor, let it naturally reconnect
                        com.example.anomess.network.TorManager.log("Network recovered. Using SOFT RECOVERY (no restart)...")
                        updateNotification("Network Back - Recovering...")
                        
                        // Step 1: Wait 15 seconds for Tor to reconnect to the network
                        com.example.anomess.network.TorManager.log("Waiting 15s for Tor to reconnect...")
                        kotlinx.coroutines.delay(15_000)
                        
                        // Step 2: Request fresh circuits
                        com.example.anomess.network.TorManager.log("Requesting new Tor circuits...")
                        torManager.requestNewCircuit()
                        
                        // Step 3: Wait for circuits to build and services to stabilize
                        com.example.anomess.network.TorManager.log("Waiting 45s for hidden services...")
                        kotlinx.coroutines.delay(45_000)
                        
                        // Step 4: Update status and retry
                        val address = torManager.getOnionHostname()
                        updateNotification("Tor Ready: ${address?.take(6)}...")
                        com.example.anomess.network.TorManager.log("Recovery complete. Retrying pending messages...")
                        retryAllPendingMessages()
                        
                        wasNetworkLost = false
                    } else {
                        com.example.anomess.network.TorManager.log("Network available.")
                        val address = torManager.getOnionHostname()
                        if (address != null) {
                            updateNotification("Tor Connected: ${address.take(6)}...")
                        }
                    }
                }
            }
            
            override fun onLost(network: android.net.Network) {
                wasNetworkLost = true
                updateNotification("Network Lost - Waiting...")
                com.example.anomess.network.TorManager.log("Network lost. Will recover when back online.")
            }
        }
        
        connectivityManager.registerNetworkCallback(builder.build(), callback)
        networkCallback = callback
    }

    private fun stopTor() {
        // Unregister callback
        networkCallback?.let {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            connectivityManager.unregisterNetworkCallback(it)
            networkCallback = null
        }
        
        connectionManager.stopListening()
        torManager.stopTor()
    }
    
    private fun retryAllPendingMessages() {
        scope.launch {
            try {
                val repository = (application as AnomessApp).repository
                val pending = repository.getPendingMessages()
                if (pending.isNotEmpty()) {
                    com.example.anomess.network.TorManager.log("Found ${pending.size} failed messages to retry...")
                    for (msg in pending) {
                        // Mark as sending
                        repository.updateMessageStatus(msg.id, com.example.anomess.data.Message.STATUS_SENDING)
                        
                        // Try to resend
                        val sent = if (msg.type == com.example.anomess.data.Message.TYPE_TEXT) {
                            connectionManager.sendMessage(msg.receiverOnionAddress, msg.content)
                        } else {
                            // For media, we'd need to read file again - simplified for text
                            false 
                        }
                        
                        if (sent) {
                            repository.updateMessageStatus(msg.id, com.example.anomess.data.Message.STATUS_SENT)
                            com.example.anomess.network.TorManager.log("Successfully resent message ${msg.id}")
                        } else {
                            repository.updateMessageStatus(msg.id, com.example.anomess.data.Message.STATUS_FAILED)
                        }
                        
                        // Small delay between messages to not overwhelm Tor
                        kotlinx.coroutines.delay(500)
                    }
                }
            } catch (e: Exception) {
                com.example.anomess.network.TorManager.error("Error retrying pending messages", e)
            }
        }
    }

    override fun onDestroy() {
        stopTor()
        super.onDestroy()
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Anomess Service")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher) // Fallback icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Minimal disturbance
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tor Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
