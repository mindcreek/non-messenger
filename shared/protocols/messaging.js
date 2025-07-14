const crypto = require('crypto');

class MessagingProtocol {
    constructor() {
        this.MESSAGE_TYPES = {
            CONTACT_REQUEST: 'contact_request',
            CONTACT_RESPONSE: 'contact_response',
            MESSAGE: 'message',
            VOICE_CALL_INIT: 'voice_call_init',
            VOICE_CALL_ACCEPT: 'voice_call_accept',
            VOICE_CALL_REJECT: 'voice_call_reject',
            VOICE_CALL_END: 'voice_call_end',
            VOICE_DATA: 'voice_data',
            STATUS_UPDATE: 'status_update',
            DELIVERY_RECEIPT: 'delivery_receipt'
        };

        this.STATUS_TYPES = {
            ONLINE: 'online',
            OFFLINE: 'offline',
            AWAY: 'away',
            BUSY: 'busy'
        };
    }

    createContactRequest(senderId, publicWords, verificationMessage, senderPublicKey) {
        if (!this.validateContactMessage(verificationMessage)) {
            throw new Error('Verification message must be exactly 256 characters');
        }

        return {
            type: this.MESSAGE_TYPES.CONTACT_REQUEST,
            id: this.generateMessageId(),
            timestamp: Date.now(),
            senderId,
            publicWords,
            verificationMessage,
            senderPublicKey,
            version: '1.0'
        };
    }

    createContactResponse(originalRequestId, accepted, secretWords = null, recipientPublicKey = null) {
        const response = {
            type: this.MESSAGE_TYPES.CONTACT_RESPONSE,
            id: this.generateMessageId(),
            timestamp: Date.now(),
            originalRequestId,
            accepted,
            version: '1.0'
        };

        if (accepted && secretWords && recipientPublicKey) {
            response.secretWords = secretWords;
            response.recipientPublicKey = recipientPublicKey;
        }

        return response;
    }

    createMessage(content, recipientId, senderId, messageType = 'text') {
        return {
            type: this.MESSAGE_TYPES.MESSAGE,
            id: this.generateMessageId(),
            timestamp: Date.now(),
            senderId,
            recipientId,
            messageType,
            content,
            version: '1.0'
        };
    }

    createVoiceCallInit(callerId, recipientId, callId) {
        return {
            type: this.MESSAGE_TYPES.VOICE_CALL_INIT,
            id: this.generateMessageId(),
            timestamp: Date.now(),
            callerId,
            recipientId,
            callId,
            version: '1.0'
        };
    }

    createVoiceCallResponse(callId, accepted, recipientId) {
        return {
            type: accepted ? this.MESSAGE_TYPES.VOICE_CALL_ACCEPT : this.MESSAGE_TYPES.VOICE_CALL_REJECT,
            id: this.generateMessageId(),
            timestamp: Date.now(),
            callId,
            recipientId,
            version: '1.0'
        };
    }

    createVoiceData(callId, encryptedAudioData, sequenceNumber) {
        return {
            type: this.MESSAGE_TYPES.VOICE_DATA,
            id: this.generateMessageId(),
            timestamp: Date.now(),
            callId,
            encryptedAudioData,
            sequenceNumber,
            version: '1.0'
        };
    }

    createStatusUpdate(userId, status, customMessage = null) {
        if (!Object.values(this.STATUS_TYPES).includes(status)) {
            throw new Error('Invalid status type');
        }

        return {
            type: this.MESSAGE_TYPES.STATUS_UPDATE,
            id: this.generateMessageId(),
            timestamp: Date.now(),
            userId,
            status,
            customMessage,
            version: '1.0'
        };
    }

    createDeliveryReceipt(originalMessageId, recipientId, delivered = true) {
        return {
            type: this.MESSAGE_TYPES.DELIVERY_RECEIPT,
            id: this.generateMessageId(),
            timestamp: Date.now(),
            originalMessageId,
            recipientId,
            delivered,
            version: '1.0'
        };
    }

    validateMessage(message) {
        if (!message || typeof message !== 'object') {
            return false;
        }

        const requiredFields = ['type', 'id', 'timestamp', 'version'];
        for (const field of requiredFields) {
            if (!message.hasOwnProperty(field)) {
                return false;
            }
        }

        if (!Object.values(this.MESSAGE_TYPES).includes(message.type)) {
            return false;
        }

        return true;
    }

    validateContactMessage(message) {
        return typeof message === 'string' && message.length === 256;
    }

    generateMessageId() {
        return crypto.randomBytes(16).toString('hex');
    }

    generateCallId() {
        return crypto.randomBytes(8).toString('hex');
    }

    encryptProtocolMessage(message, publicKey, cryptoInstance) {
        const messageString = JSON.stringify(message);
        return cryptoInstance.encryptMessage(messageString, publicKey);
    }

    decryptProtocolMessage(encryptedData, privateKey, cryptoInstance) {
        const messageString = cryptoInstance.decryptMessage(encryptedData, privateKey);
        return JSON.parse(messageString);
    }
}

module.exports = MessagingProtocol;