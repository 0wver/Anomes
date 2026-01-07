package com.example.anomess

import android.app.Application
import com.example.anomess.data.AppDatabase
import com.example.anomess.data.MessageRepository
import com.example.anomess.network.P2PConnectionManager
import com.example.anomess.network.TorManager

class AnomessApp : Application(), coil.ImageLoaderFactory {

    lateinit var torManager: TorManager
    lateinit var database: AppDatabase
    lateinit var repository: MessageRepository
    lateinit var p2pConnectionManager: P2PConnectionManager
    lateinit var securityManager: com.example.anomess.security.SecurityManager
    
    companion object {
        lateinit var instance: AnomessApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize Security
        securityManager = com.example.anomess.security.SecurityManager(this)
        
        torManager = TorManager(this)
        
        // Initialize Encrypted Database
        val dbKey = securityManager.getDatabasePassphrase()
        database = AppDatabase.getDatabase(this, dbKey)
        
        repository = MessageRepository(this, database.messageDao(), database.contactDao())
        
        // Pass SecurityManager to P2P for Signing/Verification
        p2pConnectionManager = P2PConnectionManager(torManager, repository, securityManager, this)
        
        // Start Tor Service to keep app alive
        val serviceIntent = android.content.Intent(this, com.example.anomess.service.TorService::class.java)
        serviceIntent.action = com.example.anomess.service.TorService.ACTION_START
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun newImageLoader(): coil.ImageLoader {
        return coil.ImageLoader.Builder(this)
            .components {
                // Register our encrypted file fetcher. Priority is important, add it first.
                add(com.example.anomess.media.EncryptedFileFetcher.Factory(this@AnomessApp, com.example.anomess.security.MediaCrypter(this@AnomessApp)))
            }
            .build()
    }
}
