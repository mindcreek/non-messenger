use crate::models::*;
use anyhow::{Result, anyhow};
use futures_util::{SinkExt, StreamExt};
use reqwest::Client;
use serde_json::Value;
use std::sync::Arc;
use tokio::sync::Mutex;
use tokio_tungstenite::{connect_async, tungstenite::Message as WsMessage};
use url::Url;

pub struct MessagePoolClient {
    client: Client,
    websocket: Arc<Mutex<Option<tokio_tungstenite::WebSocketStream<tokio_tungstenite::MaybeTlsStream<tokio::net::TcpStream>>>>>,
    server_url: Arc<Mutex<Option<String>>>,
    is_connected: Arc<Mutex<bool>>,
}

impl MessagePoolClient {
    pub fn new() -> Self {
        Self {
            client: Client::new(),
            websocket: Arc::new(Mutex::new(None)),
            server_url: Arc::new(Mutex::new(None)),
            is_connected: Arc::new(Mutex::new(false)),
        }
    }

    pub async fn connect(&mut self, server_url: &str) -> Result<()> {
        // Store server URL
        {
            let mut url = self.server_url.lock().await;
            *url = Some(server_url.to_string());
        }

        // Test HTTP connection first
        let health_url = format!("{}/health", server_url);
        let response = self.client.get(&health_url).send().await?;
        
        if !response.status().is_success() {
            return Err(anyhow!("Server health check failed"));
        }

        // Establish WebSocket connection
        let ws_url = server_url.replace("http://", "ws://").replace("https://", "wss://");
        let url = Url::parse(&ws_url)?;
        
        let (ws_stream, _) = connect_async(url).await?;
        
        {
            let mut websocket = self.websocket.lock().await;
            *websocket = Some(ws_stream);
        }

        {
            let mut connected = self.is_connected.lock().await;
            *connected = true;
        }

        // Start message listening loop
        self.start_message_listener().await;

        Ok(())
    }

    pub async fn disconnect(&mut self) -> Result<()> {
        {
            let mut websocket = self.websocket.lock().await;
            if let Some(ws) = websocket.take() {
                // Close WebSocket connection
                drop(ws);
            }
        }

        {
            let mut connected = self.is_connected.lock().await;
            *connected = false;
        }

        {
            let mut url = self.server_url.lock().await;
            *url = None;
        }

        Ok(())
    }

    pub async fn send_message(&self, message: &Message) -> Result<()> {
        let server_url = {
            let url = self.server_url.lock().await;
            url.clone().ok_or_else(|| anyhow!("Not connected to server"))?
        };

        let envelope = MessageEnvelope {
            id: message.id.clone(),
            recipient_contact_code: message.contact_id.clone(), // This should be the actual contact code
            encrypted_message: crate::crypto::EncryptedMessage {
                encrypted_message: message.encrypted_content.clone(),
                encrypted_key: String::new(), // Should be filled with actual encrypted key
                iv: String::new(),
                auth_tag: String::new(),
            },
            timestamp: message.timestamp,
            ttl: 86400000, // 24 hours
            message_type: message.message_type.clone(),
        };

        let response = self.client
            .post(&format!("{}/api/message", server_url))
            .json(&envelope)
            .send()
            .await?;

        if !response.status().is_success() {
            return Err(anyhow!("Failed to send message: {}", response.status()));
        }

        Ok(())
    }

    pub async fn get_messages(&self, contact_code: &str) -> Result<Vec<MessageEnvelope>> {
        let server_url = {
            let url = self.server_url.lock().await;
            url.clone().ok_or_else(|| anyhow!("Not connected to server"))?
        };

        let response = self.client
            .get(&format!("{}/api/messages/{}", server_url, contact_code))
            .send()
            .await?;

        if !response.status().is_success() {
            return Err(anyhow!("Failed to get messages: {}", response.status()));
        }

        let json: Value = response.json().await?;
        let messages: Vec<MessageEnvelope> = serde_json::from_value(json["messages"].clone())?;

        Ok(messages)
    }

    pub async fn send_voice_call_init(&self, call_id: &str, recipient_contact_code: &str, encrypted_key: &str) -> Result<()> {
        let message = VoiceCallMessage {
            r#type: "VOICE_CALL_INIT".to_string(),
            id: uuid::Uuid::new_v4().to_string(),
            timestamp: chrono::Utc::now().timestamp(),
            call_id: call_id.to_string(),
            caller_id: Some("self".to_string()), // Should be actual user ID
            recipient_id: Some(recipient_contact_code.to_string()),
            version: "1.0".to_string(),
        };

        self.send_websocket_message(&message).await
    }

    pub async fn send_voice_call_accept(&self, call_id: &str) -> Result<()> {
        let message = VoiceCallMessage {
            r#type: "VOICE_CALL_ACCEPT".to_string(),
            id: uuid::Uuid::new_v4().to_string(),
            timestamp: chrono::Utc::now().timestamp(),
            call_id: call_id.to_string(),
            caller_id: None,
            recipient_id: Some("self".to_string()),
            version: "1.0".to_string(),
        };

        self.send_websocket_message(&message).await
    }

