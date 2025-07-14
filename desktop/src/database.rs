use crate::models::*;
use anyhow::{Result, anyhow};
use rusqlite::{Connection, params, Row};
use std::path::PathBuf;
use dirs::data_dir;

pub struct Database {
    conn: Connection,
}

impl Database {
    pub async fn new() -> Result<Self> {
        let db_path = Self::get_database_path()?;
        
        // Ensure directory exists
        if let Some(parent) = db_path.parent() {
            std::fs::create_dir_all(parent)?;
        }

        let conn = Connection::open(&db_path)?;
        let mut db = Self { conn };
        
        db.initialize_tables().await?;
        Ok(db)
    }

    fn get_database_path() -> Result<PathBuf> {
        let mut path = data_dir()
            .ok_or_else(|| anyhow!("Could not find data directory"))?;
        path.push("NonMessenger");
        path.push("nonmessenger.db");
        Ok(path)
    }

    async fn initialize_tables(&mut self) -> Result<()> {
        // Contacts table
        self.conn.execute(
            "CREATE TABLE IF NOT EXISTS contacts (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                contact_code TEXT NOT NULL,
                public_key TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'offline',
                last_seen INTEGER NOT NULL DEFAULT 0,
                is_verified BOOLEAN NOT NULL DEFAULT 0,
                device_id TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )",
            [],
        )?;

