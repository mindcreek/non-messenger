use aes_gcm::{Aes256Gcm, Key, Nonce, aead::{Aead, NewAead}};
use bip39::{Mnemonic, Language, MnemonicType};
use pbkdf2::{pbkdf2_hmac};
use rand::{RngCore, rngs::OsRng};
use rsa::{RsaPrivateKey, RsaPublicKey, PaddingScheme, PublicKey, PublicKeyParts};
use sha2::Sha256;
use serde::{Deserialize, Serialize};
use anyhow::{Result, anyhow};
use base64::{Engine as _, engine::general_purpose};

const RSA_KEY_SIZE: usize = 4096;
const AES_KEY_SIZE: usize = 32;
const PBKDF2_ITERATIONS: u32 = 100_000;
const CONTACT_MESSAGE_LENGTH: usize = 256;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct KeyPair {
    pub public_key: String,
    pub private_key: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EncryptedMessage {
    pub encrypted_message: String,
    pub encrypted_key: String,
    pub iv: String,
    pub auth_tag: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct QRCodeData {
    pub version: String,
    pub r#type: String,
    pub public_key: String,
    pub device_id: String,
    pub contact_words: Vec<String>,
    pub timestamp: u64,
}

pub struct NonMessengerCrypto {
    rng: OsRng,
}

impl NonMessengerCrypto {
    pub fn new() -> Self {
        Self {
            rng: OsRng,
        }
    }

    /// Generate a 4096-bit RSA key pair for maximum security
    pub fn generate_rsa_key_pair(&mut self) -> Result<KeyPair> {
        let private_key = RsaPrivateKey::new(&mut self.rng, RSA_KEY_SIZE)?;
        let public_key = RsaPublicKey::from(&private_key);

        let private_pem = private_key.to_pkcs8_pem(rsa::pkcs8::LineEnding::LF)?;
        let public_pem = public_key.to_public_key_pem(rsa::pkcs8::LineEnding::LF)?;

        Ok(KeyPair {
            public_key: public_pem,
            private_key: private_pem.to_string(),
        })
    }

    /// Generate 8 BIP39 words for public contact sharing
    pub fn generate_8_word_contact_code(&mut self) -> Result<Vec<String>> {
        let mnemonic = Mnemonic::new(MnemonicType::Words12, Language::English);
        let words: Vec<String> = mnemonic.phrase()
            .split_whitespace()
            .take(8)
            .map(|s| s.to_string())
            .collect();
        
        Ok(words)
    }

    /// Generate 8 BIP39 words for private verification
    pub fn generate_8_word_secret_code(&mut self) -> Result<Vec<String>> {
        let mnemonic = Mnemonic::new(MnemonicType::Words12, Language::English);
        let words: Vec<String> = mnemonic.phrase()
            .split_whitespace()
            .take(8)
            .map(|s| s.to_string())
            .collect();
        
        Ok(words)
    }

    /// Generate deterministic RSA key pair from 8 contact words (2048-bit for initial contact)
    pub fn generate_contact_key_pair(&self, words: &[String]) -> Result<KeyPair> {
        if words.len() != 8 {
            return Err(anyhow!("Contact code must be exactly 8 words"));
        }

        let seed = self.derive_key_from_words(words)?;
        let mut rng = rand_chacha::ChaCha20Rng::from_seed(seed);
        
        let private_key = RsaPrivateKey::new(&mut rng, 2048)?;
        let public_key = RsaPublicKey::from(&private_key);

        let private_pem = private_key.to_pkcs8_pem(rsa::pkcs8::LineEnding::LF)?;
        let public_pem = public_key.to_public_key_pem(rsa::pkcs8::LineEnding::LF)?;

        Ok(KeyPair {
            public_key: public_pem,
            private_key: private_pem.to_string(),
        })
    }

    /// Generate deterministic RSA key pair from all 16 words (4096-bit for full security)
    pub fn generate_full_key_pair(&self, words: &[String]) -> Result<KeyPair> {
        if words.len() != 16 {
            return Err(anyhow!("Full key generation requires 16 words"));
        }

        let seed = self.derive_key_from_words(words)?;
        let mut rng = rand_chacha::ChaCha20Rng::from_seed(seed);
        
        let private_key = RsaPrivateKey::new(&mut rng, RSA_KEY_SIZE)?;
        let public_key = RsaPublicKey::from(&private_key);

        let private_pem = private_key.to_pkcs8_pem(rsa::pkcs8::LineEnding::LF)?;
        let public_pem = public_key.to_public_key_pem(rsa::pkcs8::LineEnding::LF)?;

        Ok(KeyPair {
            public_key: public_pem,
            private_key: private_pem.to_string(),
        })
    }

    /// Derive cryptographic key from BIP39 words
    pub fn derive_key_from_words(&self, words: &[String]) -> Result<[u8; 32]> {
        let phrase = words.join(" ");
        let mnemonic = Mnemonic::from_phrase(&phrase, Language::English)?;
        let seed = mnemonic.to_seed("");
        
        let mut key = [0u8; 32];
        pbkdf2_hmac::<Sha256>(&seed, b"nonmessenger-salt", PBKDF2_ITERATIONS, &mut key);
        
        Ok(key)
    }

    /// Encrypt message using hybrid RSA + AES-256-GCM encryption
    pub fn encrypt_message(&mut self, message: &str, public_key_pem: &str) -> Result<EncryptedMessage> {
        // Generate random AES key and nonce
        let mut aes_key = [0u8; AES_KEY_SIZE];
        let mut nonce_bytes = [0u8; 12]; // GCM standard nonce size
        self.rng.fill_bytes(&mut aes_key);
        self.rng.fill_bytes(&mut nonce_bytes);

        // Encrypt message with AES-256-GCM
        let key = Key::from_slice(&aes_key);
        let cipher = Aes256Gcm::new(key);
        let nonce = Nonce::from_slice(&nonce_bytes);
        
        let ciphertext = cipher.encrypt(nonce, message.as_bytes())
            .map_err(|e| anyhow!("AES encryption failed: {}", e))?;

        // Encrypt AES key with RSA
        let public_key = RsaPublicKey::from_public_key_pem(public_key_pem)?;
        let padding = PaddingScheme::new_oaep::<Sha256>();
        let encrypted_aes_key = public_key.encrypt(&mut self.rng, padding, &aes_key)?;

        Ok(EncryptedMessage {
            encrypted_message: general_purpose::STANDARD.encode(&ciphertext[..ciphertext.len()-16]),
            encrypted_key: general_purpose::STANDARD.encode(&encrypted_aes_key),
            iv: general_purpose::STANDARD.encode(&nonce_bytes),
            auth_tag: general_purpose::STANDARD.encode(&ciphertext[ciphertext.len()-16..]),
        })
    }

    /// Decrypt message using hybrid RSA + AES-256-GCM decryption
    pub fn decrypt_message(&self, encrypted_data: &EncryptedMessage, private_key_pem: &str) -> Result<String> {
        // Decrypt AES key with RSA
        let private_key = RsaPrivateKey::from_pkcs8_pem(private_key_pem)?;
        let padding = PaddingScheme::new_oaep::<Sha256>();
        let encrypted_aes_key = general_purpose::STANDARD.decode(&encrypted_data.encrypted_key)?;
        let aes_key = private_key.decrypt(padding, &encrypted_aes_key)?;

        // Decrypt message with AES-256-GCM
        let key = Key::from_slice(&aes_key);
        let cipher = Aes256Gcm::new(key);
        let nonce_bytes = general_purpose::STANDARD.decode(&encrypted_data.iv)?;
        let nonce = Nonce::from_slice(&nonce_bytes);
        
        let mut ciphertext = general_purpose::STANDARD.decode(&encrypted_data.encrypted_message)?;
        let auth_tag = general_purpose::STANDARD.decode(&encrypted_data.auth_tag)?;
        ciphertext.extend_from_slice(&auth_tag);

        let plaintext = cipher.decrypt(nonce, ciphertext.as_ref())
            .map_err(|e| anyhow!("AES decryption failed: {}", e))?;

        Ok(String::from_utf8(plaintext)?)
    }

    /// Generate QR code data for contact sharing
    pub fn generate_qr_code_data(&self, public_key: &str, device_id: &str) -> Result<String> {
        let qr_data = QRCodeData {
            version: "1.0".to_string(),
            r#type: "nonmessenger_contact".to_string(),
            public_key: public_key.to_string(),
            device_id: device_id.to_string(),
            contact_words: vec![], // Will be filled by caller
            timestamp: std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)?
                .as_secs(),
        };

        Ok(serde_json::to_string(&qr_data)?)
    }

    /// Parse QR code data
    pub fn parse_qr_code_data(&self, qr_data: &str) -> Result<QRCodeData> {
        let parsed: QRCodeData = serde_json::from_str(qr_data)
            .map_err(|_| anyhow!("Invalid QR code data"))?;
        
        if parsed.r#type != "nonmessenger_contact" {
            return Err(anyhow!("Invalid QR code type"));
        }

        Ok(parsed)
    }

    /// Validate 256-character contact verification message
    pub fn validate_contact_message(&self, message: &str) -> bool {
        message.len() == CONTACT_MESSAGE_LENGTH
    }

    /// Generate unique device ID
    pub fn generate_device_id(&mut self) -> String {
        let mut bytes = [0u8; 16];
        self.rng.fill_bytes(&mut bytes);
        hex::encode(bytes)
    }

    /// Generate random AES key for voice calls
    pub fn generate_aes_key(&mut self) -> [u8; 32] {
        let mut key = [0u8; 32];
        self.rng.fill_bytes(&mut key);
        key
    }

    /// Encrypt AES key with RSA for voice call key exchange
    pub fn encrypt_aes_key(&mut self, aes_key: &[u8], public_key_pem: &str) -> Result<String> {
        let public_key = RsaPublicKey::from_public_key_pem(public_key_pem)?;
        let padding = PaddingScheme::new_oaep::<Sha256>();
        let encrypted = public_key.encrypt(&mut self.rng, padding, aes_key)?;
        Ok(general_purpose::STANDARD.encode(&encrypted))
    }

    /// Decrypt AES key with RSA for voice call key exchange
    pub fn decrypt_aes_key(&self, encrypted_key: &str, private_key_pem: &str) -> Result<Vec<u8>> {
        let private_key = RsaPrivateKey::from_pkcs8_pem(private_key_pem)?;
        let padding = PaddingScheme::new_oaep::<Sha256>();
        let encrypted_bytes = general_purpose::STANDARD.decode(encrypted_key)?;
        let decrypted = private_key.decrypt(padding, &encrypted_bytes)?;
        Ok(decrypted)
    }
}
