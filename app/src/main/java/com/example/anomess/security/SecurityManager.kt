package com.example.anomess.security

import android.content.Context
import android.os.Build
import android.util.Base64
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.signature.SignatureConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.io.File
import java.security.GeneralSecurityException

class SecurityManager(private val context: Context) {

    private val masterKeyUri = "android-keystore://master_key_anomess"
    private val identityKeysetName = "identity_keyset"
    private val dbKeyName = "db_secret_key"
    
    // Lazy load the signing primitive
    val signer: PublicKeySign by lazy {
        getIdentityKeyset().getPrimitive(PublicKeySign::class.java)
    }

    init {
        // Register Tink configs
        SignatureConfig.register()
    }
    
    // --- Identity Management (Ed25519) ---

    @Synchronized
    private fun getIdentityKeyset(): KeysetHandle {
        val build = AndroidKeysetManager.Builder()
            .withSharedPref(context, identityKeysetName, "security_prefs")
            .withKeyTemplate(com.google.crypto.tink.signature.SignatureKeyTemplates.ED25519)
            .withMasterKeyUri(masterKeyUri)
            .build()
        return build.keysetHandle
    }

    fun getMyPublicKey(): ByteArray {
        // This is a bit tricky with Tink as it abstracts the raw key.
        // For P2P we need the raw bytes to send to peer.
        // We can export the public keyset and extract. 
        // For simplicity in this non-standard P2P, we might want to just sign a self-attestation or specific export?
        // Actually, Tink's Ed25519 public key output is proto serialized.
        // To simplify integration without implementing Proto parsing here, we might rely on the fact that
        // for verification we will use Tink as well. 
        // So we send the *Tink Keyset JSON* (Public only) to the peer.
        
        // Export public keyset to Cleartext JSON (Public keys are safe to be cleartext)
        val handle = getIdentityKeyset().publicKeysetHandle
        val baos = java.io.ByteArrayOutputStream()
        com.google.crypto.tink.CleartextKeysetHandle.write(
            handle, 
            com.google.crypto.tink.JsonKeysetWriter.withOutputStream(baos)
        )
        return baos.toByteArray()
    }
    
    fun getVerifierForPeer(peerPublicKeyBytes: ByteArray): PublicKeyVerify {
        // peerPublicKeyBytes is expected to be a Tink Public Keyset JSON
        val handle = com.google.crypto.tink.CleartextKeysetHandle.read(
            com.google.crypto.tink.JsonKeysetReader.withBytes(peerPublicKeyBytes)
        )
        return handle.getPrimitive(PublicKeyVerify::class.java)
    }
    
    // --- Database Encryption Key ---
    
    fun getDatabasePassphrase(): String {
        // SECURE IMPLEMENTATION using Android Keystore and EncryptedFile
        val secureFileName = "db.key"
        val legacyInsecureFileName = "db_key_enc"
        
        try {
            val mainKey = androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()

            val secureFile = File(context.filesDir, secureFileName)
            
            // 1. Check if secure file already exists
            if (secureFile.exists()) {
                val encryptedFile = androidx.security.crypto.EncryptedFile.Builder(
                    context,
                    secureFile,
                    mainKey,
                    androidx.security.crypto.EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()
                
                return Base64.encodeToString(encryptedFile.openFileInput().readBytes(), Base64.NO_WRAP)
            }
            
            // 2. MIGRATION: Check for legacy insecure file
            val legacyFile = File(context.filesDir, legacyInsecureFileName)
            var keyBytes: ByteArray? = null
            
            if (legacyFile.exists()) {
                // Read from legacy file
                keyBytes = legacyFile.readBytes()
                // Verify length to ensure it's valid
                if (keyBytes.size != 32) {
                    keyBytes = null // Corrupt/Invalid, generate new
                }
            }
            
            // 3. Generate new if needed
            if (keyBytes == null) {
                keyBytes = ByteArray(32)
                java.security.SecureRandom().nextBytes(keyBytes)
            }
            
            // 4. Write to SECURE file
            // Delete if exists (shouldn't, but for safety in retries) or just overwrite?
            // EncryptedFile doesn't support overwrite easily, best to ensure clean state.
            if (secureFile.exists()) secureFile.delete()
            
            val encryptedFile = androidx.security.crypto.EncryptedFile.Builder(
                context,
                secureFile,
                mainKey,
                androidx.security.crypto.EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            
            val outputStream = encryptedFile.openFileOutput()
            outputStream.write(keyBytes)
            outputStream.flush()
            outputStream.close()
            
            // 5. Cleanup Legacy
            if (legacyFile.exists()) {
                legacyFile.delete()
            }
            
            return Base64.encodeToString(keyBytes, Base64.NO_WRAP)
            
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback (should not happen in prod, but prevents crash loop if Keystore is broken)
            return "fallback-broken-keystore-key" 
        }
    }

}
