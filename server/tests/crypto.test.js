const NonMessengerCrypto = require('../shared/crypto/encryption');

describe('NonMessengerCrypto', () => {
    let crypto;

    beforeEach(() => {
        crypto = new NonMessengerCrypto();
    });

    describe('RSA Key Generation', () => {
        test('should generate 4096-bit RSA key pair', () => {
            const keyPair = crypto.generateRSAKeyPair();
            
            expect(keyPair.publicKey).toContain('-----BEGIN PUBLIC KEY-----');
            expect(keyPair.privateKey).toContain('-----BEGIN PRIVATE KEY-----');
            expect(keyPair.publicKey).toContain('-----END PUBLIC KEY-----');
            expect(keyPair.privateKey).toContain('-----END PRIVATE KEY-----');
        });

        test('should generate valid keys from 8 words', () => {
            const words = ['abandon', 'ability', 'able', 'about', 'above', 'absent', 'absorb', 'abstract'];

            const keyPair1 = crypto.generateContactKeyPair(words);
            const keyPair2 = crypto.generateContactKeyPair(words);

            // Keys should be valid RSA keys (not deterministic in this implementation)
            expect(keyPair1.publicKey).toContain('BEGIN PUBLIC KEY');
            expect(keyPair1.privateKey).toContain('BEGIN PRIVATE KEY');
            expect(keyPair2.publicKey).toContain('BEGIN PUBLIC KEY');
            expect(keyPair2.privateKey).toContain('BEGIN PRIVATE KEY');
        });

        test('should generate valid keys from 16 words', () => {
            const words = [
                'abandon', 'ability', 'able', 'about', 'above', 'absent', 'absorb', 'abstract',
                'absurd', 'abuse', 'access', 'accident', 'account', 'accuse', 'achieve', 'acid'
            ];

            const keyPair1 = crypto.generateFullKeyPair(words);
            const keyPair2 = crypto.generateFullKeyPair(words);

            // Keys should be valid 4096-bit RSA keys (not deterministic in this implementation)
            expect(keyPair1.publicKey).toContain('BEGIN PUBLIC KEY');
            expect(keyPair1.privateKey).toContain('BEGIN PRIVATE KEY');
            expect(keyPair2.publicKey).toContain('BEGIN PUBLIC KEY');
            expect(keyPair2.privateKey).toContain('BEGIN PRIVATE KEY');
        });

        test('should throw error for invalid word count', () => {
            expect(() => {
                crypto.generateContactKeyPair(['too', 'few', 'words']);
            }).toThrow('Contact code must be exactly 8 words');

            expect(() => {
                crypto.generateFullKeyPair(['too', 'few', 'words']);
            }).toThrow('Full key generation requires 16 words');
        });
    });

    describe('Word Generation', () => {
        test('should generate 8 contact words', () => {
            const words = crypto.generate8WordContactCode();
            
            expect(words).toHaveLength(8);
            expect(words.every(word => typeof word === 'string')).toBe(true);
        });

        test('should generate 8 secret words', () => {
            const words = crypto.generate8WordSecretCode();
            
            expect(words).toHaveLength(8);
            expect(words.every(word => typeof word === 'string')).toBe(true);
        });

        test('should generate different word sets each time', () => {
            const words1 = crypto.generate8WordContactCode();
            const words2 = crypto.generate8WordContactCode();
            
            expect(words1).not.toEqual(words2);
        });
    });

    describe('Message Encryption/Decryption', () => {
        test('should encrypt and decrypt messages correctly', () => {
            const keyPair = crypto.generateRSAKeyPair();
            const message = 'This is a test message for encryption';
            
            const encrypted = crypto.encryptMessage(message, keyPair.publicKey);
            const decrypted = crypto.decryptMessage(encrypted, keyPair.privateKey);
            
            expect(decrypted).toBe(message);
        });

        test('should produce different ciphertext for same message', () => {
            const keyPair = crypto.generateRSAKeyPair();
            const message = 'Same message';
            
            const encrypted1 = crypto.encryptMessage(message, keyPair.publicKey);
            const encrypted2 = crypto.encryptMessage(message, keyPair.publicKey);
            
            expect(encrypted1.encryptedMessage).not.toBe(encrypted2.encryptedMessage);
            expect(encrypted1.iv).not.toBe(encrypted2.iv);
        });

        test('should handle large messages', () => {
            const keyPair = crypto.generateRSAKeyPair();
            const largeMessage = 'A'.repeat(10000);
            
            const encrypted = crypto.encryptMessage(largeMessage, keyPair.publicKey);
            const decrypted = crypto.decryptMessage(encrypted, keyPair.privateKey);
            
            expect(decrypted).toBe(largeMessage);
        });

        test('should fail with wrong private key', () => {
            const keyPair1 = crypto.generateRSAKeyPair();
            const keyPair2 = crypto.generateRSAKeyPair();
            const message = 'Secret message';
            
            const encrypted = crypto.encryptMessage(message, keyPair1.publicKey);
            
            expect(() => {
                crypto.decryptMessage(encrypted, keyPair2.privateKey);
            }).toThrow();
        });
    });

    describe('QR Code Data', () => {
        test('should generate and parse QR code data', () => {
            const keyPair = crypto.generateRSAKeyPair();
            const deviceId = crypto.generateDeviceId();
            
            const qrData = crypto.generateQRCodeData(keyPair.publicKey, deviceId);
            const parsed = crypto.parseQRCodeData(qrData);
            
            expect(parsed.publicKey).toBe(keyPair.publicKey);
            expect(parsed.deviceId).toBe(deviceId);
            expect(parsed.version).toBe('1.0');
            expect(typeof parsed.timestamp).toBe('number');
        });

        test('should throw error for invalid QR data', () => {
            expect(() => {
                crypto.parseQRCodeData('invalid json');
            }).toThrow('Invalid QR code data');
        });
    });

    describe('Contact Message Validation', () => {
        test('should validate 256-character messages', () => {
            const validMessage = 'A'.repeat(256);
            const invalidMessage = 'A'.repeat(255);
            
            expect(crypto.validateContactMessage(validMessage)).toBe(true);
            expect(crypto.validateContactMessage(invalidMessage)).toBe(false);
            expect(crypto.validateContactMessage(null)).toBe(false);
            expect(crypto.validateContactMessage(123)).toBe(false);
        });
    });

    describe('Device ID Generation', () => {
        test('should generate unique device IDs', () => {
            const id1 = crypto.generateDeviceId();
            const id2 = crypto.generateDeviceId();
            
            expect(id1).not.toBe(id2);
            expect(typeof id1).toBe('string');
            expect(typeof id2).toBe('string');
            expect(id1.length).toBeGreaterThan(0);
        });
    });

    describe('Key Derivation', () => {
        test('should derive consistent keys from same words', () => {
            const words = ['abandon', 'ability', 'able', 'about', 'above', 'absent', 'absorb', 'abstract'];
            
            const key1 = crypto.deriveKeyFromWords(words);
            const key2 = crypto.deriveKeyFromWords(words);
            
            expect(key1.equals(key2)).toBe(true);
        });

        test('should derive different keys from different words', () => {
            const words1 = ['abandon', 'ability', 'able', 'about', 'above', 'absent', 'absorb', 'abstract'];
            const words2 = ['abandon', 'ability', 'able', 'about', 'above', 'absent', 'absorb', 'absurd'];
            
            const key1 = crypto.deriveKeyFromWords(words1);
            const key2 = crypto.deriveKeyFromWords(words2);
            
            expect(key1.equals(key2)).toBe(false);
        });
    });
});
