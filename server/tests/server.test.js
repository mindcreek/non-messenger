const request = require('supertest');
const WebSocket = require('ws');
const NonMessengerServer = require('../server');

describe('NonMessenger Server', () => {
    let server;
    let app;

    beforeAll(() => {
        // Use a different port for testing
        process.env.PORT = 3001;
        server = new NonMessengerServer();
        app = server.app;
    });

    beforeEach(() => {
        // Reset rate limiter between tests
        if (server && server.rateLimiter) {
            server.rateLimiter = new (require('rate-limiter-flexible')).RateLimiterMemory({
                keyGenerator: (req) => req.ip,
                points: 100,
                duration: 60,
            });
        }
    });

    afterAll((done) => {
        if (server.server) {
            server.server.close(() => {
                // Clean up any remaining connections
                setTimeout(done, 100);
            });
        } else {
            done();
        }
    });

    describe('Health Check', () => {
        test('should return server health status', async () => {
            const response = await request(app)
                .get('/health')
                .expect(200);

            expect(response.body).toHaveProperty('status', 'healthy');
            expect(response.body).toHaveProperty('timestamp');
            expect(response.body).toHaveProperty('version', '1.0.0');
            expect(response.body).toHaveProperty('messagePoolSize');
            expect(response.body).toHaveProperty('activeSessions');
            expect(response.body).toHaveProperty('connectedNodes');
        });
    });

    describe('Message API', () => {
        test('should accept valid message', async () => {
            const messageData = {
                recipientContactCode: 'test-contact-code',
                encryptedMessage: 'encrypted-test-message',
                messageId: 'test-message-id-123',
                ttl: 86400000
            };

            const response = await request(app)
                .post('/api/message')
                .send(messageData)
                .expect(200);

            expect(response.body).toHaveProperty('success', true);
            expect(response.body).toHaveProperty('messageId', messageData.messageId);
            expect(response.body).toHaveProperty('delivered');
            expect(response.body).toHaveProperty('pooled');
        });

        test('should reject message with missing fields', async () => {
            const incompleteMessage = {
                recipientContactCode: 'test-contact-code'
                // Missing encryptedMessage and messageId
            };

            const response = await request(app)
                .post('/api/message')
                .send(incompleteMessage)
                .expect(400);

            expect(response.body).toHaveProperty('error', 'Missing required fields');
        });

        test('should retrieve messages for contact code', async () => {
            const contactCode = 'test-retrieval-contact';
            const messageData = {
                recipientContactCode: contactCode,
                encryptedMessage: 'encrypted-test-message-for-retrieval',
                messageId: 'test-retrieval-message-id',
                ttl: 86400000
            };

            // First, send a message
            await request(app)
                .post('/api/message')
                .send(messageData)
                .expect(200);

            // Then retrieve it
            const response = await request(app)
                .get(`/api/messages/${contactCode}`)
                .expect(200);

            expect(response.body).toHaveProperty('messages');
            expect(Array.isArray(response.body.messages)).toBe(true);
        });

        test('should delete specific message', async () => {
            const messageData = {
                recipientContactCode: 'test-delete-contact',
                encryptedMessage: 'encrypted-test-message-for-deletion',
                messageId: 'test-delete-message-id',
                ttl: 86400000
            };

            // Send message
            await request(app)
                .post('/api/message')
                .send(messageData)
                .expect(200);

            // Delete message
            const response = await request(app)
                .delete(`/api/message/${messageData.messageId}`)
                .expect(200);

            expect(response.body).toHaveProperty('success', true);
        });
    });

    describe('Node Registration', () => {
        test('should register new node', async () => {
            const nodeData = {
                nodeUrl: 'https://test-node.example.com',
                publicKey: 'test-public-key'
            };

            const response = await request(app)
                .post('/api/register-node')
                .send(nodeData)
                .expect(200);

            expect(response.body).toHaveProperty('success', true);
            expect(response.body).toHaveProperty('registeredNodes');
        });

        test('should reject node registration with missing data', async () => {
            const incompleteNodeData = {
                nodeUrl: 'https://test-node.example.com'
                // Missing publicKey
            };

            const response = await request(app)
                .post('/api/register-node')
                .send(incompleteNodeData)
                .expect(400);

            expect(response.body).toHaveProperty('error', 'Missing node URL or public key');
        });

        test('should list registered nodes', async () => {
            const response = await request(app)
                .get('/api/nodes')
                .expect(200);

            expect(response.body).toHaveProperty('nodes');
            expect(Array.isArray(response.body.nodes)).toBe(true);
        });
    });

    describe('Rate Limiting', () => {
        test('should apply rate limiting', async () => {
            const promises = [];
            
            // Send many requests quickly to trigger rate limiting
            for (let i = 0; i < 150; i++) {
                promises.push(
                    request(app)
                        .get('/health')
                );
            }

            const responses = await Promise.allSettled(promises);
            const rateLimitedResponses = responses.filter(
                result => result.value && result.value.status === 429
            );

            expect(rateLimitedResponses.length).toBeGreaterThan(0);
        });
    });

    describe('Security Headers', () => {
        test('should include security headers', async () => {
            const response = await request(app)
                .get('/health')
                .expect(200);

            // Check for helmet security headers
            expect(response.headers).toHaveProperty('x-content-type-options');
            expect(response.headers).toHaveProperty('x-frame-options');
        });
    });

    describe('Message Pool Management', () => {
        test('should handle message TTL', async () => {
            const messageData = {
                recipientContactCode: 'test-ttl-contact',
                encryptedMessage: 'encrypted-test-message-ttl',
                messageId: 'test-ttl-message-id',
                ttl: 1000 // 1 second TTL
            };

            await request(app)
                .post('/api/message')
                .send(messageData)
                .expect(200);

            // Wait for TTL to expire
            await new Promise(resolve => setTimeout(resolve, 1500));

            // Manually trigger cleanup
            server.cleanupExpiredMessages();

            // Try to retrieve - should be empty
            const response = await request(app)
                .get(`/api/messages/${messageData.recipientContactCode}`)
                .expect(200);

            expect(response.body.messages).toHaveLength(0);
        });
    });
});
