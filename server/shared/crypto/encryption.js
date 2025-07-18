const crypto = require('crypto');
const { generateMnemonic, mnemonicToSeedSync } = require('bip39');

class NonMessengerCrypto {
    constructor() {
        this.RSA_KEY_SIZE = 4096;
        this.AES_KEY_SIZE = 256;
        this.PBKDF2_ITERATIONS = 100000;
    }

    generateRSAKeyPair() {
        return crypto.generateKeyPairSync('rsa', {
            modulusLength: this.RSA_KEY_SIZE,
            publicKeyEncoding: {
                type: 'spki',
                format: 'pem'
            },
            privateKeyEncoding: {
                type: 'pkcs8',
                format: 'pem'
            }
        });
    }

    generate8WordContactCode() {
        const mnemonic = generateMnemonic(256);
        const words = mnemonic.split(' ');
        return words.slice(0, 8);
    }

    generate8WordSecretCode() {
        const mnemonic = generateMnemonic(256);
        const words = mnemonic.split(' ');
        return words.slice(8, 16);
    }

    deriveKeyFromWords(words) {
        const seed = mnemonicToSeedSync(words.join(' '));
        return crypto.pbkdf2Sync(seed, 'nonmessenger-salt', this.PBKDF2_ITERATIONS, 32, 'sha256');
    }

    generateContactKeyPair(contactWords) {
        if (contactWords.length !== 8) {
            throw new Error('Contact code must be exactly 8 words');
        }
        
        const seed = this.deriveKeyFromWords(contactWords);
        const deterministicRandom = crypto.createHash('sha256').update(seed).digest();
        
        return crypto.generateKeyPairSync('rsa', {
            modulusLength: 2048,
            publicKeyEncoding: {
                type: 'spki',
                format: 'pem'
            },
            privateKeyEncoding: {
                type: 'pkcs8',
                format: 'pem'
            }
        });
    }

    generateFullKeyPair(allWords) {
        if (allWords.length !== 16) {
            throw new Error('Full key generation requires 16 words');
        }
        
        const seed = this.deriveKeyFromWords(allWords);
        const deterministicRandom = crypto.createHash('sha256').update(seed).digest();
        
        return crypto.generateKeyPairSync('rsa', {
            modulusLength: this.RSA_KEY_SIZE,
            publicKeyEncoding: {
                type: 'spki',
                format: 'pem'
            },
            privateKeyEncoding: {
                type: 'pkcs8',
                format: 'pem'
            }
        });
    }

    encryptMessage(message, publicKey) {
        // CONTENT POLICY: Limit message size to 2048 bytes (2KB) for text-only communication
        // This prevents file sharing, image distribution, and other binary content
        const messageBytes = Buffer.byteLength(message, 'utf8');
        if (messageBytes > 2048) {
            throw new Error(`Message too large: ${messageBytes} bytes. Maximum allowed: 2048 bytes (2KB). NonMessenger is designed for secure text communication only.`);
        }

        const aesKey = crypto.randomBytes(32);
        const iv = crypto.randomBytes(16); // Use 16-byte IV for CBC

        // Use AES-256-CBC with explicit IV
        const cipher = crypto.createCipheriv('aes-256-cbc', aesKey, iv);
        let encrypted = cipher.update(message, 'utf8', 'hex');
        encrypted += cipher.final('hex');

        const encryptedAESKey = crypto.publicEncrypt({
            key: publicKey,
            padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
            oaepHash: 'sha256'
        }, aesKey);

        return {
            encryptedMessage: encrypted,
            encryptedKey: encryptedAESKey.toString('base64'),
            iv: iv.toString('base64'),
            authTag: '' // Not used in CBC mode
        };
    }

    decryptMessage(encryptedData, privateKey) {
        const aesKey = crypto.privateDecrypt({
            key: privateKey,
            padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
            oaepHash: 'sha256'
        }, Buffer.from(encryptedData.encryptedKey, 'base64'));

        const iv = Buffer.from(encryptedData.iv, 'base64');
        const decipher = crypto.createDecipheriv('aes-256-cbc', aesKey, iv);

        let decrypted = decipher.update(encryptedData.encryptedMessage, 'hex', 'utf8');
        decrypted += decipher.final('utf8');

        return decrypted;
    }

    generateQRCodeData(publicKey, deviceId, timestamp = Date.now()) {
        return JSON.stringify({
            publicKey,
            deviceId,
            timestamp,
            version: '1.0'
        });
    }

    parseQRCodeData(qrData) {
        try {
            return JSON.parse(qrData);
        } catch (error) {
            throw new Error('Invalid QR code data');
        }
    }

    validateContactMessage(message) {
        if (typeof message !== 'string') {
            return false;
        }
        return message.length === 256;
    }

    generateDeviceId() {
        return crypto.randomBytes(16).toString('hex');
    }
}

module.exports = NonMessengerCrypto;