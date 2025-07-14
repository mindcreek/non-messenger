# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

NonMessenger is a secure, distributed messaging platform with 4096-bit encryption and decentralized infrastructure. The project implements a unique 8+8 word contact system with 256-character verification for secure pairing.

## Build and Development Commands

### Server (Node.js)
```bash
cd server
npm install              # Install dependencies
npm start                # Run production server
npm run dev              # Run development server with hot reload (nodemon)
npm test                 # Run tests (Jest)
```

### Android
```bash
cd android
./gradlew build          # Build the project
./gradlew clean          # Clean build artifacts
./gradlew assembleDebug  # Build debug APK
./gradlew assembleRelease # Build release APK
```

### Python Development
```bash
# Create and activate virtual environment
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Run linting and type checking
flake8 .
mypy .
black .

# Run tests
pytest
```

## Architecture Overview

### Core Components

1. **Message Pool Server** (`server/server.js`)
   - WebSocket + REST API hybrid architecture
   - Handles real-time message delivery with pooling fallback
   - Implements message TTL and automatic cleanup
   - Supports multi-node replication for distribution
   - Rate limiting and security middleware integrated

2. **Cryptographic System** (`shared/crypto/encryption.js`)
   - 4096-bit RSA for full communication, 2048-bit for initial contact
   - AES-256-GCM for message content encryption
   - BIP39 mnemonic generation for contact words
   - PBKDF2 key derivation (100,000 iterations)

3. **Contact System**
   - Two-phase verification: 8 public words + 8 private words
   - 256-character encrypted verification message
   - Prevents man-in-the-middle attacks through mutual verification

### Message Flow

1. **Contact Establishment**:
   - User A generates 16-word mnemonic (8 public + 8 private)
   - Shares first 8 words publicly
   - User B sends contact request with 256-char encrypted message
   - User A approves and shares remaining 8 words
   - Full 4096-bit encrypted channel established

2. **Message Routing**:
   - Messages encrypted client-side with recipient's public key
   - Sent to distributed pool servers via WebSocket/REST
   - Recipients pull messages from multiple servers
   - Messages auto-expire based on TTL

## Key Files and Their Purposes

- `server/server.js`: Main server implementation with WebSocket and REST endpoints
- `shared/crypto/encryption.js`: Core cryptographic functions
- `shared/protocols/messaging.js`: Message format and protocol definitions
- `shared/protocols/awareness.js`: Situational awareness implementation
- `shared/utils/keyStorage.js`: Secure key management utilities
- `android/app/src/main/java/com/nonmessenger/crypto/NonMessengerCrypto.kt`: Android crypto wrapper

## Development Guidelines

### Security Considerations
- All cryptographic operations happen client-side
- Server never sees unencrypted content or private keys
- Message pooling prevents traffic analysis
- Multiple server nodes prevent single point of failure

### API Endpoints
- POST `/api/messages`: Submit encrypted message to pool
- GET `/api/messages/:recipientId`: Retrieve messages for recipient
- WS `/`: WebSocket connection for real-time delivery
- GET `/api/status`: Server health check

### Environment Variables
Server configuration via `.env`:
- `PORT`: Server port (default: 3000)
- `NODE_ENV`: Environment (development/production)
- `MESSAGE_TTL`: Message expiration time
- `CLEANUP_INTERVAL`: Cleanup job frequency

## Testing Approach

While test infrastructure is configured (Jest for server), no tests currently exist. When implementing tests:
- Use Jest for server-side JavaScript testing
- Focus on cryptographic integrity and protocol compliance
- Test message routing and pool management
- Verify security constraints are maintained

## Platform Status

- **Server**: Fully implemented and functional
- **Shared Libraries**: Complete implementation
- **Android**: Basic structure with crypto module started
- **iOS/Desktop**: Planned but not implemented