        // Messages table
        self.conn.execute(
            "CREATE TABLE IF NOT EXISTS messages (
                id TEXT PRIMARY KEY,
                contact_id TEXT NOT NULL,
                content TEXT NOT NULL,
                is_from_me BOOLEAN NOT NULL,
                timestamp INTEGER NOT NULL,
                message_type TEXT NOT NULL DEFAULT 'text',
                delivery_status TEXT NOT NULL DEFAULT 'sent',
                encrypted_content TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                FOREIGN KEY (contact_id) REFERENCES contacts (id)
            )",
            [],
        )?;

        // Contact requests table
        self.conn.execute(
            "CREATE TABLE IF NOT EXISTS contact_requests (
                id TEXT PRIMARY KEY,
                sender_id TEXT NOT NULL,
                sender_name TEXT NOT NULL,
                public_words TEXT NOT NULL,
                verification_message TEXT NOT NULL,
                sender_public_key TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'pending',
                received_at INTEGER NOT NULL
            )",
            [],
        )?;

        // User profile table
        self.conn.execute(
            "CREATE TABLE IF NOT EXISTS user_profile (
                id TEXT PRIMARY KEY,
                contact_code TEXT NOT NULL,
                secret_words TEXT NOT NULL,
                public_key TEXT NOT NULL,
                private_key TEXT NOT NULL,
                device_id TEXT NOT NULL,
                display_name TEXT NOT NULL DEFAULT 'Me',
                status TEXT NOT NULL DEFAULT 'online',
                custom_message TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )",
            [],
        )?;

        // Server nodes table
        self.conn.execute(
            "CREATE TABLE IF NOT EXISTS server_nodes (
                url TEXT PRIMARY KEY,
                public_key TEXT NOT NULL,
                is_active BOOLEAN NOT NULL DEFAULT 1,
                last_ping INTEGER NOT NULL DEFAULT 0,
                response_time INTEGER NOT NULL DEFAULT 0,
                priority INTEGER NOT NULL DEFAULT 0
            )",
            [],
        )?;

        // Create indexes for better performance
        self.conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_messages_contact_id ON messages (contact_id)",
            [],
        )?;

        self.conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON messages (timestamp)",
            [],
        )?;

        self.conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_contacts_status ON contacts (status)",
            [],
        )?;

        Ok(())
    }

    // Contact operations
    pub async fn get_all_contacts(&self) -> Result<Vec<Contact>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, name, contact_code, public_key, status, last_seen, is_verified, device_id, created_at 
             FROM contacts ORDER BY name ASC"
        )?;

        let contact_iter = stmt.query_map([], |row| {
            Ok(Contact {
                id: row.get(0)?,
                name: row.get(1)?,
                contact_code: serde_json::from_str(&row.get::<_, String>(2)?).unwrap_or_default(),
                public_key: row.get(3)?,
                status: row.get(4)?,
                last_seen: row.get(5)?,
                is_verified: row.get(6)?,
                device_id: row.get(7)?,
                created_at: row.get(8)?,
            })
        })?;

        let mut contacts = Vec::new();
        for contact in contact_iter {
            contacts.push(contact?);
        }

        Ok(contacts)
    }

    pub async fn get_contact_by_id(&self, contact_id: &str) -> Result<Option<Contact>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, name, contact_code, public_key, status, last_seen, is_verified, device_id, created_at 
             FROM contacts WHERE id = ?1"
        )?;

        let mut contact_iter = stmt.query_map([contact_id], |row| {
            Ok(Contact {
                id: row.get(0)?,
                name: row.get(1)?,
                contact_code: serde_json::from_str(&row.get::<_, String>(2)?).unwrap_or_default(),
                public_key: row.get(3)?,
                status: row.get(4)?,
                last_seen: row.get(5)?,
                is_verified: row.get(6)?,
                device_id: row.get(7)?,
                created_at: row.get(8)?,
            })
        })?;

        match contact_iter.next() {
            Some(contact) => Ok(Some(contact?)),
            None => Ok(None),
        }
    }

    pub async fn insert_contact(&self, contact: &Contact) -> Result<()> {
        self.conn.execute(
            "INSERT OR REPLACE INTO contacts 
             (id, name, contact_code, public_key, status, last_seen, is_verified, device_id, created_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)",
            params![
                contact.id,
                contact.name,
                serde_json::to_string(&contact.contact_code)?,
                contact.public_key,
                contact.status,
                contact.last_seen,
                contact.is_verified,
                contact.device_id,
                contact.created_at
            ],
        )?;

        Ok(())
    }

    pub async fn update_contact_status(&self, contact_id: &str, status: &str, last_seen: i64) -> Result<()> {
        self.conn.execute(
            "UPDATE contacts SET status = ?1, last_seen = ?2 WHERE id = ?3",
            params![status, last_seen, contact_id],
        )?;

        Ok(())
    }

    // Message operations
    pub async fn get_messages_for_contact(&self, contact_id: &str) -> Result<Vec<Message>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, contact_id, content, is_from_me, timestamp, message_type, delivery_status, encrypted_content, created_at
             FROM messages WHERE contact_id = ?1 ORDER BY timestamp ASC"
        )?;

        let message_iter = stmt.query_map([contact_id], |row| {
            Ok(Message {
                id: row.get(0)?,
                contact_id: row.get(1)?,
                content: row.get(2)?,
                is_from_me: row.get(3)?,
                timestamp: row.get(4)?,
                message_type: row.get(5)?,
                delivery_status: row.get(6)?,
                encrypted_content: row.get(7)?,
                created_at: row.get(8)?,
            })
        })?;

        let mut messages = Vec::new();
        for message in message_iter {
            messages.push(message?);
        }

        Ok(messages)
    }

    pub async fn insert_message(&self, message: &Message) -> Result<()> {
        self.conn.execute(
            "INSERT OR REPLACE INTO messages 
             (id, contact_id, content, is_from_me, timestamp, message_type, delivery_status, encrypted_content, created_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)",
            params![
                message.id,
                message.contact_id,
                message.content,
                message.is_from_me,
                message.timestamp,
                message.message_type,
                message.delivery_status,
                message.encrypted_content,
                message.created_at
            ],
        )?;

        Ok(())
    }

    pub async fn update_message_status(&self, message_id: &str, status: &str) -> Result<()> {
        self.conn.execute(
            "UPDATE messages SET delivery_status = ?1 WHERE id = ?2",
            params![status, message_id],
        )?;

        Ok(())
    }

    // User profile operations
    pub async fn get_user_profile(&self) -> Result<Option<UserProfile>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, contact_code, secret_words, public_key, private_key, device_id, display_name, status, custom_message, created_at
             FROM user_profile WHERE id = 'user_profile'"
        )?;

        let mut profile_iter = stmt.query_map([], |row| {
            Ok(UserProfile {
                id: row.get(0)?,
                contact_code: serde_json::from_str(&row.get::<_, String>(1)?).unwrap_or_default(),
                secret_words: serde_json::from_str(&row.get::<_, String>(2)?).unwrap_or_default(),
                public_key: row.get(3)?,
                private_key: row.get(4)?,
                device_id: row.get(5)?,
                display_name: row.get(6)?,
                status: row.get(7)?,
                custom_message: row.get(8)?,
                created_at: row.get(9)?,
            })
        })?;

        match profile_iter.next() {
            Some(profile) => Ok(Some(profile?)),
            None => Ok(None),
        }
    }

    pub async fn save_user_profile(&self, profile: &UserProfile) -> Result<()> {
        self.conn.execute(
            "INSERT OR REPLACE INTO user_profile 
             (id, contact_code, secret_words, public_key, private_key, device_id, display_name, status, custom_message, created_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)",
            params![
                profile.id,
                serde_json::to_string(&profile.contact_code)?,
                serde_json::to_string(&profile.secret_words)?,
                profile.public_key,
                profile.private_key,
                profile.device_id,
                profile.display_name,
                profile.status,
                profile.custom_message,
                profile.created_at
            ],
        )?;

        Ok(())
    }

    // Server node operations
    pub async fn get_active_nodes(&self) -> Result<Vec<ServerNode>> {
        let mut stmt = self.conn.prepare(
            "SELECT url, public_key, is_active, last_ping, response_time, priority
             FROM server_nodes WHERE is_active = 1 ORDER BY priority ASC"
        )?;

        let node_iter = stmt.query_map([], |row| {
            Ok(ServerNode {
                url: row.get(0)?,
                public_key: row.get(1)?,
                is_active: row.get(2)?,
                last_ping: row.get(3)?,
                response_time: row.get(4)?,
                priority: row.get(5)?,
            })
        })?;

        let mut nodes = Vec::new();
        for node in node_iter {
            nodes.push(node?);
        }

        Ok(nodes)
    }

    pub async fn insert_server_node(&self, node: &ServerNode) -> Result<()> {
        self.conn.execute(
            "INSERT OR REPLACE INTO server_nodes 
             (url, public_key, is_active, last_ping, response_time, priority)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
            params![
                node.url,
                node.public_key,
                node.is_active,
                node.last_ping,
                node.response_time,
                node.priority
            ],
        )?;

        Ok(())
    }

    pub async fn update_node_ping(&self, url: &str, timestamp: i64, response_time: i64) -> Result<()> {
        self.conn.execute(
            "UPDATE server_nodes SET last_ping = ?1, response_time = ?2 WHERE url = ?3",
            params![timestamp, response_time, url],
        )?;

        Ok(())
    }
}
