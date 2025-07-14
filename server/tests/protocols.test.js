const MessagingProtocol = require('../shared/protocols/messaging');
const SituationalAwareness = require('../shared/protocols/awareness');

describe('Messaging Protocol', () => {
    let protocol;

    beforeEach(() => {
        protocol = new MessagingProtocol();
    });

    describe('Message Types', () => {
        test('should have all required message types', () => {
            expect(protocol.MESSAGE_TYPES).toHaveProperty('CONTACT_REQUEST');
            expect(protocol.MESSAGE_TYPES).toHaveProperty('CONTACT_RESPONSE');
            expect(protocol.MESSAGE_TYPES).toHaveProperty('MESSAGE');
            expect(protocol.MESSAGE_TYPES).toHaveProperty('VOICE_CALL_INIT');
            expect(protocol.MESSAGE_TYPES).toHaveProperty('VOICE_CALL_ACCEPT');
            expect(protocol.MESSAGE_TYPES).toHaveProperty('VOICE_CALL_REJECT');
            expect(protocol.MESSAGE_TYPES).toHaveProperty('VOICE_CALL_END');
            expect(protocol.MESSAGE_TYPES).toHaveProperty('VOICE_DATA');
        });
    });

    describe('Contact Request Creation', () => {
        test('should create valid contact request', () => {
            const request = protocol.createContactRequest(
                'sender-123',
                ['word1', 'word2', 'word3', 'word4', 'word5', 'word6', 'word7', 'word8'],
                'A'.repeat(256),
                'public-key-data'
            );

            expect(request.type).toBe(protocol.MESSAGE_TYPES.CONTACT_REQUEST);
            expect(request.senderId).toBe('sender-123');
            expect(request.publicWords).toHaveLength(8);
            expect(request.verificationMessage).toHaveLength(256);
            expect(request.senderPublicKey).toBe('public-key-data');
            expect(request).toHaveProperty('id');
            expect(request).toHaveProperty('timestamp');
            expect(request.version).toBe('1.0');
        });
    });

    describe('Contact Response Creation', () => {
        test('should create acceptance response', () => {
            const response = protocol.createContactResponse(
                'original-request-id',
                true,
                ['secret1', 'secret2', 'secret3', 'secret4', 'secret5', 'secret6', 'secret7', 'secret8'],
                'recipient-public-key'
            );

            expect(response.type).toBe(protocol.MESSAGE_TYPES.CONTACT_RESPONSE);
            expect(response.originalRequestId).toBe('original-request-id');
            expect(response.accepted).toBe(true);
            expect(response.secretWords).toHaveLength(8);
            expect(response.recipientPublicKey).toBe('recipient-public-key');
        });

        test('should create rejection response', () => {
            const response = protocol.createContactResponse('original-request-id', false);

            expect(response.type).toBe(protocol.MESSAGE_TYPES.CONTACT_RESPONSE);
            expect(response.originalRequestId).toBe('original-request-id');
            expect(response.accepted).toBe(false);
            expect(response).not.toHaveProperty('secretWords');
            expect(response).not.toHaveProperty('recipientPublicKey');
        });
    });

    describe('Message Creation', () => {
        test('should create standard message', () => {
            const message = protocol.createMessage(
                'Hello, this is a test message',
                'recipient-123',
                'sender-456',
                'text'
            );

            expect(message.type).toBe(protocol.MESSAGE_TYPES.MESSAGE);
            expect(message.content).toBe('Hello, this is a test message');
            expect(message.recipientId).toBe('recipient-123');
            expect(message.senderId).toBe('sender-456');
            expect(message.messageType).toBe('text');
        });
    });

    describe('Voice Call Messages', () => {
        test('should create voice call init message', () => {
            const callInit = protocol.createVoiceCallInit('caller-123', 'recipient-456', 'call-789');

            expect(callInit.type).toBe(protocol.MESSAGE_TYPES.VOICE_CALL_INIT);
            expect(callInit.callerId).toBe('caller-123');
            expect(callInit.recipientId).toBe('recipient-456');
            expect(callInit.callId).toBe('call-789');
        });

        test('should create voice call accept message', () => {
            const callAccept = protocol.createVoiceCallResponse('call-789', true, 'recipient-456');

            expect(callAccept.type).toBe(protocol.MESSAGE_TYPES.VOICE_CALL_ACCEPT);
            expect(callAccept.callId).toBe('call-789');
            expect(callAccept.recipientId).toBe('recipient-456');
        });

        test('should create voice call reject message', () => {
            const callReject = protocol.createVoiceCallResponse('call-789', false, 'recipient-456');

            expect(callReject.type).toBe(protocol.MESSAGE_TYPES.VOICE_CALL_REJECT);
            expect(callReject.callId).toBe('call-789');
            expect(callReject.recipientId).toBe('recipient-456');
        });

        test('should create voice data message', () => {
            const voiceData = protocol.createVoiceData('call-789', 'encrypted-audio-data', 42);

            expect(voiceData.type).toBe(protocol.MESSAGE_TYPES.VOICE_DATA);
            expect(voiceData.callId).toBe('call-789');
            expect(voiceData.encryptedAudioData).toBe('encrypted-audio-data');
            expect(voiceData.sequenceNumber).toBe(42);
        });
    });

    describe('Message Validation', () => {
        test('should validate correct messages', () => {
            const validMessage = {
                type: protocol.MESSAGE_TYPES.MESSAGE,
                id: 'test-id',
                timestamp: Date.now(),
                version: '1.0',
                content: 'test content'
            };

            expect(protocol.validateMessage(validMessage)).toBe(true);
        });

        test('should reject invalid messages', () => {
            expect(protocol.validateMessage(null)).toBe(false);
            expect(protocol.validateMessage({})).toBe(false);
            expect(protocol.validateMessage({ type: 'invalid' })).toBe(false);
            expect(protocol.validateMessage({ type: protocol.MESSAGE_TYPES.MESSAGE })).toBe(false);
        });

        test('should validate contact messages', () => {
            expect(protocol.validateContactMessage('A'.repeat(256))).toBe(true);
            expect(protocol.validateContactMessage('A'.repeat(255))).toBe(false);
            expect(protocol.validateContactMessage(null)).toBe(false);
        });
    });

    describe('ID Generation', () => {
        test('should generate unique message IDs', () => {
            const id1 = protocol.generateMessageId();
            const id2 = protocol.generateMessageId();

            expect(id1).not.toBe(id2);
            expect(typeof id1).toBe('string');
            expect(id1.length).toBeGreaterThan(0);
        });

        test('should generate unique call IDs', () => {
            const id1 = protocol.generateCallId();
            const id2 = protocol.generateCallId();

            expect(id1).not.toBe(id2);
            expect(typeof id1).toBe('string');
            expect(id1.length).toBeGreaterThan(0);
        });
    });
});

