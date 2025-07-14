package com.nonmessenger.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.util.Log
import androidx.core.content.ContextCompat
import com.nonmessenger.crypto.NonMessengerCrypto
import com.nonmessenger.model.Contact
import com.nonmessenger.network.MessagePoolClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class VoiceCallManager(
    private val context: Context,
    private val crypto: NonMessengerCrypto,
    private val messageClient: MessagePoolClient
) {
    companion object {
        private const val TAG = "VoiceCallManager"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Audio components
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val isRecording = AtomicBoolean(false)
    private val isPlaying = AtomicBoolean(false)
    
    // Call state
    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()
    
    private val _currentCall = MutableStateFlow<VoiceCall?>(null)
    val currentCall: StateFlow<VoiceCall?> = _currentCall.asStateFlow()
    
    // Audio encryption
    private var callEncryptionKey: ByteArray? = null
    private var sequenceNumber = 0
    
    enum class CallState {
        IDLE, CALLING, RINGING, CONNECTED, ENDED, ERROR
    }
    
    data class VoiceCall(
        val callId: String,
        val contact: Contact,
        val isIncoming: Boolean,
        val startTime: Long = System.currentTimeMillis(),
        var endTime: Long? = null,
        var duration: Long = 0
    )
    
    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    suspend fun initiateCall(contact: Contact): Boolean {
        if (!hasAudioPermission()) {
            _callState.value = CallState.ERROR
            return false
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val callId = generateCallId()
                val call = VoiceCall(
                    callId = callId,
                    contact = contact,
                    isIncoming = false
                )
                
                _currentCall.value = call
                _callState.value = CallState.CALLING
                
                // Generate encryption key for this call
                callEncryptionKey = crypto.generateAESKey()
                
                // Send call initiation message
                val success = messageClient.sendVoiceCallInit(
                    callId = callId,
                    recipientContactCode = contact.contactCode.joinToString("-"),
                    encryptedKey = crypto.encryptAESKey(callEncryptionKey!!, contact.publicKey)
                )
                
                if (success) {
                    // Start timeout timer
                    startCallTimeout(callId)
                    true
                } else {
                    _callState.value = CallState.ERROR
                    _currentCall.value = null
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initiate call", e)
                _callState.value = CallState.ERROR
                false
            }
        }
    }
    
    suspend fun acceptCall(callId: String, encryptedKey: String, callerPublicKey: String): Boolean {
        if (!hasAudioPermission()) {
            _callState.value = CallState.ERROR
            return false
        }
        
        return withContext(Dispatchers.IO) {
            try {
                // Decrypt the call encryption key
                callEncryptionKey = crypto.decryptAESKey(encryptedKey, callerPublicKey)
                
                // Send acceptance message
                val success = messageClient.sendVoiceCallAccept(callId)
                
                if (success) {
                    _callState.value = CallState.CONNECTED
                    startAudioStreaming()
                    true
                } else {
                    _callState.value = CallState.ERROR
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to accept call", e)
                _callState.value = CallState.ERROR
                false
            }
        }
    }
    
    suspend fun rejectCall(callId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val success = messageClient.sendVoiceCallReject(callId)
                _callState.value = CallState.ENDED
                _currentCall.value = null
                success
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reject call", e)
                false
            }
        }
    }
    
    suspend fun endCall(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val call = _currentCall.value
                if (call != null) {
                    // Send end call message
                    messageClient.sendVoiceCallEnd(call.callId)
                    
                    // Update call duration
                    call.endTime = System.currentTimeMillis()
                    call.duration = call.endTime!! - call.startTime
                }
                
                stopAudioStreaming()
                _callState.value = CallState.ENDED
                _currentCall.value = null
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to end call", e)
                false
            }
        }
    }
    
    private fun startAudioStreaming() {
        if (!hasAudioPermission()) return
        
        scope.launch {
            try {
                initializeAudioRecord()
                initializeAudioTrack()
                
                // Start recording and streaming
                launch { startRecording() }
                launch { startPlayback() }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start audio streaming", e)
                _callState.value = CallState.ERROR
            }
        }
    }
    
    private fun stopAudioStreaming() {
        isRecording.set(false)
        isPlaying.set(false)
        
        audioRecord?.apply {
            if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                stop()
            }
            release()
        }
        audioRecord = null
        
        audioTrack?.apply {
            if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                stop()
            }
            release()
        }
        audioTrack = null
    }
    
    private fun initializeAudioRecord() {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        ) * BUFFER_SIZE_FACTOR
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
    }
    
    private fun initializeAudioTrack() {
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AUDIO_FORMAT
        ) * BUFFER_SIZE_FACTOR
        
        audioTrack = AudioTrack(
            AudioManager.STREAM_VOICE_CALL,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AUDIO_FORMAT,
            bufferSize,
            AudioTrack.MODE_STREAM
        )
    }
    
    private suspend fun startRecording() = withContext(Dispatchers.IO) {
        val record = audioRecord ?: return@withContext
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val buffer = ByteArray(bufferSize)
        
        record.startRecording()
        isRecording.set(true)
        
        while (isRecording.get() && _callState.value == CallState.CONNECTED) {
            val bytesRead = record.read(buffer, 0, buffer.size)
            
            if (bytesRead > 0) {
                // Encrypt audio data
                val encryptedAudio = encryptAudioData(buffer, bytesRead)
                
                // Send encrypted audio packet
                messageClient.sendVoiceData(
                    callId = _currentCall.value?.callId ?: "",
                    encryptedAudioData = encryptedAudio,
                    sequenceNumber = sequenceNumber++
                )
            }
            
            delay(20) // ~50 packets per second
        }
        
        record.stop()
    }
    
    private suspend fun startPlayback() = withContext(Dispatchers.IO) {
        val track = audioTrack ?: return@withContext
        
        track.play()
        isPlaying.set(true)
        
        // Listen for incoming audio packets
        messageClient.onVoiceDataReceived = { encryptedData, sequenceNum ->
            scope.launch {
                try {
                    val decryptedAudio = decryptAudioData(encryptedData)
                    track.write(decryptedAudio, 0, decryptedAudio.size)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decrypt audio data", e)
                }
            }
        }
    }
    
    private fun encryptAudioData(audioData: ByteArray, length: Int): String {
        val key = callEncryptionKey ?: throw IllegalStateException("No encryption key")
        
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        
        val dataToEncrypt = audioData.copyOf(length)
        val encryptedData = cipher.doFinal(dataToEncrypt)
        
        return android.util.Base64.encodeToString(encryptedData, android.util.Base64.DEFAULT)
    }
    
    private fun decryptAudioData(encryptedData: String): ByteArray {
        val key = callEncryptionKey ?: throw IllegalStateException("No encryption key")
        
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        
        val encryptedBytes = android.util.Base64.decode(encryptedData, android.util.Base64.DEFAULT)
        return cipher.doFinal(encryptedBytes)
    }
    
    private fun startCallTimeout(callId: String) {
        scope.launch {
            delay(30000) // 30 second timeout
            
            if (_currentCall.value?.callId == callId && _callState.value == CallState.CALLING) {
                _callState.value = CallState.ENDED
                _currentCall.value = null
            }
        }
    }
    
    private fun generateCallId(): String {
        return "call_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
    
    fun cleanup() {
        scope.cancel()
        stopAudioStreaming()
        callEncryptionKey = null
        sequenceNumber = 0
    }
}
