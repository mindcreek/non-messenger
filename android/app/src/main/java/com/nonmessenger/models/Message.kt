package com.nonmessenger.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.*

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey
    val id: String,
    val contactId: String,
    val content: String,
    val isFromMe: Boolean,
    val timestamp: Long,
    val messageType: String = "text",
    val deliveryStatus: String = "sent",
    val encryptedContent: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getFormattedTime(): String {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
    
    fun getFormattedDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
    
    // For the UI, we'll use the formatted time as timestamp
    val timestampFormatted: String
        get() = getFormattedTime()
}
