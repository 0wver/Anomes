package com.example.anomess.media

import android.content.Context
import android.media.MediaPlayer
import com.example.anomess.security.MediaCrypter
import java.io.File
import java.io.FileOutputStream

class AudioPlayer(
    private val context: Context,
    private val crypter: MediaCrypter
) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentTempFile: File? = null
    var onCompletion: (() -> Unit)? = null

    fun play(encryptedPath: String, onComplete: () -> Unit = {}) {
        stop() // Stop any previous playback
        this.onCompletion = onComplete

        try {
            val encryptedFile = File(encryptedPath)
            if (!encryptedFile.exists()) return

            // 1. Decrypt to Temp File
            // We use a unique name to avoid conflicts
            currentTempFile = File.createTempFile("play_", ".m4a", context.cacheDir)
            
            val inputStream = try {
                 crypter.getInputStream(encryptedFile)
            } catch (e: Exception) {
                // Fallback for unencrypted legacy files
                java.io.FileInputStream(encryptedFile)
            }
            
            val outputStream = FileOutputStream(currentTempFile)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            // 2. Play Temp File
            mediaPlayer = MediaPlayer().apply {
                setDataSource(currentTempFile!!.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    cleanup()
                    onComplete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            cleanup()
            onComplete()
        }
    }

    fun stop() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.stop()
        }
        cleanup()
    }

    private fun cleanup() {
        mediaPlayer?.release()
        mediaPlayer = null
        try {
            if (currentTempFile?.exists() == true) {
                currentTempFile?.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        currentTempFile = null
    }
}
