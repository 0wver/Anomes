package com.example.anomess.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

object BinaryProtocol {
    private const val VERSION: Int = 2
    private val gson = Gson()

    // Header Structure V2:
    // VERSION (1 byte)
    // TYPE (1 byte)
    // METADATA_LEN (4 bytes Int)
    // PAYLOAD_LEN (8 bytes Long)
    // SIG_LEN (4 bytes Int)
    // KEY_LEN (4 bytes Int)
    // [Metadata]
    // [Payload]
    // [Signature]
    // [PublicKey]

    fun write(output: OutputStream, payload: MessagePayload) {
        val dos = DataOutputStream(output)
        
        // Use existing rawMetadata if available (for exact signature match), else serialize
        val metadataBytes = payload.rawMetadata ?: gson.toJson(payload.metadata).toByteArray(Charsets.UTF_8)
        
        dos.writeByte(VERSION)
        dos.writeByte(payload.type)
        dos.writeInt(metadataBytes.size)
        dos.writeLong(payload.data.size.toLong())
        
        val sigBytes = payload.signature ?: ByteArray(0)
        val keyBytes = payload.senderPublicKey ?: ByteArray(0)
        
        dos.writeInt(sigBytes.size)
        dos.writeInt(keyBytes.size)
        
        dos.write(metadataBytes)
        dos.write(payload.data)
        dos.write(sigBytes)
        dos.write(keyBytes)
        
        dos.flush()
    }

    fun read(input: InputStream): MessagePayload {
        val dis = DataInputStream(input)
        
        val version = dis.readByte().toInt()
        
        if (version != 1 && version != 2) {
             throw IllegalArgumentException("Unsupported protocol version: $version")
        }
        
        val type = dis.readByte().toInt()
        val metadataLen = dis.readInt()
        val payloadLen = dis.readLong()
        
        var sigLen = 0
        var keyLen = 0
        
        if (version >= 2) {
            sigLen = dis.readInt()
            keyLen = dis.readInt()
        }
        
        // Read Metadata
        // SECURITY FIX VULN-001: Limit metadata size to 64KB to prevent OOM DoS attacks
        if (metadataLen < 0 || metadataLen > 64 * 1024) {
             throw IllegalArgumentException("Metadata invalid size: $metadataLen bytes (Max 64KB)")
        }

        val metadataBytes = ByteArray(metadataLen)
        dis.readFully(metadataBytes)
        val metadataJson = String(metadataBytes, Charsets.UTF_8)
        
        val mapType = object : TypeToken<Map<String, String>>() {}.type
        val metadata: Map<String, String> = gson.fromJson(metadataJson, mapType)
        
        // Read Payload
        // DoS Mitigation: Reduce max payload to 100MB to prevent OOM
        // Also check for negative lengths which could cause issues
        if (payloadLen < 0 || payloadLen > 100 * 1024 * 1024) { 
            throw IllegalArgumentException("Payload invalid size: $payloadLen bytes (Max 100MB)")
        }
        
        val data = ByteArray(payloadLen.toInt())
        dis.readFully(data)
        
        var signature: ByteArray? = null
        var publicKey: ByteArray? = null
        
        if (version >= 2) {
            if (sigLen > 0) {
                signature = ByteArray(sigLen)
                dis.readFully(signature)
            }
            if (keyLen > 0) {
                publicKey = ByteArray(keyLen)
                dis.readFully(publicKey)
            }
        }
        
        return MessagePayload(type, metadata, data, signature, publicKey, metadataBytes)
    }
}
