package com.nonmessenger.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nonmessenger.crypto.NonMessengerCrypto
import com.nonmessenger.database.NonMessengerDatabase
import com.nonmessenger.models.*
import com.nonmessenger.network.MessagePoolClient
import com.nonmessenger.repository.ContactRepository
import com.nonmessenger.repository.MessageRepository
import com.nonmessenger.repository.UserRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = NonMessengerDatabase.getDatabase(application)
    private val crypto = NonMessengerCrypto()
    private val messageClient = MessagePoolClient()
    
    private val contactRepository = ContactRepository(database.contactDao())
    private val messageRepository = MessageRepository(database.messageDao())
    private val userRepository = UserRepository(database.userDao())
    
    // UI State
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    private val _selectedContact = MutableStateFlow<Contact?>(null)
    val selectedContact: StateFlow<Contact?> = _selectedContact.asStateFlow()
    
    private val _myContactCode = MutableStateFlow<List<String>>(emptyList())
    val myContactCode: StateFlow<List<String>> = _myContactCode.asStateFlow()
    
    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _serverStatus = MutableStateFlow<ServerStatus?>(null)
    val serverStatus: StateFlow<ServerStatus?> = _serverStatus.asStateFlow()
    
    val primaryServer = "ws://localhost:3000" // TODO: Make configurable
    
    init {
        loadUserProfile()
        loadContacts()
        connectToMessagePool()
    }
    
    private fun loadUserProfile() {
        viewModelScope.launch {
            try {
                val profile = userRepository.getUserProfile()
                _userProfile.value = profile
                _myContactCode.value = profile?.contactCode ?: emptyList()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load user profile: ${e.message}"
            }
        }
    }
    
    private fun loadContacts() {
        viewModelScope.launch {
            contactRepository.getAllContacts().collect { contactList ->
                _contacts.value = contactList
            }
        }
    }
    
    private fun connectToMessagePool() {
        viewModelScope.launch {
            try {
                messageClient.connect(primaryServer)
                _serverStatus.value = ServerStatus(
                    url = primaryServer,
                    isConnected = true,
                    lastPing = System.currentTimeMillis(),
                    responseTime = 0
                )
                
                // Register user with server
                _userProfile.value?.let { profile ->
                    messageClient.registerUser(profile.contactCode.joinToString("-"))
                }
                
                // Listen for incoming messages
                messageClient.onMessageReceived = { encryptedMessage ->
                    handleIncomingMessage(encryptedMessage)
                }
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to connect to server: ${e.message}"
                _serverStatus.value = ServerStatus(
                    url = primaryServer,
                    isConnected = false,
                    lastPing = System.currentTimeMillis(),
                    responseTime = 0
                )
            }
        }
    }
    
    fun generateContactCode() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // Generate 8 public words and 8 secret words
                val publicWords = crypto.generate8WordContactCode()
                val secretWords = crypto.generate8WordSecretCode()
                
                // Generate key pair from all 16 words
                val allWords = publicWords + secretWords
                val keyPair = crypto.generateFullKeyPair(allWords)
                
                // Generate device ID
                val deviceId = crypto.generateDeviceId()
                
                // Create user profile
                val profile = UserProfile(
                    contactCode = publicWords,
                    secretWords = secretWords,
                    publicKey = keyPair.publicKey,
                    privateKey = keyPair.privateKey,
                    deviceId = deviceId
                )
                
                // Save to database
                userRepository.saveUserProfile(profile)
                
                _userProfile.value = profile
                _myContactCode.value = publicWords
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to generate contact code: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun selectContact(contact: Contact) {
        _selectedContact.value = contact
        loadMessagesForContact(contact.id)
    }
    
    private fun loadMessagesForContact(contactId: String) {
        viewModelScope.launch {
            messageRepository.getMessagesForContact(contactId).collect { messageList ->
                _messages.value = messageList
            }
        }
    }
    
    fun sendMessage(content: String) {
        val contact = _selectedContact.value ?: return
        val profile = _userProfile.value ?: return
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // Create message
                val message = Message(
                    contactId = contact.id,
                    content = content,
                    isFromMe = true,
                    timestamp = getCurrentTimeString(),
                    deliveryStatus = "sending"
                )
                
                // Save to local database first
                messageRepository.insertMessage(message)
                
                // Encrypt message
                val encryptedData = crypto.encryptMessage(content, contact.publicKey)
                
                // Create message envelope
                val envelope = MessageEnvelope(
                    id = message.id,
                    recipientContactCode = contact.contactCode.joinToString("-"),
                    encryptedMessage = encryptedData,
                    timestamp = System.currentTimeMillis()
                )
                
                // Send to server
                val success = messageClient.sendMessage(envelope)
                
                // Update delivery status
                val updatedMessage = message.copy(
                    deliveryStatus = if (success) "sent" else "failed"
                )
                messageRepository.updateMessage(updatedMessage)
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to send message: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private fun handleIncomingMessage(encryptedMessage: String) {
        viewModelScope.launch {
            try {
                val profile = _userProfile.value ?: return@launch
                
                // Parse the encrypted message envelope
                val envelope = kotlinx.serialization.json.Json.decodeFromString<MessageEnvelope>(encryptedMessage)
                
                // Decrypt the message content
                val decryptedContent = crypto.decryptMessage(envelope.encryptedMessage, profile.privateKey)
                
                // Find the sender contact
                val senderContactCode = envelope.recipientContactCode // This should be sender's code in incoming messages
                val senderContact = _contacts.value.find { 
                    it.contactCode.joinToString("-") == senderContactCode 
                }
                
                if (senderContact != null) {
                    // Create message record
                    val message = Message(
                        id = envelope.id,
                        contactId = senderContact.id,
                        content = decryptedContent,
                        isFromMe = false,
                        timestamp = getCurrentTimeString(),
                        deliveryStatus = "delivered"
                    )
                    
                    // Save to database
                    messageRepository.insertMessage(message)
                    
                    // If this contact is currently selected, refresh messages
                    if (_selectedContact.value?.id == senderContact.id) {
                        loadMessagesForContact(senderContact.id)
                    }
                }
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to process incoming message: ${e.message}"
            }
        }
    }
    
    fun addContact(qrCodeData: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // Parse QR code data
                val qrData = crypto.parseQRCodeData(qrCodeData)
                
                // Extract contact information
                val publicKey = qrData["publicKey"] as? String ?: throw Exception("Invalid QR code: missing public key")
                val deviceId = qrData["deviceId"] as? String ?: throw Exception("Invalid QR code: missing device ID")
                val contactWords = qrData["contactWords"] as? List<String> ?: throw Exception("Invalid QR code: missing contact words")
                
                // Create contact
                val contact = Contact(
                    name = "Contact ${deviceId.take(8)}", // Default name, user can change later
                    contactCode = contactWords,
                    publicKey = publicKey,
                    deviceId = deviceId,
                    status = "offline"
                )
                
                // Save to database
                contactRepository.insertContact(contact)
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add contact: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun shareContactCode() {
        val profile = _userProfile.value ?: return
        
        viewModelScope.launch {
            try {
                // Create QR code data
                val qrData = QRCodeData(
                    publicKey = profile.publicKey,
                    deviceId = profile.deviceId,
                    timestamp = System.currentTimeMillis(),
                    contactWords = profile.contactCode
                )
                
                val qrString = kotlinx.serialization.json.Json.encodeToString(QRCodeData.serializer(), qrData)
                
                // TODO: Generate QR code image and share
                // For now, just copy to clipboard or show in dialog
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to share contact code: ${e.message}"
            }
        }
    }
    
    fun testServerConnection() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val startTime = System.currentTimeMillis()
                
                val isConnected = messageClient.testConnection()
                val responseTime = System.currentTimeMillis() - startTime
                
                _serverStatus.value = ServerStatus(
                    url = primaryServer,
                    isConnected = isConnected,
                    lastPing = System.currentTimeMillis(),
                    responseTime = responseTime
                )
                
                if (!isConnected) {
                    _errorMessage.value = "Server connection failed"
                }
                
            } catch (e: Exception) {
                _errorMessage.value = "Connection test failed: ${e.message}"
                _serverStatus.value = ServerStatus(
                    url = primaryServer,
                    isConnected = false,
                    lastPing = System.currentTimeMillis(),
                    responseTime = 0
                )
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    private fun getCurrentTimeString(): String {
        return java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
    }
    
    override fun onCleared() {
        super.onCleared()
        messageClient.disconnect()
    }
}
