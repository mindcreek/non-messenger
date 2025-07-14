use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Contact {
    pub id: String,
    pub name: String,
    pub contact_code: Vec<String>,
    pub public_key: String,
    pub status: String,
    pub last_seen: i64,
    pub is_verified: bool,
    pub device_id: String,
    pub created_at: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Message {
    pub id: String,
    pub contact_id: String,
    pub content: String,
    pub is_from_me: bool,
    pub timestamp: i64,
    pub message_type: String,
    pub delivery_status: String,
    pub encrypted_content: String,
    pub created_at: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ContactRequest {
    pub id: String,
    pub sender_id: String,
    pub sender_name: String,
    pub public_words: Vec<String>,
    pub verification_message: String,
    pub sender_public_key: String,
    pub status: String,
    pub received_at: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UserProfile {
    pub id: String,
    pub contact_code: Vec<String>,
    pub secret_words: Vec<String>,
    pub public_key: String,
    pub private_key: String,
    pub device_id: String,
    pub display_name: String,
    pub status: String,
    pub custom_message: String,
    pub created_at: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ServerNode {
    pub url: String,
    pub public_key: String,
    pub is_active: bool,
    pub last_ping: i64,
    pub response_time: i64,
    pub priority: i32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MessageEnvelope {
    pub id: String,
    pub recipient_contact_code: String,
    pub encrypted_message: crate::crypto::EncryptedMessage,
    pub timestamp: i64,
    pub ttl: i64,
    pub message_type: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ContactRequestMessage {
    pub r#type: String,
    pub id: String,
    pub timestamp: i64,
    pub sender_id: String,
    pub sender_name: String,
    pub public_words: Vec<String>,
    pub verification_message: String,
    pub sender_public_key: String,
    pub version: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ContactResponseMessage {
    pub r#type: String,
    pub id: String,
    pub timestamp: i64,
    pub original_request_id: String,
    pub accepted: bool,
    pub secret_words: Option<Vec<String>>,
    pub recipient_public_key: Option<String>,
    pub version: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VoiceCallMessage {
    pub r#type: String,
    pub id: String,
    pub timestamp: i64,
    pub call_id: String,
    pub caller_id: Option<String>,
    pub recipient_id: Option<String>,
    pub version: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VoiceDataMessage {
    pub r#type: String,
    pub id: String,
    pub timestamp: i64,
    pub call_id: String,
    pub encrypted_audio_data: String,
    pub sequence_number: i32,
    pub version: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AwarenessMessage {
    pub r#type: String,
    pub user_id: String,
    pub timestamp: i64,
    pub status: Option<String>,
    pub custom_message: Option<String>,
    pub is_typing: Option<bool>,
    pub chat_id: Option<String>,
    pub message_id: Option<String>,
    pub delivery_status: Option<String>,
    pub is_online: Option<bool>,
    pub connection_quality: Option<f32>,
    pub version: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChatSession {
    pub contact_id: String,
    pub contact_name: String,
    pub last_message: String,
    pub last_message_time: i64,
    pub unread_count: i32,
    pub is_typing: bool,
    pub is_online: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ServerStatus {
    pub url: String,
    pub is_connected: bool,
    pub last_ping: i64,
    pub response_time: i64,
    pub message_pool_size: i32,
    pub active_sessions: i32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CallStatus {
    pub state: String,
    pub call_id: Option<String>,
    pub contact_id: Option<String>,
    pub start_time: Option<i64>,
    pub duration: Option<i64>,
    pub is_incoming: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeviceInfo {
    pub platform: String,
    pub arch: String,
    pub version: String,
    pub build_date: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateInfo {
    pub available: bool,
    pub version: String,
    pub download_url: Option<String>,
    pub release_notes: Option<String>,
}

// Enums for better type safety
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum MessageType {
    Text,
    Image,
    File,
    VoiceNote,
    ContactRequest,
    ContactResponse,
    VoiceCallInit,
    VoiceCallAccept,
    VoiceCallReject,
    VoiceCallEnd,
    VoiceData,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum DeliveryStatus {
    Sending,
    Sent,
    Delivered,
    Read,
    Failed,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ContactStatus {
    Online,
    Offline,
    Away,
    Busy,
    Invisible,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ContactRequestStatus {
    Pending,
    Accepted,
    Rejected,
    Expired,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum CallState {
    Idle,
    Calling,
    Ringing,
    Connected,
    Ended,
    Failed,
}

// Extension traits for convenience
impl Contact {
    pub fn get_display_name(&self) -> &str {
        if !self.name.is_empty() {
            &self.name
        } else {
            &self.id[..8.min(self.id.len())]
        }
    }

    pub fn get_contact_code_string(&self) -> String {
        self.contact_code.join(" ")
    }

    pub fn is_online(&self) -> bool {
        self.status == "online"
    }
}

impl Message {
    pub fn get_formatted_time(&self) -> String {
        let datetime = chrono::DateTime::from_timestamp(self.timestamp, 0)
            .unwrap_or_else(|| chrono::Utc::now());
        datetime.format("%H:%M").to_string()
    }

    pub fn get_formatted_date(&self) -> String {
        let datetime = chrono::DateTime::from_timestamp(self.timestamp, 0)
            .unwrap_or_else(|| chrono::Utc::now());
        datetime.format("%Y-%m-%d").to_string()
    }
}

impl UserProfile {
    pub fn get_full_contact_code(&self) -> Vec<String> {
        let mut full_code = self.contact_code.clone();
        full_code.extend(self.secret_words.clone());
        full_code
    }

    pub fn get_public_contact_string(&self) -> String {
        self.contact_code.join(" ")
    }
}

impl ServerNode {
    pub fn is_healthy(&self) -> bool {
        let now = chrono::Utc::now().timestamp();
        self.is_active && (now - self.last_ping) < 60 // Consider healthy if pinged within last minute
    }

    pub fn get_latency_description(&self) -> String {
        match self.response_time {
            0..=50 => "Excellent".to_string(),
            51..=100 => "Good".to_string(),
            101..=200 => "Fair".to_string(),
            201..=500 => "Poor".to_string(),
            _ => "Very Poor".to_string(),
        }
    }
}
