const express = require('express');
const WebSocket = require('ws');
const cors = require('cors');
const helmet = require('helmet');
const { RateLimiterMemory } = require('rate-limiter-flexible');
const cron = require('node-cron');
const crypto = require('crypto');
const fetch = require('node-fetch');
require('dotenv').config();

class NonMessengerServer {
    constructor() {
        this.app = express();
        this.server = null;
        this.wss = null;
        this.messagePool = new Map();
        this.userSessions = new Map();
        this.serverNodes = new Set();
        this.port = process.env.PORT || 3000;
        
        this.rateLimiter = new RateLimiterMemory({
            keyGenerator: (req) => req.ip,
            points: 100,
            duration: 60,
        });

        this.setupMiddleware();
        this.setupRoutes();
        this.setupWebSocket();
        this.setupCleanupTasks();
    }

    setupMiddleware() {
        this.app.use(helmet());
        this.app.use(cors({
            origin: process.env.ALLOWED_ORIGINS?.split(',') || ['http://localhost:3000'],
            credentials: true
        }));
        this.app.use(express.json({ limit: '10mb' }));
        this.app.use(this.rateLimitMiddleware.bind(this));
    }

    async rateLimitMiddleware(req, res, next) {
        try {
            await this.rateLimiter.consume(req.ip);
            next();
        } catch (rejRes) {
            res.status(429).json({ error: 'Too many requests' });
        }
    }

    setupRoutes() {
        this.app.get('/health', (req, res) => {
            res.json({ 
                status: 'healthy', 
                timestamp: Date.now(),
                version: '1.0.0',
                messagePoolSize: this.messagePool.size,
                activeSessions: this.userSessions.size,
                connectedNodes: this.serverNodes.size
            });
        });

        this.app.post('/api/message', this.handleMessage.bind(this));
        this.app.get('/api/messages/:contactCode', this.getMessages.bind(this));
        this.app.delete('/api/message/:messageId', this.deleteMessage.bind(this));
        this.app.post('/api/register-node', this.registerNode.bind(this));
        this.app.get('/api/nodes', this.getNodes.bind(this));
    }

    setupWebSocket() {
        this.server = this.app.listen(this.port, () => {
            console.log(`NonMessenger server running on port ${this.port}`);
        });

        this.wss = new WebSocket.Server({ server: this.server });
        
        this.wss.on('connection', (ws, req) => {
            const sessionId = this.generateSessionId();
            ws.sessionId = sessionId;
            
            console.log(`New WebSocket connection: ${sessionId}`);
            
            ws.on('message', (data) => {
                this.handleWebSocketMessage(ws, data);
            });
            
            ws.on('close', () => {
                this.userSessions.delete(sessionId);
                console.log(`WebSocket disconnected: ${sessionId}`);
            });
            
            ws.on('error', (error) => {
                console.error(`WebSocket error for ${sessionId}:`, error);
            });
        });
    }

    handleWebSocketMessage(ws, data) {
        try {
            const message = JSON.parse(data);
            
            switch (message.type) {
                case 'register_user':
                    this.registerUser(ws, message.contactCode);
                    break;
                case 'status_update':
                    this.broadcastStatusUpdate(message);
                    break;
                case 'real_time_message':
                    this.forwardRealTimeMessage(message);
                    break;
                default:
                    ws.send(JSON.stringify({ error: 'Unknown message type' }));
            }
        } catch (error) {
            console.error('Error handling WebSocket message:', error);
            ws.send(JSON.stringify({ error: 'Invalid message format' }));
        }
    }

    registerUser(ws, contactCode) {
        if (!contactCode || typeof contactCode !== 'string') {
            ws.send(JSON.stringify({ error: 'Invalid contact code' }));
            return;
        }

        this.userSessions.set(ws.sessionId, {
            ws,
            contactCode,
            lastSeen: Date.now(),
            status: 'online'
        });

        ws.send(JSON.stringify({ 
            type: 'registration_success', 
            sessionId: ws.sessionId 
        }));

        console.log(`User registered: ${contactCode} (${ws.sessionId})`);
    }

    async handleMessage(req, res) {
        try {
            const { recipientContactCode, encryptedMessage, messageId, ttl = 86400000 } = req.body;

            if (!recipientContactCode || !encryptedMessage || !messageId) {
                return res.status(400).json({ error: 'Missing required fields' });
            }

            const message = {
                id: messageId,
                recipientContactCode,
                encryptedMessage,
                timestamp: Date.now(),
                ttl,
                attempts: 0,
                maxAttempts: 3
            };

            this.messagePool.set(messageId, message);

            const delivered = await this.attemptDirectDelivery(message);
            
            if (delivered) {
                this.messagePool.delete(messageId);
            }

            await this.replicateToNodes(message);

            res.json({ 
                success: true, 
                messageId, 
                delivered,
                pooled: !delivered 
            });

        } catch (error) {
            console.error('Error handling message:', error);
            res.status(500).json({ error: 'Internal server error' });
        }
    }

