package com.nonmessenger.model

import androidx.room.*
import kotlinx.serialization.Serializable
import java.util.*

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val contactCode: List<String>,
    val publicKey: String,
    val status: String = "offline",
    val lastSeen: Long = 0,
    val isVerified: Boolean = false,
    val deviceId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val contactId: String,
    val content: String,
    val isFromMe: Boolean,
    val timestamp: String,
    val messageType: String = "text",
    val deliveryStatus: String = "sent", // sent, delivered, read, failed
    val encryptedContent: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "contact_requests")
data class ContactRequest(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String,
    val publicWords: List<String>,
    val verificationMessage: String,
    val senderPublicKey: String,
    val status: String = "pending", // pending, accepted, rejected
    val receivedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: String = "user_profile",
    val contactCode: List<String>,
    val secretWords: List<String>,
    val publicKey: String,
    val privateKey: String,
    val deviceId: String,
    val displayName: String = "Me",
    val status: String = "online",
    val customMessage: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "server_nodes")
data class ServerNode(
    @PrimaryKey val url: String,
    val publicKey: String,
    val isActive: Boolean = true,
    val lastPing: Long = 0,
    val responseTime: Long = 0,
    val priority: Int = 0 // 0 = highest priority
)

@Serializable
data class QRCodeData(
    val publicKey: String,
    val deviceId: String,
    val timestamp: Long,
    val version: String = "1.0",
    val contactWords: List<String>? = null
)

@Serializable
data class EncryptedMessage(
    val encryptedMessage: String,
    val encryptedKey: String,
    val iv: String,
    val authTag: String
)

@Serializable
data class MessageEnvelope(
    val id: String,
    val recipientContactCode: String,
    val encryptedMessage: EncryptedMessage,
    val timestamp: Long,
    val ttl: Long = 86400000, // 24 hours default
    val messageType: String = "text"
)

@Serializable
data class ContactRequestMessage(
    val type: String = "CONTACT_REQUEST",
    val id: String,
    val timestamp: Long,
    val senderId: String,
    val senderName: String,
    val publicWords: List<String>,
    val verificationMessage: String,
    val senderPublicKey: String,
    val version: String = "1.0"
)

@Serializable
data class ContactResponseMessage(
    val type: String = "CONTACT_RESPONSE",
    val id: String,
    val timestamp: Long,
    val originalRequestId: String,
    val accepted: Boolean,
    val secretWords: List<String>? = null,
    val recipientPublicKey: String? = null,
    val version: String = "1.0"
)

@Serializable
data class VoiceCallMessage(
    val type: String, // VOICE_CALL_INIT, VOICE_CALL_ACCEPT, VOICE_CALL_REJECT, VOICE_CALL_END
    val id: String,
    val timestamp: Long,
    val callId: String,
    val callerId: String? = null,
    val recipientId: String? = null,
    val version: String = "1.0"
)

@Serializable
data class VoiceDataMessage(
    val type: String = "VOICE_DATA",
    val id: String,
    val timestamp: Long,
    val callId: String,
    val encryptedAudioData: String,
    val sequenceNumber: Int,
    val version: String = "1.0"
)

@Serializable
data class AwarenessMessage(
    val type: String, // USER_STATUS, TYPING_INDICATOR, LAST_SEEN, etc.
    val userId: String,
    val timestamp: Long,
    val status: String? = null,
    val customMessage: String? = null,
    val isTyping: Boolean? = null,
    val chatId: String? = null,
    val messageId: String? = null,
    val deliveryStatus: String? = null,
    val isOnline: Boolean? = null,
    val connectionQuality: Float? = null,
    val version: String = "1.0"
)

data class ChatSession(
    val contactId: String,
    val contactName: String,
    val lastMessage: String,
    val lastMessageTime: Long,
    val unreadCount: Int = 0,
    val isTyping: Boolean = false,
    val isOnline: Boolean = false
)

data class ServerStatus(
    val url: String,
    val isConnected: Boolean,
    val lastPing: Long,
    val responseTime: Long,
    val messagePoolSize: Int = 0,
    val activeSessions: Int = 0
)

// Type converters for Room database
class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return value.joinToString(",")
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return if (value.isEmpty()) emptyList() else value.split(",")
    }
}

// Enums for better type safety
enum class MessageType {
    TEXT, IMAGE, FILE, VOICE_NOTE, CONTACT_REQUEST, CONTACT_RESPONSE, VOICE_CALL_INIT, VOICE_CALL_ACCEPT, VOICE_CALL_REJECT, VOICE_CALL_END, VOICE_DATA
}

enum class DeliveryStatus {
    SENDING, SENT, DELIVERED, READ, FAILED
}

enum class ContactStatus {
    ONLINE, OFFLINE, AWAY, BUSY, INVISIBLE
}

enum class ContactRequestStatus {
    PENDING, ACCEPTED, REJECTED, EXPIRED
}

enum class CallStatus {
    RINGING, ACTIVE, ENDED, MISSED, FAILED
}

// Extension functions for convenience
fun Contact.getDisplayName(): String {
    return if (name.isNotBlank()) name else "Contact ${id.take(8)}"
}

fun Message.getFormattedTime(): String {
    return java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(createdAt))
}

fun Contact.getContactCodeString(): String {
    return contactCode.joinToString(" ")
}

fun UserProfile.getFullContactCode(): List<String> {
    return contactCode + secretWords
}

fun ServerNode.isHealthy(): Boolean {
    val now = System.currentTimeMillis()
    return isActive && (now - lastPing) < 60000 // Consider healthy if pinged within last minute
}
