package com.nonmessenger.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey
    val id: String,
    val name: String,
    val contactCode: List<String>,
    val publicKey: String,
    val status: String = "offline",
    val lastSeen: Long = 0L,
    val isVerified: Boolean = false,
    val deviceId: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getDisplayName(): String {
        return if (name.isNotEmpty()) name else id.take(8)
    }
    
    fun getContactCodeString(): String {
        return contactCode.joinToString(" ")
    }
    
    fun isOnline(): Boolean {
        return status == "online"
    }
}