describe('Situational Awareness', () => {
    let awareness;

    beforeEach(() => {
        awareness = new SituationalAwareness();
    });

    describe('Awareness Types', () => {
        test('should have all required awareness types', () => {
            expect(awareness.AWARENESS_TYPES).toHaveProperty('USER_STATUS');
            expect(awareness.AWARENESS_TYPES).toHaveProperty('TYPING_INDICATOR');
            expect(awareness.AWARENESS_TYPES).toHaveProperty('LAST_SEEN');
            expect(awareness.AWARENESS_TYPES).toHaveProperty('DELIVERY_STATUS');
            expect(awareness.AWARENESS_TYPES).toHaveProperty('READ_RECEIPT');
            expect(awareness.AWARENESS_TYPES).toHaveProperty('VOICE_CALL_STATUS');
            expect(awareness.AWARENESS_TYPES).toHaveProperty('NETWORK_STATUS');
        });
    });

    describe('Status Creation', () => {
        test('should create user status message', () => {
            const status = awareness.createUserStatus('user-123', 'online', 'Working on project');

            expect(status.type).toBe(awareness.AWARENESS_TYPES.USER_STATUS);
            expect(status.userId).toBe('user-123');
            expect(status.status).toBe('online');
            expect(status.customMessage).toBe('Working on project');
        });

        test('should create typing indicator', () => {
            const typing = awareness.createTypingIndicator('user-123', 'chat-456', true);

            expect(typing.type).toBe(awareness.AWARENESS_TYPES.TYPING_INDICATOR);
            expect(typing.userId).toBe('user-123');
            expect(typing.chatId).toBe('chat-456');
            expect(typing.isTyping).toBe(true);
        });

        test('should create delivery status', () => {
            const delivery = awareness.createDeliveryStatus('message-123', 'delivered');

            expect(delivery.type).toBe(awareness.AWARENESS_TYPES.DELIVERY_STATUS);
            expect(delivery.messageId).toBe('message-123');
            expect(delivery.status).toBe('delivered');
        });
    });

    describe('Message Validation', () => {
        test('should validate awareness messages', () => {
            const validMessage = {
                type: awareness.AWARENESS_TYPES.USER_STATUS,
                timestamp: Date.now(),
                version: '1.0',
                userId: 'test-user'
            };

            expect(awareness.validateAwarenessMessage(validMessage)).toBe(true);
        });

        test('should reject invalid awareness messages', () => {
            expect(awareness.validateAwarenessMessage(null)).toBe(false);
            expect(awareness.validateAwarenessMessage({})).toBe(false);
            expect(awareness.validateAwarenessMessage({ type: 'invalid' })).toBe(false);
        });
    });

    describe('Typing Indicator Logic', () => {
        test('should show typing indicator for recent messages', () => {
            const recentTyping = {
                isTyping: true,
                timestamp: Date.now() - 1000 // 1 second ago
            };

            expect(awareness.shouldShowTypingIndicator(recentTyping)).toBe(true);
        });

        test('should not show typing indicator for old messages', () => {
            const oldTyping = {
                isTyping: true,
                timestamp: Date.now() - 10000 // 10 seconds ago
            };

            expect(awareness.shouldShowTypingIndicator(oldTyping)).toBe(false);
        });
    });
});
