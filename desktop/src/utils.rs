use anyhow::{Result, anyhow};
use std::path::PathBuf;
use dirs::{config_dir, data_dir, cache_dir};

pub struct AppPaths;

impl AppPaths {
    pub fn get_config_dir() -> Result<PathBuf> {
        let mut path = config_dir()
            .ok_or_else(|| anyhow!("Could not find config directory"))?;
        path.push("NonMessenger");
        std::fs::create_dir_all(&path)?;
        Ok(path)
    }

    pub fn get_data_dir() -> Result<PathBuf> {
        let mut path = data_dir()
            .ok_or_else(|| anyhow!("Could not find data directory"))?;
        path.push("NonMessenger");
        std::fs::create_dir_all(&path)?;
        Ok(path)
    }

    pub fn get_cache_dir() -> Result<PathBuf> {
        let mut path = cache_dir()
            .ok_or_else(|| anyhow!("Could not find cache directory"))?;
        path.push("NonMessenger");
        std::fs::create_dir_all(&path)?;
        Ok(path)
    }

    pub fn get_logs_dir() -> Result<PathBuf> {
        let mut path = Self::get_data_dir()?;
        path.push("logs");
        std::fs::create_dir_all(&path)?;
        Ok(path)
    }

    pub fn get_keys_dir() -> Result<PathBuf> {
        let mut path = Self::get_data_dir()?;
        path.push("keys");
        std::fs::create_dir_all(&path)?;
        Ok(path)
    }

    pub fn get_exports_dir() -> Result<PathBuf> {
        let mut path = Self::get_data_dir()?;
        path.push("exports");
        std::fs::create_dir_all(&path)?;
        Ok(path)
    }
}

pub struct SystemInfo;

impl SystemInfo {
    pub fn get_platform() -> String {
        std::env::consts::OS.to_string()
    }

    pub fn get_architecture() -> String {
        std::env::consts::ARCH.to_string()
    }

    pub fn get_version() -> String {
        env!("CARGO_PKG_VERSION").to_string()
    }

    pub fn get_build_info() -> String {
        format!("{} {} v{}", 
            Self::get_platform(), 
            Self::get_architecture(), 
            Self::get_version()
        )
    }

    pub fn is_debug_build() -> bool {
        cfg!(debug_assertions)
    }
}

pub struct Validator;

impl Validator {
    pub fn validate_contact_code(words: &[String]) -> bool {
        words.len() == 8 && words.iter().all(|word| !word.is_empty())
    }

    pub fn validate_secret_words(words: &[String]) -> bool {
        words.len() == 8 && words.iter().all(|word| !word.is_empty())
    }

    pub fn validate_full_words(words: &[String]) -> bool {
        words.len() == 16 && words.iter().all(|word| !word.is_empty())
    }

    pub fn validate_contact_message(message: &str) -> bool {
        message.len() == 256
    }

    pub fn validate_device_id(device_id: &str) -> bool {
        device_id.len() == 32 && device_id.chars().all(|c| c.is_ascii_hexdigit())
    }

    pub fn validate_public_key(key: &str) -> bool {
        key.starts_with("-----BEGIN PUBLIC KEY-----") && 
        key.ends_with("-----END PUBLIC KEY-----")
    }

    pub fn validate_private_key(key: &str) -> bool {
        key.starts_with("-----BEGIN PRIVATE KEY-----") && 
        key.ends_with("-----END PRIVATE KEY-----")
    }

    pub fn validate_server_url(url: &str) -> bool {
        url.starts_with("http://") || url.starts_with("https://") ||
        url.starts_with("ws://") || url.starts_with("wss://")
    }
}

pub struct Formatter;

impl Formatter {
    pub fn format_timestamp(timestamp: i64) -> String {
        let datetime = chrono::DateTime::from_timestamp(timestamp, 0)
            .unwrap_or_else(|| chrono::Utc::now());
        datetime.format("%Y-%m-%d %H:%M:%S UTC").to_string()
    }

    pub fn format_duration(seconds: i64) -> String {
        let hours = seconds / 3600;
        let minutes = (seconds % 3600) / 60;
        let secs = seconds % 60;

        if hours > 0 {
            format!("{}:{:02}:{:02}", hours, minutes, secs)
        } else {
            format!("{}:{:02}", minutes, secs)
        }
    }

    pub fn format_file_size(bytes: u64) -> String {
        const UNITS: &[&str] = &["B", "KB", "MB", "GB", "TB"];
        let mut size = bytes as f64;
        let mut unit_index = 0;

        while size >= 1024.0 && unit_index < UNITS.len() - 1 {
            size /= 1024.0;
            unit_index += 1;
        }

        if unit_index == 0 {
            format!("{} {}", bytes, UNITS[unit_index])
        } else {
            format!("{:.1} {}", size, UNITS[unit_index])
        }
    }

