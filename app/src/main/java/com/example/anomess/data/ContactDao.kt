package com.example.anomess.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE onionAddress = :address LIMIT 1")
    suspend fun getContact(address: String): Contact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact)

    @Delete
    suspend fun deleteContact(contact: Contact)
    
    @Query("UPDATE contacts SET publicKey = :publicKey WHERE onionAddress = :address")
    suspend fun updatePublicKey(address: String, publicKey: String)

    @Query("UPDATE contacts SET name = :newName WHERE onionAddress = :address")
    suspend fun updateContactName(address: String, newName: String)
}
