const crypto = require('crypto');

class SecureKeyStorage {
    constructor() {
        this.STORAGE_VERSION = '1.0';
        this.ENCRYPTION_ALGORITHM = 'aes-256-gcm';
        this.KEY_DERIVATION_ITERATIONS = 100000;
        this.SALT_LENGTH = 32;
        this.IV_LENGTH = 16;
        this.TAG_LENGTH = 16;
    }

    deriveKeyFromPassword(password, salt) {
        return crypto.pbkdf2Sync(
            password, 
            salt, 
            this.KEY_DERIVATION_ITERATIONS, 
            32, 
            'sha256'
        );
    }

    generateSalt() {
        return crypto.randomBytes(this.SALT_LENGTH);
    }

    generateIV() {
        return crypto.randomBytes(this.IV_LENGTH);
    }

    encryptData(data, password) {
        const salt = this.generateSalt();
        const iv = this.generateIV();
        const key = this.deriveKeyFromPassword(password, salt);
        
        const cipher = crypto.createCipherGCM(this.ENCRYPTION_ALGORITHM, key, iv);
        
        let encrypted = cipher.update(JSON.stringify(data), 'utf8', 'hex');
        encrypted += cipher.final('hex');
        
        const authTag = cipher.getAuthTag();
        
        return {
            version: this.STORAGE_VERSION,
            salt: salt.toString('base64'),
            iv: iv.toString('base64'),
            authTag: authTag.toString('base64'),
            encryptedData: encrypted
        };
    }

    decryptData(encryptedContainer, password) {
        if (encryptedContainer.version !== this.STORAGE_VERSION) {
            throw new Error('Unsupported storage version');
        }

        const salt = Buffer.from(encryptedContainer.salt, 'base64');
        const iv = Buffer.from(encryptedContainer.iv, 'base64');
        const authTag = Buffer.from(encryptedContainer.authTag, 'base64');
        const key = this.deriveKeyFromPassword(password, salt);
        
        const decipher = crypto.createDecipherGCM(this.ENCRYPTION_ALGORITHM, key, iv);
        decipher.setAuthTag(authTag);
        
        let decrypted = decipher.update(encryptedContainer.encryptedData, 'hex', 'utf8');
        decrypted += decipher.final('utf8');
        
        return JSON.parse(decrypted);
    }

    createKeyContainer(contactWords, secretWords, keyPairs, deviceId) {
        return {
            contactWords,
            secretWords,
            keyPairs: {
                contactKeyPair: keyPairs.contactKeyPair,
                fullKeyPair: keyPairs.fullKeyPair
            },
            deviceId,
            createdAt: Date.now(),
            lastUsed: Date.now(),
            version: this.STORAGE_VERSION
        };
    }

    updateLastUsed(keyContainer) {
        keyContainer.lastUsed = Date.now();
        return keyContainer;
    }

    validateKeyContainer(container) {
        const requiredFields = [
            'contactWords', 
            'secretWords', 
            'keyPairs', 
            'deviceId', 
            'createdAt', 
            'version'
        ];

        for (const field of requiredFields) {
            if (!container.hasOwnProperty(field)) {
                return false;
            }
        }

        if (!container.keyPairs.contactKeyPair || !container.keyPairs.fullKeyPair) {
            return false;
        }

        if (!Array.isArray(container.contactWords) || container.contactWords.length !== 8) {
            return false;
        }

        if (!Array.isArray(container.secretWords) || container.secretWords.length !== 8) {
            return false;
        }

        return true;
    }

    exportKeyContainer(container, password) {
        if (!this.validateKeyContainer(container)) {
            throw new Error('Invalid key container');
        }

        return this.encryptData(container, password);
    }

    importKeyContainer(encryptedContainer, password) {
        const container = this.decryptData(encryptedContainer, password);
        
        if (!this.validateKeyContainer(container)) {
            throw new Error('Invalid imported key container');
        }

        return container;
    }

