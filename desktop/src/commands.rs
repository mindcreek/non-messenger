use crate::{AppState, crypto::*, models::*, database::*, network::*};
use tauri::State;
use serde_json::Value;
use anyhow::Result;

// Crypto Commands
#[tauri::command]
pub async fn generate_contact_code(state: State<'_, AppState>) -> Result<Vec<String>, String> {
    let mut crypto = state.crypto.as_ref().clone();
    crypto.generate_8_word_contact_code()
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn generate_key_pair(state: State<'_, AppState>) -> Result<KeyPair, String> {
    let mut crypto = state.crypto.as_ref().clone();
    crypto.generate_rsa_key_pair()
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn encrypt_message(
    message: String,
    public_key: String,
    state: State<'_, AppState>
) -> Result<EncryptedMessage, String> {
    let mut crypto = state.crypto.as_ref().clone();
    crypto.encrypt_message(&message, &public_key)
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn decrypt_message(
    encrypted_data: EncryptedMessage,
    private_key: String,
    state: State<'_, AppState>
) -> Result<String, String> {
    let crypto = state.crypto.as_ref();
    crypto.decrypt_message(&encrypted_data, &private_key)
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn generate_qr_code(
    public_key: String,
    device_id: String,
    contact_words: Vec<String>,
    state: State<'_, AppState>
) -> Result<String, String> {
    let crypto = state.crypto.as_ref();
    let mut qr_data = crypto.generate_qr_code_data(&public_key, &device_id)
        .map_err(|e| e.to_string())?;
    
    // Parse and add contact words
    let mut parsed: QRCodeData = serde_json::from_str(&qr_data)
        .map_err(|e| e.to_string())?;
    parsed.contact_words = contact_words;
    
    serde_json::to_string(&parsed)
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn parse_qr_code(
    qr_data: String,
    state: State<'_, AppState>
) -> Result<QRCodeData, String> {
    let crypto = state.crypto.as_ref();
    crypto.parse_qr_code_data(&qr_data)
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn validate_contact_message(
    message: String,
    state: State<'_, AppState>
) -> Result<bool, String> {
    let crypto = state.crypto.as_ref();
    Ok(crypto.validate_contact_message(&message))
}

// Database Commands
#[tauri::command]
pub async fn get_contacts(state: State<'_, AppState>) -> Result<Vec<Contact>, String> {
    let db = state.database.lock().await;
    db.get_all_contacts().await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn add_contact(
    contact: Contact,
    state: State<'_, AppState>
) -> Result<(), String> {
    let db = state.database.lock().await;
    db.insert_contact(&contact).await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn get_messages(
    contact_id: String,
    state: State<'_, AppState>
) -> Result<Vec<Message>, String> {
    let db = state.database.lock().await;
    db.get_messages_for_contact(&contact_id).await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn send_message(
    contact_id: String,
    content: String,
    state: State<'_, AppState>
) -> Result<String, String> {
    let message = Message {
        id: uuid::Uuid::new_v4().to_string(),
        contact_id: contact_id.clone(),
        content: content.clone(),
        is_from_me: true,
        timestamp: chrono::Utc::now().timestamp(),
        message_type: "text".to_string(),
        delivery_status: "sending".to_string(),
        encrypted_content: String::new(),
        created_at: chrono::Utc::now().timestamp(),
    };

    // Save to database
    {
        let db = state.database.lock().await;
        db.insert_message(&message).await
            .map_err(|e| e.to_string())?;
    }

    // Send via network
    {
        let mut network = state.network.lock().await;
        network.send_message(&message).await
            .map_err(|e| e.to_string())?;
    }

    Ok(message.id)
}

#[tauri::command]
pub async fn get_user_profile(state: State<'_, AppState>) -> Result<Option<UserProfile>, String> {
    let db = state.database.lock().await;
    db.get_user_profile().await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn update_user_profile(
    profile: UserProfile,
    state: State<'_, AppState>
) -> Result<(), String> {
    let db = state.database.lock().await;
    db.save_user_profile(&profile).await
        .map_err(|e| e.to_string())
}

// Network Commands
#[tauri::command]
pub async fn connect_to_server(
    server_url: String,
    state: State<'_, AppState>
) -> Result<(), String> {
    let mut network = state.network.lock().await;
    network.connect(&server_url).await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn disconnect_from_server(state: State<'_, AppState>) -> Result<(), String> {
    let mut network = state.network.lock().await;
    network.disconnect().await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn get_server_status(state: State<'_, AppState>) -> Result<ServerStatus, String> {
    let network = state.network.lock().await;
    network.get_status().await
        .map_err(|e| e.to_string())
}

// Voice Call Commands
#[tauri::command]
pub async fn initiate_voice_call(
    contact_id: String,
    state: State<'_, AppState>
) -> Result<String, String> {
    let mut voice = state.voice.lock().await;
    
    // Get contact from database
    let contact = {
        let db = state.database.lock().await;
        db.get_contact_by_id(&contact_id).await
            .map_err(|e| e.to_string())?
            .ok_or("Contact not found")?
    };

    voice.initiate_call(&contact).await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn accept_voice_call(
    call_id: String,
    state: State<'_, AppState>
) -> Result<(), String> {
    let mut voice = state.voice.lock().await;
    voice.accept_call(&call_id).await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn reject_voice_call(
    call_id: String,
    state: State<'_, AppState>
) -> Result<(), String> {
    let mut voice = state.voice.lock().await;
    voice.reject_call(&call_id).await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn end_voice_call(state: State<'_, AppState>) -> Result<(), String> {
    let mut voice = state.voice.lock().await;
    voice.end_call().await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn get_call_status(state: State<'_, AppState>) -> Result<CallStatus, String> {
    let voice = state.voice.lock().await;
    voice.get_status().await
        .map_err(|e| e.to_string())
}

// Utility Commands
#[tauri::command]
pub async fn export_keys(
    password: String,
    state: State<'_, AppState>
) -> Result<String, String> {
    let db = state.database.lock().await;
    let profile = db.get_user_profile().await
        .map_err(|e| e.to_string())?
        .ok_or("No user profile found")?;

    // Encrypt and export keys
    let export_data = serde_json::json!({
        "version": "1.0",
        "contact_code": profile.contact_code,
        "secret_words": profile.secret_words,
        "public_key": profile.public_key,
        "private_key": profile.private_key,
        "device_id": profile.device_id,
        "created_at": profile.created_at
    });

    // TODO: Encrypt with password
    Ok(export_data.to_string())
}

#[tauri::command]
pub async fn import_keys(
    encrypted_data: String,
    password: String,
    state: State<'_, AppState>
) -> Result<(), String> {
    // TODO: Decrypt with password
    let import_data: Value = serde_json::from_str(&encrypted_data)
        .map_err(|e| e.to_string())?;

    let profile = UserProfile {
        id: "user_profile".to_string(),
        contact_code: import_data["contact_code"].as_array()
            .ok_or("Invalid contact_code")?
            .iter()
            .map(|v| v.as_str().unwrap_or("").to_string())
            .collect(),
        secret_words: import_data["secret_words"].as_array()
            .ok_or("Invalid secret_words")?
            .iter()
            .map(|v| v.as_str().unwrap_or("").to_string())
            .collect(),
        public_key: import_data["public_key"].as_str()
            .ok_or("Invalid public_key")?.to_string(),
        private_key: import_data["private_key"].as_str()
            .ok_or("Invalid private_key")?.to_string(),
        device_id: import_data["device_id"].as_str()
            .ok_or("Invalid device_id")?.to_string(),
        display_name: "Me".to_string(),
        status: "online".to_string(),
        custom_message: String::new(),
        created_at: import_data["created_at"].as_i64().unwrap_or(0),
    };

    let db = state.database.lock().await;
    db.save_user_profile(&profile).await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn get_device_info() -> Result<DeviceInfo, String> {
    Ok(DeviceInfo {
        platform: std::env::consts::OS.to_string(),
        arch: std::env::consts::ARCH.to_string(),
        version: env!("CARGO_PKG_VERSION").to_string(),
        build_date: chrono::Utc::now().timestamp(),
    })
}

#[tauri::command]
pub async fn check_for_updates() -> Result<UpdateInfo, String> {
    // TODO: Implement update checking
    Ok(UpdateInfo {
        available: false,
        version: env!("CARGO_PKG_VERSION").to_string(),
        download_url: None,
        release_notes: None,
    })
}