    pub fn format_contact_code(words: &[String]) -> String {
        words.join(" ")
    }

    pub fn format_contact_code_numbered(words: &[String]) -> String {
        words.iter()
            .enumerate()
            .map(|(i, word)| format!("{}. {}", i + 1, word))
            .collect::<Vec<_>>()
            .join("\n")
    }

    pub fn truncate_string(s: &str, max_len: usize) -> String {
        if s.len() <= max_len {
            s.to_string()
        } else {
            format!("{}...", &s[..max_len.saturating_sub(3)])
        }
    }
}

pub struct Security;

impl Security {
    pub fn secure_compare(a: &[u8], b: &[u8]) -> bool {
        if a.len() != b.len() {
            return false;
        }

        let mut result = 0u8;
        for (x, y) in a.iter().zip(b.iter()) {
            result |= x ^ y;
        }
        result == 0
    }

    pub fn secure_zero(data: &mut [u8]) {
        for byte in data.iter_mut() {
            *byte = 0;
        }
    }

    pub fn generate_random_string(length: usize) -> String {
        use rand::Rng;
        const CHARSET: &[u8] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        let mut rng = rand::thread_rng();
        
        (0..length)
            .map(|_| {
                let idx = rng.gen_range(0..CHARSET.len());
                CHARSET[idx] as char
            })
            .collect()
    }

    pub fn is_secure_context() -> bool {
        // Check if we're running in a secure environment
        // This is a simplified check - in production, you might want more sophisticated checks
        !cfg!(debug_assertions) || std::env::var("NONMESSENGER_ALLOW_INSECURE").is_err()
    }
}

pub struct Logger;

impl Logger {
    pub fn init() -> Result<()> {
        let logs_dir = AppPaths::get_logs_dir()?;
        let log_file = logs_dir.join("nonmessenger.log");

        env_logger::Builder::from_default_env()
            .target(env_logger::Target::Stdout)
            .init();

        log::info!("NonMessenger Desktop v{} starting", SystemInfo::get_version());
        log::info!("Platform: {}", SystemInfo::get_build_info());
        log::info!("Log file: {}", log_file.display());

        Ok(())
    }

    pub fn log_security_event(event: &str, details: &str) {
        log::warn!("SECURITY EVENT: {} - {}", event, details);
    }

    pub fn log_crypto_operation(operation: &str, success: bool) {
        if success {
            log::debug!("Crypto operation successful: {}", operation);
        } else {
            log::error!("Crypto operation failed: {}", operation);
        }
    }

    pub fn log_network_event(event: &str, server: &str) {
        log::info!("Network event: {} - Server: {}", event, server);
    }

    pub fn log_voice_event(event: &str, call_id: &str) {
        log::info!("Voice call event: {} - Call ID: {}", event, call_id);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_validator_contact_code() {
        let valid_code = vec!["word1".to_string(), "word2".to_string(), "word3".to_string(), "word4".to_string(),
                             "word5".to_string(), "word6".to_string(), "word7".to_string(), "word8".to_string()];
        assert!(Validator::validate_contact_code(&valid_code));

        let invalid_code = vec!["word1".to_string(), "word2".to_string()];
        assert!(!Validator::validate_contact_code(&invalid_code));
    }

    #[test]
    fn test_validator_contact_message() {
        let valid_message = "A".repeat(256);
        assert!(Validator::validate_contact_message(&valid_message));

        let invalid_message = "A".repeat(255);
        assert!(!Validator::validate_contact_message(&invalid_message));
    }

    #[test]
    fn test_formatter_duration() {
        assert_eq!(Formatter::format_duration(65), "1:05");
        assert_eq!(Formatter::format_duration(3665), "1:01:05");
        assert_eq!(Formatter::format_duration(30), "0:30");
    }

    #[test]
    fn test_formatter_file_size() {
        assert_eq!(Formatter::format_file_size(1024), "1.0 KB");
        assert_eq!(Formatter::format_file_size(1048576), "1.0 MB");
        assert_eq!(Formatter::format_file_size(500), "500 B");
    }

    #[test]
    fn test_security_secure_compare() {
        let a = b"hello";
        let b = b"hello";
        let c = b"world";

        assert!(Security::secure_compare(a, b));
        assert!(!Security::secure_compare(a, c));
        assert!(!Security::secure_compare(a, b"hell")); // Different lengths
    }

    #[test]
    fn test_security_random_string() {
        let s1 = Security::generate_random_string(10);
        let s2 = Security::generate_random_string(10);

        assert_eq!(s1.len(), 10);
        assert_eq!(s2.len(), 10);
        assert_ne!(s1, s2); // Very unlikely to be the same
    }
}
