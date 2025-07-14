package com.nonmessenger.database

import android.content.Context
import androidx.room.*
import com.nonmessenger.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<Contact>>
    
    @Query("SELECT * FROM contacts WHERE id = :contactId")
    suspend fun getContactById(contactId: String): Contact?
    
    @Query("SELECT * FROM contacts WHERE contactCode = :contactCode")
    suspend fun getContactByCode(contactCode: String): Contact?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact)
    
    @Update
    suspend fun updateContact(contact: Contact)
    
    @Delete
    suspend fun deleteContact(contact: Contact)
    
    @Query("UPDATE contacts SET status = :status, lastSeen = :lastSeen WHERE id = :contactId")
    suspend fun updateContactStatus(contactId: String, status: String, lastSeen: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE contactId = :contactId ORDER BY createdAt ASC")
    fun getMessagesForContact(contactId: String): Flow<List<Message>>
    
    @Query("SELECT * FROM messages ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestMessage(): Message?
    
    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): Message?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)
    
    @Update
    suspend fun updateMessage(message: Message)
    
    @Delete
    suspend fun deleteMessage(message: Message)
    
    @Query("UPDATE messages SET deliveryStatus = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)
    
    @Query("DELETE FROM messages WHERE contactId = :contactId")
    suspend fun deleteMessagesForContact(contactId: String)
    
    @Query("SELECT COUNT(*) FROM messages WHERE contactId = :contactId AND isFromMe = 0 AND deliveryStatus != 'read'")
    suspend fun getUnreadMessageCount(contactId: String): Int
}

@Dao
interface ContactRequestDao {
    @Query("SELECT * FROM contact_requests WHERE status = 'pending' ORDER BY receivedAt DESC")
    fun getPendingRequests(): Flow<List<ContactRequest>>
    
    @Query("SELECT * FROM contact_requests ORDER BY receivedAt DESC")
    fun getAllRequests(): Flow<List<ContactRequest>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: ContactRequest)
    
    @Update
    suspend fun updateRequest(request: ContactRequest)
    
    @Query("UPDATE contact_requests SET status = :status WHERE id = :requestId")
    suspend fun updateRequestStatus(requestId: String, status: String)
    
    @Delete
    suspend fun deleteRequest(request: ContactRequest)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM user_profile WHERE id = 'user_profile'")
    suspend fun getUserProfile(): UserProfile?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserProfile(profile: UserProfile)
    
    @Update
    suspend fun updateUserProfile(profile: UserProfile)
    
    @Query("UPDATE user_profile SET status = :status WHERE id = 'user_profile'")
    suspend fun updateUserStatus(status: String)
    
    @Query("UPDATE user_profile SET customMessage = :message WHERE id = 'user_profile'")
    suspend fun updateCustomMessage(message: String)
}

@Dao
interface ServerNodeDao {
    @Query("SELECT * FROM server_nodes WHERE isActive = 1 ORDER BY priority ASC")
    fun getActiveNodes(): Flow<List<ServerNode>>
    
    @Query("SELECT * FROM server_nodes ORDER BY priority ASC")
    fun getAllNodes(): Flow<List<ServerNode>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: ServerNode)
    
    @Update
    suspend fun updateNode(node: ServerNode)
    
    @Query("UPDATE server_nodes SET lastPing = :timestamp, responseTime = :responseTime WHERE url = :url")
    suspend fun updateNodePing(url: String, timestamp: Long, responseTime: Long)
    
    @Query("UPDATE server_nodes SET isActive = :isActive WHERE url = :url")
    suspend fun updateNodeStatus(url: String, isActive: Boolean)
    
    @Delete
    suspend fun deleteNode(node: ServerNode)
}

@Database(
    entities = [
        Contact::class,
        Message::class,
        ContactRequest::class,
        UserProfile::class,
        ServerNode::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class NonMessengerDatabase : RoomDatabase() {
    
    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao
    abstract fun contactRequestDao(): ContactRequestDao
    abstract fun userDao(): UserDao
    abstract fun serverNodeDao(): ServerNodeDao
    
    companion object {
        @Volatile
        private var INSTANCE: NonMessengerDatabase? = null
        
        fun getDatabase(context: Context): NonMessengerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NonMessengerDatabase::class.java,
                    "nonmessenger_database"
                )
                .fallbackToDestructiveMigration() // For development only
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Repository classes for clean architecture
class ContactRepository(private val contactDao: ContactDao) {
    fun getAllContacts() = contactDao.getAllContacts()
    
    suspend fun getContactById(contactId: String) = contactDao.getContactById(contactId)
    
    suspend fun getContactByCode(contactCode: String) = contactDao.getContactByCode(contactCode)
    
    suspend fun insertContact(contact: Contact) = contactDao.insertContact(contact)
    
    suspend fun updateContact(contact: Contact) = contactDao.updateContact(contact)
    
    suspend fun deleteContact(contact: Contact) = contactDao.deleteContact(contact)
    
    suspend fun updateContactStatus(contactId: String, status: String, lastSeen: Long = System.currentTimeMillis()) {
        contactDao.updateContactStatus(contactId, status, lastSeen)
    }
}

class MessageRepository(private val messageDao: MessageDao) {
    fun getMessagesForContact(contactId: String) = messageDao.getMessagesForContact(contactId)
    
    suspend fun getLatestMessage() = messageDao.getLatestMessage()
    
    suspend fun getMessageById(messageId: String) = messageDao.getMessageById(messageId)
    
    suspend fun insertMessage(message: Message) = messageDao.insertMessage(message)
    
    suspend fun updateMessage(message: Message) = messageDao.updateMessage(message)
    
    suspend fun deleteMessage(message: Message) = messageDao.deleteMessage(message)
    
    suspend fun updateMessageStatus(messageId: String, status: String) {
        messageDao.updateMessageStatus(messageId, status)
    }
    
    suspend fun deleteMessagesForContact(contactId: String) = messageDao.deleteMessagesForContact(contactId)
    
    suspend fun getUnreadMessageCount(contactId: String) = messageDao.getUnreadMessageCount(contactId)
}

class UserRepository(private val userDao: UserDao) {
    suspend fun getUserProfile() = userDao.getUserProfile()
    
    suspend fun saveUserProfile(profile: UserProfile) = userDao.saveUserProfile(profile)
    
    suspend fun updateUserProfile(profile: UserProfile) = userDao.updateUserProfile(profile)
    
    suspend fun updateUserStatus(status: String) = userDao.updateUserStatus(status)
    
    suspend fun updateCustomMessage(message: String) = userDao.updateCustomMessage(message)
}

class ServerNodeRepository(private val serverNodeDao: ServerNodeDao) {
    fun getActiveNodes() = serverNodeDao.getActiveNodes()
    
    fun getAllNodes() = serverNodeDao.getAllNodes()
    
    suspend fun insertNode(node: ServerNode) = serverNodeDao.insertNode(node)
    
    suspend fun updateNode(node: ServerNode) = serverNodeDao.updateNode(node)
    
    suspend fun updateNodePing(url: String, timestamp: Long, responseTime: Long) {
        serverNodeDao.updateNodePing(url, timestamp, responseTime)
    }
    
    suspend fun updateNodeStatus(url: String, isActive: Boolean) {
        serverNodeDao.updateNodeStatus(url, isActive)
    }
    
    suspend fun deleteNode(node: ServerNode) = serverNodeDao.deleteNode(node)
}
