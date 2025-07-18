package com.nonmessenger.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey
    val id: String = "user_profile",
    val contactCode: List<String>,
    val secretWords: List<String>,
    val publicKey: String,
    val privateKey: String,
    val deviceId: String,
    val displayName: String = "Me",
    val status: String = "online",
    val customMessage: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getFullContactCode(): List<String> {
        return contactCode + secretWords
    }
    
    fun getPublicContactString(): String {
        return contactCode.joinToString(" ")
    }
}