    generateBackupPhrase(container) {
        const allWords = [...container.contactWords, ...container.secretWords];
        return allWords.join(' ');
    }

    restoreFromBackupPhrase(backupPhrase, deviceId) {
        const words = backupPhrase.trim().split(/\s+/);
        
        if (words.length !== 16) {
            throw new Error('Backup phrase must contain exactly 16 words');
        }

        const contactWords = words.slice(0, 8);
        const secretWords = words.slice(8, 16);

        return {
            contactWords,
            secretWords,
            deviceId,
            restoredAt: Date.now()
        };
    }

    createSecureHash(data) {
        return crypto.createHash('sha256').update(JSON.stringify(data)).digest('hex');
    }

    verifyIntegrity(container, expectedHash) {
        const computedHash = this.createSecureHash(container);
        return computedHash === expectedHash;
    }

    wipeSecureData(container) {
        if (container.keyPairs && container.keyPairs.fullKeyPair) {
            container.keyPairs.fullKeyPair.privateKey = null;
        }
        if (container.keyPairs && container.keyPairs.contactKeyPair) {
            container.keyPairs.contactKeyPair.privateKey = null;
        }
        container.secretWords = null;
        container.contactWords = null;
    }
}

class PlatformKeyStorage extends SecureKeyStorage {
    constructor(platform) {
        super();
        this.platform = platform;
    }

    async saveToSecureStorage(keyContainer, password, identifier) {
        const encryptedContainer = this.exportKeyContainer(keyContainer, password);
        
        switch (this.platform) {
            case 'android':
                return this.saveToAndroidKeystore(encryptedContainer, identifier);
            case 'ios':
                return this.saveToiOSKeychain(encryptedContainer, identifier);
            case 'desktop':
                return this.saveToDesktopSecureStorage(encryptedContainer, identifier);
            default:
                throw new Error(`Unsupported platform: ${this.platform}`);
        }
    }

    async loadFromSecureStorage(password, identifier) {
        let encryptedContainer;
        
        switch (this.platform) {
            case 'android':
                encryptedContainer = await this.loadFromAndroidKeystore(identifier);
                break;
            case 'ios':
                encryptedContainer = await this.loadFromiOSKeychain(identifier);
                break;
            case 'desktop':
                encryptedContainer = await this.loadFromDesktopSecureStorage(identifier);
                break;
            default:
                throw new Error(`Unsupported platform: ${this.platform}`);
        }

        return this.importKeyContainer(encryptedContainer, password);
    }

    async saveToAndroidKeystore(encryptedContainer, identifier) {
        throw new Error('Android Keystore integration not implemented - requires native code');
    }

    async loadFromAndroidKeystore(identifier) {
        throw new Error('Android Keystore integration not implemented - requires native code');
    }

    async saveToiOSKeychain(encryptedContainer, identifier) {
        throw new Error('iOS Keychain integration not implemented - requires native code');
    }

    async loadFromiOSKeychain(identifier) {
        throw new Error('iOS Keychain integration not implemented - requires native code');
    }

    async saveToDesktopSecureStorage(encryptedContainer, identifier) {
        const fs = require('fs').promises;
        const os = require('os');
        const path = require('path');
        
        const storageDir = path.join(os.homedir(), '.nonmessenger');
        await fs.mkdir(storageDir, { recursive: true });
        
        const filePath = path.join(storageDir, `${identifier}.enc`);
        await fs.writeFile(filePath, JSON.stringify(encryptedContainer), 'utf8');
        
        return filePath;
    }

    async loadFromDesktopSecureStorage(identifier) {
        const fs = require('fs').promises;
        const os = require('os');
        const path = require('path');
        
        const filePath = path.join(os.homedir(), '.nonmessenger', `${identifier}.enc`);
        const data = await fs.readFile(filePath, 'utf8');
        
        return JSON.parse(data);
    }
}

module.exports = { SecureKeyStorage, PlatformKeyStorage };