    async attemptDirectDelivery(message) {
        for (const [sessionId, session] of this.userSessions) {
            if (session.contactCode === message.recipientContactCode) {
                try {
                    session.ws.send(JSON.stringify({
                        type: 'new_message',
                        message: message.encryptedMessage,
                        messageId: message.id,
                        timestamp: message.timestamp
                    }));
                    return true;
                } catch (error) {
                    console.error(`Failed to deliver to ${sessionId}:`, error);
                    this.userSessions.delete(sessionId);
                }
            }
        }
        return false;
    }

    async getMessages(req, res) {
        try {
            const { contactCode } = req.params;
            const messages = [];

            for (const [messageId, message] of this.messagePool) {
                if (message.recipientContactCode === contactCode) {
                    messages.push({
                        id: messageId,
                        encryptedMessage: message.encryptedMessage,
                        timestamp: message.timestamp
                    });
                    
                    this.messagePool.delete(messageId);
                }
            }

            res.json({ messages });

        } catch (error) {
            console.error('Error getting messages:', error);
            res.status(500).json({ error: 'Internal server error' });
        }
    }

    async deleteMessage(req, res) {
        try {
            const { messageId } = req.params;
            const deleted = this.messagePool.delete(messageId);
            
            res.json({ success: deleted });

        } catch (error) {
            console.error('Error deleting message:', error);
            res.status(500).json({ error: 'Internal server error' });
        }
    }

    async registerNode(req, res) {
        try {
            const { nodeUrl, publicKey } = req.body;
            
            if (!nodeUrl || !publicKey) {
                return res.status(400).json({ error: 'Missing node URL or public key' });
            }

            this.serverNodes.add({ nodeUrl, publicKey, lastSeen: Date.now() });
            
            res.json({ success: true, registeredNodes: this.serverNodes.size });

        } catch (error) {
            console.error('Error registering node:', error);
            res.status(500).json({ error: 'Internal server error' });
        }
    }

    getNodes(req, res) {
        const nodes = Array.from(this.serverNodes).map(node => ({
            nodeUrl: node.nodeUrl,
            lastSeen: node.lastSeen
        }));
        
        res.json({ nodes });
    }

    async replicateToNodes(message) {
        const replicationPromises = Array.from(this.serverNodes).map(async (node) => {
            try {
                const response = await fetch(`${node.nodeUrl}/api/replicate`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(message),
                    timeout: 5000
                });
                return response.ok;
            } catch (error) {
                console.error(`Failed to replicate to ${node.nodeUrl}:`, error);
                return false;
            }
        });

        await Promise.allSettled(replicationPromises);
    }

    forwardRealTimeMessage(message) {
        for (const [sessionId, session] of this.userSessions) {
            if (session.contactCode === message.recipientContactCode) {
                try {
                    session.ws.send(JSON.stringify(message));
                } catch (error) {
                    console.error(`Failed to forward real-time message to ${sessionId}:`, error);
                    this.userSessions.delete(sessionId);
                }
            }
        }
    }

    broadcastStatusUpdate(statusMessage) {
        const broadcast = JSON.stringify(statusMessage);
        
        this.wss.clients.forEach((client) => {
            if (client.readyState === WebSocket.OPEN) {
                try {
                    client.send(broadcast);
                } catch (error) {
                    console.error('Error broadcasting status update:', error);
                }
            }
        });
    }

    setupCleanupTasks() {
        cron.schedule('*/5 * * * *', () => {
            this.cleanupExpiredMessages();
        });

        cron.schedule('*/1 * * * *', () => {
            this.cleanupInactiveSessions();
        });
    }

    cleanupExpiredMessages() {
        const now = Date.now();
        let cleaned = 0;

        for (const [messageId, message] of this.messagePool) {
            if (now - message.timestamp > message.ttl) {
                this.messagePool.delete(messageId);
                cleaned++;
            }
        }

        if (cleaned > 0) {
            console.log(`Cleaned up ${cleaned} expired messages`);
        }
    }

    cleanupInactiveSessions() {
        const now = Date.now();
        const timeout = 5 * 60 * 1000;
        let cleaned = 0;

        for (const [sessionId, session] of this.userSessions) {
            if (now - session.lastSeen > timeout) {
                try {
                    session.ws.close();
                } catch (error) {
                    console.error(`Error closing inactive session ${sessionId}:`, error);
                }
                this.userSessions.delete(sessionId);
                cleaned++;
            }
        }

        if (cleaned > 0) {
            console.log(`Cleaned up ${cleaned} inactive sessions`);
        }
    }

    generateSessionId() {
        return crypto.randomBytes(16).toString('hex');
    }
}

const server = new NonMessengerServer();

process.on('SIGTERM', () => {
    console.log('Received SIGTERM, shutting down gracefully');
    server.server.close(() => {
        process.exit(0);
    });
});

module.exports = NonMessengerServer;