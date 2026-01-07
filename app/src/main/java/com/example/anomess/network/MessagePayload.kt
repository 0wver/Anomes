package com.example.anomess.network

data class MessagePayload(
    val type: Int, // 0=Text, 1=Image, 2=Audio, 3=File
    val metadata: Map<String, String>, // JSON metadata (sender, timestamp, filename)
    val data: ByteArray, // Content bytes
    val signature: ByteArray? = null, // Ed25519 Signature
    val senderPublicKey: ByteArray? = null, // Sender's Ed25519 Public Key
    val rawMetadata: ByteArray? = null // For verification stability
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MessagePayload

        if (type != other.type) return false
        if (metadata != other.metadata) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + metadata.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
