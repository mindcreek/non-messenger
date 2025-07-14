class SituationalAwareness {
    constructor() {
        this.AWARENESS_TYPES = {
            USER_STATUS: 'user_status',
            TYPING_INDICATOR: 'typing_indicator',
            LAST_SEEN: 'last_seen',
            DELIVERY_STATUS: 'delivery_status',
            READ_RECEIPT: 'read_receipt',
            VOICE_CALL_STATUS: 'voice_call_status',
            NETWORK_STATUS: 'network_status'
        };

        this.USER_STATUSES = {
            ONLINE: 'online',
            OFFLINE: 'offline',
            AWAY: 'away',
            BUSY: 'busy',
            INVISIBLE: 'invisible'
        };

        this.DELIVERY_STATUSES = {
            SENT: 'sent',
            DELIVERED: 'delivered',
            READ: 'read',
            FAILED: 'failed'
        };

        this.CALL_STATUSES = {
            RINGING: 'ringing',
            ACTIVE: 'active',
            ENDED: 'ended',
            MISSED: 'missed'
        };
    }

    createUserStatus(userId, status, customMessage = null, timestamp = Date.now()) {
        if (!Object.values(this.USER_STATUSES).includes(status)) {
            throw new Error('Invalid user status');
        }

        return {
            type: this.AWARENESS_TYPES.USER_STATUS,
            userId,
            status,
            customMessage,
            timestamp,
            version: '1.0'
        };
    }

    createStatusUpdate(userId, status, customMessage = null, timestamp = Date.now()) {
        if (!Object.values(this.USER_STATUSES).includes(status)) {
            throw new Error('Invalid user status');
        }

        return {
            type: this.AWARENESS_TYPES.USER_STATUS,
            userId,
            status,
            customMessage,
            timestamp,
            version: '1.0'
        };
    }

    createTypingIndicator(userId, chatId, isTyping, timestamp = Date.now()) {
        return {
            type: this.AWARENESS_TYPES.TYPING_INDICATOR,
            userId,
            chatId,
            isTyping,
            timestamp,
            version: '1.0'
        };
    }

    createLastSeenUpdate(userId, timestamp = Date.now()) {
        return {
            type: this.AWARENESS_TYPES.LAST_SEEN,
            userId,
            lastSeen: timestamp,
            version: '1.0'
        };
    }

    createDeliveryStatus(messageId, status, userId, timestamp = Date.now()) {
        if (!Object.values(this.DELIVERY_STATUSES).includes(status)) {
            throw new Error('Invalid delivery status');
        }

        return {
            type: this.AWARENESS_TYPES.DELIVERY_STATUS,
            messageId,
            status,
            userId,
            timestamp,
            version: '1.0'
        };
    }

    createReadReceipt(messageId, userId, readTimestamp = Date.now()) {
        return {
            type: this.AWARENESS_TYPES.READ_RECEIPT,
            messageId,
            userId,
            readTimestamp,
            version: '1.0'
        };
    }

    createVoiceCallStatus(callId, status, participants, timestamp = Date.now()) {
        if (!Object.values(this.CALL_STATUSES).includes(status)) {
            throw new Error('Invalid call status');
        }

        return {
            type: this.AWARENESS_TYPES.VOICE_CALL_STATUS,
            callId,
            status,
            participants,
            timestamp,
            version: '1.0'
        };
    }

    createNetworkStatus(userId, isOnline, connectionQuality = null, timestamp = Date.now()) {
        return {
            type: this.AWARENESS_TYPES.NETWORK_STATUS,
            userId,
            isOnline,
            connectionQuality,
            timestamp,
            version: '1.0'
        };
    }

    validateAwarenessMessage(message) {
        if (!message || typeof message !== 'object') {
            return false;
        }

        const requiredFields = ['type', 'timestamp', 'version'];
        for (const field of requiredFields) {
            if (!message.hasOwnProperty(field)) {
                return false;
            }
        }

        if (!Object.values(this.AWARENESS_TYPES).includes(message.type)) {
            return false;
        }

        return true;
    }

    shouldShowTypingIndicator(typingMessage, maxAge = 5000) {
        const now = Date.now();
        return typingMessage.isTyping && (now - typingMessage.timestamp) < maxAge;
    }

    formatLastSeen(lastSeenTimestamp) {
        const now = Date.now();
        const diff = now - lastSeenTimestamp;
        
        if (diff < 60000) {
            return 'just now';
        } else if (diff < 3600000) {
            const minutes = Math.floor(diff / 60000);
            return `${minutes} minute${minutes > 1 ? 's' : ''} ago`;
        } else if (diff < 86400000) {
            const hours = Math.floor(diff / 3600000);
            return `${hours} hour${hours > 1 ? 's' : ''} ago`;
        } else {
            const days = Math.floor(diff / 86400000);
            return `${days} day${days > 1 ? 's' : ''} ago`;
        }
    }

    getConnectionQualityDescription(quality) {
        if (quality === null || quality === undefined) {
            return 'Unknown';
        }
        
        if (quality >= 0.8) {
            return 'Excellent';
        } else if (quality >= 0.6) {
            return 'Good';
        } else if (quality >= 0.4) {
            return 'Fair';
        } else if (quality >= 0.2) {
            return 'Poor';
        } else {
            return 'Very Poor';
        }
    }

    aggregateUserPresence(awarenessMessages) {
        const userPresence = new Map();

        for (const message of awarenessMessages) {
            if (!this.validateAwarenessMessage(message)) {
                continue;
            }

            const userId = message.userId;
            if (!userPresence.has(userId)) {
                userPresence.set(userId, {
                    status: this.USER_STATUSES.OFFLINE,
                    lastSeen: 0,
                    isTyping: false,
                    isOnline: false,
                    connectionQuality: null,
                    customMessage: null
                });
            }

            const presence = userPresence.get(userId);

            switch (message.type) {
                case this.AWARENESS_TYPES.USER_STATUS:
                    presence.status = message.status;
                    presence.customMessage = message.customMessage;
                    presence.lastSeen = message.timestamp;
                    break;

                case this.AWARENESS_TYPES.LAST_SEEN:
                    presence.lastSeen = Math.max(presence.lastSeen, message.lastSeen);
                    break;

                case this.AWARENESS_TYPES.TYPING_INDICATOR:
                    presence.isTyping = this.shouldShowTypingIndicator(message);
                    break;

                case this.AWARENESS_TYPES.NETWORK_STATUS:
                    presence.isOnline = message.isOnline;
                    presence.connectionQuality = message.connectionQuality;
                    break;
            }
        }

        return userPresence;
    }

    createPresenceSnapshot(userPresence) {
        const snapshot = {};
        
        for (const [userId, presence] of userPresence) {
            snapshot[userId] = {
                status: presence.status,
                lastSeen: presence.lastSeen,
                lastSeenFormatted: this.formatLastSeen(presence.lastSeen),
                isTyping: presence.isTyping,
                isOnline: presence.isOnline,
                connectionQuality: presence.connectionQuality,
                connectionDescription: this.getConnectionQualityDescription(presence.connectionQuality),
                customMessage: presence.customMessage
            };
        }

        return snapshot;
    }
}

module.exports = SituationalAwareness;