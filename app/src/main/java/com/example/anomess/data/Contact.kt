package com.example.anomess.data

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey val onionAddress: String,
    val name: String,
    val publicKey: String? = null // Base64 encoded Ed25519 Public Key
)