    pub async fn send_voice_call_reject(&self, call_id: &str) -> Result<()> {
        let message = VoiceCallMessage {
            r#type: "VOICE_CALL_REJECT".to_string(),
            id: uuid::Uuid::new_v4().to_string(),
            timestamp: chrono::Utc::now().timestamp(),
            call_id: call_id.to_string(),
            caller_id: None,
            recipient_id: Some("self".to_string()),
            version: "1.0".to_string(),
        };

        self.send_websocket_message(&message).await
    }

    pub async fn send_voice_call_end(&self, call_id: &str) -> Result<()> {
        let message = VoiceCallMessage {
            r#type: "VOICE_CALL_END".to_string(),
            id: uuid::Uuid::new_v4().to_string(),
            timestamp: chrono::Utc::now().timestamp(),
            call_id: call_id.to_string(),
            caller_id: None,
            recipient_id: None,
            version: "1.0".to_string(),
        };

        self.send_websocket_message(&message).await
    }

    pub async fn send_voice_data(&self, call_id: &str, encrypted_audio_data: &str, sequence_number: i32) -> Result<()> {
        let message = VoiceDataMessage {
            r#type: "VOICE_DATA".to_string(),
            id: uuid::Uuid::new_v4().to_string(),
            timestamp: chrono::Utc::now().timestamp(),
            call_id: call_id.to_string(),
            encrypted_audio_data: encrypted_audio_data.to_string(),
            sequence_number,
            version: "1.0".to_string(),
        };

        self.send_websocket_message(&message).await
    }

    pub async fn register_user(&self, contact_code: &str) -> Result<()> {
        let message = serde_json::json!({
            "type": "register_user",
            "contactCode": contact_code
        });

        self.send_websocket_message(&message).await
    }

    pub async fn get_status(&self) -> Result<ServerStatus> {
        let server_url = {
            let url = self.server_url.lock().await;
            url.clone().ok_or_else(|| anyhow!("Not connected to server"))?
        };

        let is_connected = {
            let connected = self.is_connected.lock().await;
            *connected
        };

        let start_time = std::time::Instant::now();
        let response = self.client
            .get(&format!("{}/health", server_url))
            .send()
            .await?;
        let response_time = start_time.elapsed().as_millis() as i64;

        if response.status().is_success() {
            let json: Value = response.json().await?;
            Ok(ServerStatus {
                url: server_url,
                is_connected,
                last_ping: chrono::Utc::now().timestamp(),
                response_time,
                message_pool_size: json["messagePoolSize"].as_i64().unwrap_or(0) as i32,
                active_sessions: json["activeSessions"].as_i64().unwrap_or(0) as i32,
            })
        } else {
            Err(anyhow!("Server health check failed"))
        }
    }

    async fn send_websocket_message<T: serde::Serialize>(&self, message: &T) -> Result<()> {
        let mut websocket = self.websocket.lock().await;
        
        if let Some(ws) = websocket.as_mut() {
            let json = serde_json::to_string(message)?;
            ws.send(WsMessage::Text(json)).await?;
            Ok(())
        } else {
            Err(anyhow!("WebSocket not connected"))
        }
    }

    async fn start_message_listener(&self) {
        let websocket = Arc::clone(&self.websocket);
        let is_connected = Arc::clone(&self.is_connected);

        tokio::spawn(async move {
            loop {
                let mut ws_guard = websocket.lock().await;
                
                if let Some(ws) = ws_guard.as_mut() {
                    match ws.next().await {
                        Some(Ok(WsMessage::Text(text))) => {
                            // Handle incoming message
                            if let Ok(json) = serde_json::from_str::<Value>(&text) {
                                Self::handle_incoming_message(json).await;
                            }
                        }
                        Some(Ok(WsMessage::Close(_))) => {
                            log::info!("WebSocket connection closed");
                            break;
                        }
                        Some(Err(e)) => {
                            log::error!("WebSocket error: {}", e);
                            break;
                        }
                        None => {
                            log::info!("WebSocket stream ended");
                            break;
                        }
                        _ => {}
                    }
                } else {
                    break;
                }
            }

            // Mark as disconnected
            let mut connected = is_connected.lock().await;
            *connected = false;
        });
    }

    async fn handle_incoming_message(message: Value) {
        let message_type = message["type"].as_str().unwrap_or("");
        
        match message_type {
            "new_message" => {
                // Handle new message
                log::info!("Received new message");
            }
            "voice_call_init" => {
                // Handle incoming voice call
                log::info!("Received voice call initiation");
            }
            "voice_call_accept" => {
                // Handle call acceptance
                log::info!("Voice call accepted");
            }
            "voice_call_reject" => {
                // Handle call rejection
                log::info!("Voice call rejected");
            }
            "voice_call_end" => {
                // Handle call end
                log::info!("Voice call ended");
            }
            "voice_data" => {
                // Handle voice data
                log::debug!("Received voice data packet");
            }
            "status_update" => {
                // Handle status update
                log::info!("Received status update");
            }
            _ => {
                log::warn!("Unknown message type: {}", message_type);
            }
        }
    }
}
