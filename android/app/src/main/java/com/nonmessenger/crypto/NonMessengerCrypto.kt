package com.nonmessenger.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.spec.PBEKeySpec
import javax.crypto.SecretKeyFactory
import java.security.SecureRandom

class NonMessengerCrypto {
    companion object {
        private const val RSA_KEY_SIZE = 4096
        private const val AES_KEY_SIZE = 256
        private const val PBKDF2_ITERATIONS = 100000
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        
        init {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private val secureRandom = SecureRandom()

    data class KeyPair(
        val publicKey: String,
        val privateKey: String
    )

    data class EncryptedMessage(
        val encryptedMessage: String,
        val encryptedKey: String,
        val iv: String,
        val authTag: String
    )

    fun generateRSAKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC")
        keyPairGenerator.initialize(RSA_KEY_SIZE, secureRandom)
        
        val keyPair = keyPairGenerator.generateKeyPair()
        
        val publicKeyBytes = keyPair.public.encoded
        val privateKeyBytes = keyPair.private.encoded
        
        return KeyPair(
            publicKey = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP),
            privateKey = Base64.encodeToString(privateKeyBytes, Base64.NO_WRAP)
        )
    }

    fun generate8WordContactCode(): List<String> {
        val words = BIP39WordList.getRandomWords(8)
        return words
    }

    fun generate8WordSecretCode(): List<String> {
        val words = BIP39WordList.getRandomWords(8)
        return words
    }

    fun deriveKeyFromWords(words: List<String>): ByteArray {
        val mnemonic = words.joinToString(" ")
        val salt = "nonmessenger-salt".toByteArray(StandardCharsets.UTF_8)
        
        val spec = PBEKeySpec(mnemonic.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        
        return factory.generateSecret(spec).encoded
    }

    fun generateContactKeyPair(contactWords: List<String>): KeyPair {
        require(contactWords.size == 8) { "Contact code must be exactly 8 words" }
        
        val seed = deriveKeyFromWords(contactWords)
        val deterministicRandom = SecureRandom.getInstance("SHA1PRNG")
        deterministicRandom.setSeed(seed)
        
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC")
        keyPairGenerator.initialize(2048, deterministicRandom)
        
        val keyPair = keyPairGenerator.generateKeyPair()
        
        return KeyPair(
            publicKey = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP),
            privateKey = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
        )
    }

    fun generateFullKeyPair(allWords: List<String>): KeyPair {
        require(allWords.size == 16) { "Full key generation requires 16 words" }
        
        val seed = deriveKeyFromWords(allWords)
        val deterministicRandom = SecureRandom.getInstance("SHA1PRNG")
        deterministicRandom.setSeed(seed)
        
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC")
        keyPairGenerator.initialize(RSA_KEY_SIZE, deterministicRandom)
        
        val keyPair = keyPairGenerator.generateKeyPair()
        
        return KeyPair(
            publicKey = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP),
            privateKey = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
        )
    }

    fun encryptMessage(message: String, publicKeyString: String): EncryptedMessage {
        // Generate AES key
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(AES_KEY_SIZE)
        val aesKey = keyGenerator.generateKey()
        
        // Generate IV
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)
        
        // Encrypt message with AES-GCM
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec)
        
        val encryptedBytes = cipher.doFinal(message.toByteArray(StandardCharsets.UTF_8))
        val encryptedMessage = encryptedBytes.dropLast(GCM_TAG_LENGTH).toByteArray()
        val authTag = encryptedBytes.takeLast(GCM_TAG_LENGTH).toByteArray()
        
        // Encrypt AES key with RSA
        val publicKeyBytes = Base64.decode(publicKeyString, Base64.NO_WRAP)
        val keyFactory = KeyFactory.getInstance("RSA", "BC")
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
        
        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding", "BC")
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encryptedAESKey = rsaCipher.doFinal(aesKey.encoded)
        
        return EncryptedMessage(
            encryptedMessage = Base64.encodeToString(encryptedMessage, Base64.NO_WRAP),
            encryptedKey = Base64.encodeToString(encryptedAESKey, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
            authTag = Base64.encodeToString(authTag, Base64.NO_WRAP)
        )
    }

    fun decryptMessage(encryptedData: EncryptedMessage, privateKeyString: String): String {
        // Decrypt AES key with RSA
        val privateKeyBytes = Base64.decode(privateKeyString, Base64.NO_WRAP)
        val keyFactory = KeyFactory.getInstance("RSA", "BC")
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
        
        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding", "BC")
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey)
        val aesKeyBytes = rsaCipher.doFinal(Base64.decode(encryptedData.encryptedKey, Base64.NO_WRAP))
        val aesKey = SecretKeySpec(aesKeyBytes, "AES")
        
        // Decrypt message with AES-GCM
        val iv = Base64.decode(encryptedData.iv, Base64.NO_WRAP)
        val encryptedMessage = Base64.decode(encryptedData.encryptedMessage, Base64.NO_WRAP)
        val authTag = Base64.decode(encryptedData.authTag, Base64.NO_WRAP)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        cipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec)
        
        val encryptedWithTag = encryptedMessage + authTag
        val decryptedBytes = cipher.doFinal(encryptedWithTag)
        
        return String(decryptedBytes, StandardCharsets.UTF_8)
    }

    fun generateQRCodeData(publicKey: String, deviceId: String, timestamp: Long = System.currentTimeMillis()): String {
        val qrData = mapOf(
            "publicKey" to publicKey,
            "deviceId" to deviceId,
            "timestamp" to timestamp,
            "version" to "1.0"
        )
        return com.google.gson.Gson().toJson(qrData)
    }

    fun parseQRCodeData(qrData: String): Map<String, Any> {
        return try {
            com.google.gson.Gson().fromJson(qrData, Map::class.java) as Map<String, Any>
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid QR code data", e)
        }
    }

    fun validateContactMessage(message: String): Boolean {
        return message.length == 256
    }

    fun generateDeviceId(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}