package com.nonmessenger.network

import android.util.Log
import com.nonmessenger.model.MessageEnvelope
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class MessagePoolClient {
    private var webSocket: WebSocket? = null
    private var httpClient: OkHttpClient? = null
    private var serverUrl: String = ""
    private var isConnected = false
    private val json = Json { ignoreUnknownKeys = true }
    
    var onMessageReceived: ((String) -> Unit)? = null
    var onConnectionStatusChanged: ((Boolean) -> Unit)? = null
    
    private val webSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connected")
            isConnected = true
            onConnectionStatusChanged?.invoke(true)
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Received message: $text")
            try {
                val message = json.decodeFromString<WebSocketMessage>(text)
                when (message.type) {
                    "new_message" -> {
                        onMessageReceived?.invoke(message.message ?: "")
                    }
                    "status_update" -> {
                        handleStatusUpdate(message)
                    }
                    "registration_success" -> {
                        Log.d(TAG, "User registration successful")
                    }
                    "error" -> {
                        Log.e(TAG, "Server error: ${message.error}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse WebSocket message", e)
            }
        }
        
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code $reason")
            isConnected = false
            onConnectionStatusChanged?.invoke(false)
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code $reason")
            isConnected = false
            onConnectionStatusChanged?.invoke(false)
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure", t)
            isConnected = false
            onConnectionStatusChanged?.invoke(false)
        }
    }
    
    suspend fun connect(serverUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            this@MessagePoolClient.serverUrl = serverUrl
            
            // Initialize HTTP client
            httpClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
            
            // Convert HTTP URL to WebSocket URL
            val wsUrl = serverUrl.replace("http://", "ws://").replace("https://", "wss://")
            
            // Create WebSocket connection
            val request = Request.Builder()
                .url(wsUrl)
                .build()
            
            webSocket = httpClient!!.newWebSocket(request, webSocketListener)
            
            // Wait a bit for connection to establish
            delay(2000)
            
            isConnected
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to server", e)
            false
        }
    }
    
    fun registerUser(contactCode: String) {
        if (!isConnected) {
            Log.w(TAG, "Cannot register user: not connected")
            return
        }
        
        val message = WebSocketMessage(
            type = "register_user",
            contactCode = contactCode
        )
        
        val messageJson = json.encodeToString(WebSocketMessage.serializer(), message)
        webSocket?.send(messageJson)
    }
    
    suspend fun sendMessage(envelope: MessageEnvelope): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = httpClient ?: return@withContext false
            
            val messageJson = json.encodeToString(MessageEnvelope.serializer(), envelope)
            val requestBody = messageJson.toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url("$serverUrl/api/message")
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Message sent successfully: $responseBody")
                true
            } else {
                Log.e(TAG, "Failed to send message: ${response.code} ${response.message}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            false
        }
    }
    
    suspend fun getMessages(contactCode: String): List<MessageEnvelope> = withContext(Dispatchers.IO) {
        try {
            val client = httpClient ?: return@withContext emptyList()
            
            val request = Request.Builder()
                .url("$serverUrl/api/messages/$contactCode")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: return@withContext emptyList()
                val messageResponse = json.decodeFromString<MessageResponse>(responseBody)
                messageResponse.messages
            } else {
                Log.e(TAG, "Failed to get messages: ${response.code} ${response.message}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting messages", e)
            emptyList()
        }
    }
    
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = httpClient ?: return@withContext false
            
            val request = Request.Builder()
                .url("$serverUrl/health")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            false
        }
    }
    
    suspend fun getServerStatus(): ServerHealthResponse? = withContext(Dispatchers.IO) {
        try {
            val client = httpClient ?: return@withContext null
            
            val request = Request.Builder()
                .url("$serverUrl/health")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: return@withContext null
                json.decodeFromString<ServerHealthResponse>(responseBody)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get server status", e)
            null
        }
    }
    
    fun sendStatusUpdate(status: String, customMessage: String? = null) {
        if (!isConnected) return
        
        val message = WebSocketMessage(
            type = "status_update",
            status = status,
            customMessage = customMessage
        )
        
        val messageJson = json.encodeToString(WebSocketMessage.serializer(), message)
        webSocket?.send(messageJson)
    }
    
    fun sendTypingIndicator(chatId: String, isTyping: Boolean) {
        if (!isConnected) return
        
        val message = WebSocketMessage(
            type = "typing_indicator",
            chatId = chatId,
            isTyping = isTyping
        )
        
        val messageJson = json.encodeToString(WebSocketMessage.serializer(), message)
        webSocket?.send(messageJson)
    }
    
    private fun handleStatusUpdate(message: WebSocketMessage) {
        // Handle status updates from other users
        Log.d(TAG, "Status update: ${message.status} from ${message.userId}")
        // TODO: Update contact status in database
    }
    
    fun disconnect() {
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
        httpClient = null
        isConnected = false
        onConnectionStatusChanged?.invoke(false)
    }
    
    fun isConnected(): Boolean = isConnected
    
    companion object {
        private const val TAG = "MessagePoolClient"
    }
}

@kotlinx.serialization.Serializable
data class WebSocketMessage(
    val type: String,
    val contactCode: String? = null,
    val message: String? = null,
    val messageId: String? = null,
    val timestamp: Long? = null,
    val error: String? = null,
    val status: String? = null,
    val customMessage: String? = null,
    val userId: String? = null,
    val chatId: String? = null,
    val isTyping: Boolean? = null
)

@kotlinx.serialization.Serializable
data class MessageResponse(
    val messages: List<MessageEnvelope>
)

@kotlinx.serialization.Serializable
data class ServerHealthResponse(
    val status: String,
    val timestamp: Long,
    val version: String,
    val messagePoolSize: Int,
    val activeSessions: Int,
    val connectedNodes: Int
)
