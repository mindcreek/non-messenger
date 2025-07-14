// Prevents additional console window on Windows in release, DO NOT REMOVE!!
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use tauri::{
    CustomMenuItem, Manager, SystemTray, SystemTrayEvent, SystemTrayMenu, SystemTrayMenuItem,
    WindowBuilder, WindowUrl,
};
use std::sync::Arc;
use tokio::sync::Mutex;

mod crypto;
mod database;
mod network;
mod voice;
mod commands;
mod models;
mod utils;

use crypto::NonMessengerCrypto;
use database::Database;
use network::MessagePoolClient;
use voice::VoiceCallManager;

pub struct AppState {
    pub crypto: Arc<NonMessengerCrypto>,
    pub database: Arc<Mutex<Database>>,
    pub network: Arc<Mutex<MessagePoolClient>>,
    pub voice: Arc<Mutex<VoiceCallManager>>,
}

#[tokio::main]
async fn main() {
    env_logger::init();
    
    // Initialize application state
    let crypto = Arc::new(NonMessengerCrypto::new());
    let database = Arc::new(Mutex::new(Database::new().await.expect("Failed to initialize database")));
    let network = Arc::new(Mutex::new(MessagePoolClient::new()));
    let voice = Arc::new(Mutex::new(VoiceCallManager::new()));
    
    let app_state = AppState {
        crypto,
        database,
        network,
        voice,
    };

    // Create system tray
    let quit = CustomMenuItem::new("quit".to_string(), "Quit NonMessenger");
    let show = CustomMenuItem::new("show".to_string(), "Show Window");
    let hide = CustomMenuItem::new("hide".to_string(), "Hide Window");
    let tray_menu = SystemTrayMenu::new()
        .add_item(show)
        .add_item(hide)
        .add_native_item(SystemTrayMenuItem::Separator)
        .add_item(quit);

    let system_tray = SystemTray::new().with_menu(tray_menu);

    tauri::Builder::default()
        .manage(app_state)
        .system_tray(system_tray)
        .on_system_tray_event(|app, event| match event {
            SystemTrayEvent::LeftClick {
                position: _,
                size: _,
                ..
            } => {
                let window = app.get_window("main").unwrap();
                if window.is_visible().unwrap() {
                    window.hide().unwrap();
                } else {
                    window.show().unwrap();
                    window.set_focus().unwrap();
                }
            }
            SystemTrayEvent::MenuItemClick { id, .. } => match id.as_str() {
                "quit" => {
                    std::process::exit(0);
                }
                "show" => {
                    let window = app.get_window("main").unwrap();
                    window.show().unwrap();
                    window.set_focus().unwrap();
                }
                "hide" => {
                    let window = app.get_window("main").unwrap();
                    window.hide().unwrap();
                }
                _ => {}
            },
            _ => {}
        })
        .invoke_handler(tauri::generate_handler![
            commands::generate_contact_code,
            commands::generate_key_pair,
            commands::encrypt_message,
            commands::decrypt_message,
            commands::get_contacts,
            commands::add_contact,
            commands::send_message,
            commands::get_messages,
            commands::connect_to_server,
            commands::disconnect_from_server,
            commands::get_server_status,
            commands::initiate_voice_call,
            commands::accept_voice_call,
            commands::reject_voice_call,
            commands::end_voice_call,
            commands::get_call_status,
            commands::generate_qr_code,
            commands::parse_qr_code,
            commands::export_keys,
            commands::import_keys,
            commands::get_user_profile,
            commands::update_user_profile,
            commands::validate_contact_message,
            commands::get_device_info,
            commands::check_for_updates,
        ])
        .setup(|app| {
            // Create main window
            let _window = WindowBuilder::new(
                app,
                "main",
                WindowUrl::App("index.html".into())
            )
            .title("NonMessenger")
            .inner_size(1200.0, 800.0)
            .min_inner_size(800.0, 600.0)
            .center()
            .build()?;

            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_app_initialization() {
        let crypto = Arc::new(NonMessengerCrypto::new());
        assert!(crypto.generate_rsa_key_pair().is_ok());
    }

    #[tokio::test]
    async fn test_database_initialization() {
        let db = Database::new().await;
        assert!(db.is_ok());
    }

    #[test]
    fn test_crypto_operations() {
        let crypto = NonMessengerCrypto::new();
        let key_pair = crypto.generate_rsa_key_pair().unwrap();
        
        let message = "Test message for encryption";
        let encrypted = crypto.encrypt_message(message, &key_pair.public_key).unwrap();
        let decrypted = crypto.decrypt_message(&encrypted, &key_pair.private_key).unwrap();
        
        assert_eq!(message, decrypted);
    }

    #[test]
    fn test_contact_code_generation() {
        let crypto = NonMessengerCrypto::new();
        let contact_words = crypto.generate_8_word_contact_code().unwrap();
        let secret_words = crypto.generate_8_word_secret_code().unwrap();
        
        assert_eq!(contact_words.len(), 8);
        assert_eq!(secret_words.len(), 8);
        
        // Ensure words are different
        assert_ne!(contact_words, secret_words);
    }

    #[test]
    fn test_deterministic_key_generation() {
        let crypto = NonMessengerCrypto::new();
        let words = vec![
            "abandon".to_string(), "ability".to_string(), "able".to_string(), "about".to_string(),
            "above".to_string(), "absent".to_string(), "absorb".to_string(), "abstract".to_string()
        ];
        
        let key_pair1 = crypto.generate_contact_key_pair(&words).unwrap();
        let key_pair2 = crypto.generate_contact_key_pair(&words).unwrap();
        
        // Keys should be deterministic (same input = same output)
        assert_eq!(key_pair1.public_key, key_pair2.public_key);
        assert_eq!(key_pair1.private_key, key_pair2.private_key);
    }

    #[test]
    fn test_qr_code_generation() {
        let crypto = NonMessengerCrypto::new();
        let key_pair = crypto.generate_rsa_key_pair().unwrap();
        let device_id = crypto.generate_device_id();
        
        let qr_data = crypto.generate_qr_code_data(&key_pair.public_key, &device_id).unwrap();
        let parsed = crypto.parse_qr_code_data(&qr_data).unwrap();
        
        assert_eq!(parsed.public_key, key_pair.public_key);
        assert_eq!(parsed.device_id, device_id);
    }

    #[test]
    fn test_message_validation() {
        let crypto = NonMessengerCrypto::new();
        
        let valid_message = "A".repeat(256);
        let invalid_message = "A".repeat(255);
        
        assert!(crypto.validate_contact_message(&valid_message));
        assert!(!crypto.validate_contact_message(&invalid_message));
    }
}
