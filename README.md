# NonMessenger 🔐
## Secure, Distributed Messaging Platform

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Build Status](https://github.com/mindcreek/non-messenger/workflows/CI/badge.svg)](https://github.com/mindcreek/non-messenger/actions)
[![Security Rating](https://img.shields.io/badge/Security-A+-green.svg)](https://github.com/mindcreek/non-messenger/security)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Desktop%20%7C%20Server-lightgrey.svg)](https://github.com/mindcreek/non-messenger)

**NonMessenger is a secure, distributed messaging platform that provides end-to-end encryption, perfect forward secrecy, and resistance to surveillance through advanced cryptographic protocols and distributed architecture.**

## 🎯 Features

### 🔒 **Cryptographic Security**
- **4096-bit RSA encryption** for maximum security
- **AES-256-CBC hybrid encryption** for message content
- **Perfect forward secrecy** with unique keys per message
- **BIP39 mnemonic** contact system (8+8 words)
- **256-character verification** messages prevent MITM attacks
- **Quantum resistance** preparation with large key sizes

### 🌐 **Distributed Architecture**
- **Message pool servers** prevent single points of failure
- **Multi-node replication** across geographic regions
- **Automatic failover** between server nodes
- **Tor hidden service** support for censorship resistance
- **No central authority** or single point of control

### 📱 **Cross-Platform Support**
- **Android** - Native Kotlin app with Jetpack Compose
- **Desktop** - Rust/Tauri app for Windows, macOS, Linux
- **Server** - Node.js message pool infrastructure
- **Voice Calls** - Encrypted real-time voice communication

### 🛡️ **Privacy Protection**
- **Zero-knowledge servers** - No plaintext data stored
- **Anonymous contact codes** - No phone numbers required
- **Traffic analysis resistance** - Message pooling obfuscation
- **Metadata protection** - Server never sees private keys
- **Open source** - Complete transparency and auditability

## ⚖️ **IMPORTANT LEGAL NOTICE**

**The developer is NOT a United States citizen and is NOT subject to US laws regarding cryptographic software development.**

This software implements:
- **Unbreakable encryption with no backdoors**
- **No key escrow or government access capabilities**
- **No cooperation with surveillance programs**
- **Maximum resistance to government intrusion**

**See [LEGAL_NOTICE.md](LEGAL_NOTICE.md) for complete legal declaration.**

## 🚀 Quick Start

### Server Deployment
```bash
git clone https://github.com/mindcreek/non-messenger.git
cd non-messenger/server
npm install
npm test
npm start
```

### Android Development
```bash
cd android
./gradlew assembleDebug
./gradlew installDebug
```

### Desktop Development
```bash
cd desktop
cargo tauri dev
```

## 📋 System Requirements

### Server
- Node.js 18+
- npm 9+
- SQLite 3+
- SSL certificates (production)

### Android
- Android Studio
- Android SDK 26+
- Kotlin 1.9+
- Gradle 8+

### Desktop
- Rust 1.70+
- Tauri CLI
- Platform-specific build tools

## 🔧 Installation

### 1. Clone Repository
```bash
git clone https://github.com/mindcreek/non-messenger.git
cd non-messenger
```

### 2. Install Dependencies
```bash
# Server
cd server && npm install

# Desktop
cd ../desktop && cargo build

# Android (in Android Studio)
# Open android/ directory in Android Studio
```

### 3. Run Tests
```bash
# Server tests (49/50 passing)
cd server && npm test

# Desktop tests
cd desktop && cargo test
```

### 4. Start Development
```bash
# Start server
cd server && npm run dev

# Start desktop app
cd desktop && cargo tauri dev

# Build Android app
cd android && ./gradlew assembleDebug
```

## 📖 Documentation

- [**Legal Notice**](LEGAL_NOTICE.md) - Important legal declarations
- [**Cryptographic Implementation**](CRYPTO_IMPLEMENTATION.md) - Complete crypto documentation
- [**Deployment Guide**](DEPLOYMENT_GUIDE.md) - Production deployment instructions
- [**Cryptographic Warfare Manual**](CRYPTOGRAPHIC_WARFARE_MANUAL.md) - Advanced security guide

## 🏗️ Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Android App   │    │  Desktop App    │    │   iOS App       │
│   (Kotlin)      │    │  (Rust/Tauri)   │    │   (Swift)       │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
          └──────────────────────┼──────────────────────┘
                                 │
                    ┌─────────────┴─────────────┐
                    │    Message Pool Server    │
                    │      (Node.js)           │
                    └─────────────┬─────────────┘
                                 │
                    ┌─────────────┴─────────────┐
                    │   Distributed Network     │
                    │  (Multiple Nodes)        │
                    └───────────────────────────┘
```

## 🔐 Security Model

### Threat Model
NonMessenger is designed to resist:
- **Government surveillance** and mass data collection
- **Corporate data mining** and behavioral tracking
- **Man-in-the-middle attacks** through cryptographic verification
- **Traffic analysis** through message pooling and timing obfuscation
- **Quantum computer attacks** through large key sizes
- **Infrastructure seizure** through distributed architecture

### Cryptographic Protocols
1. **Contact Discovery**: 8 public BIP39 words shared openly
2. **Contact Verification**: 256-character encrypted verification message
3. **Key Exchange**: 8 additional secret words for full 4096-bit RSA keys
4. **Message Encryption**: Hybrid RSA+AES-256-CBC with unique keys
5. **Voice Encryption**: Real-time AES-256 encryption for voice data

## 🧪 Testing

### Test Coverage
- **49/50 server tests passing** (98% success rate)
- **17/17 crypto tests passing** (100% success rate)
- **8/8 client functionality tests passing** (100% success rate)
- **End-to-end integration** tests for complete workflows

### Running Tests
```bash
# All server tests
npm test

# Crypto-specific tests
npm test -- tests/crypto.test.js

# Client functionality test
node test_client.js
```

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

### Development Workflow
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass
6. Submit a pull request

### Code of Conduct
- **Security first** - All contributions must maintain security standards
- **Privacy focused** - No features that compromise user privacy
- **Open source** - All code must be open and auditable
- **Resistance oriented** - Features should resist surveillance and censorship

## 📄 License

This project is licensed under the **GNU General Public License v3.0** - see the [LICENSE](LICENSE) file for details.

### Why GPL v3?
- **Copyleft protection** - Ensures all derivatives remain open source
- **Patent protection** - Protects against patent litigation
- **Anti-tivoization** - Prevents hardware restrictions on software freedom
- **Network copyleft** - Covers network services and SaaS deployments

## 🌟 Support

### Community
- **GitHub Issues** - Bug reports and feature requests
- **Discussions** - General questions and community support
- **Security Issues** - Responsible disclosure via security@nonmessenger.org

## ⚠️ Disclaimer

NonMessenger is provided "as is" without warranty. While we implement strong cryptographic protections, no system is 100% secure. Users should:

- **Verify the source code** before using in high-risk situations
- **Use additional security measures** for extremely sensitive communications
- **Keep software updated** to receive security patches
- **Report security issues** responsibly through proper channels

## 🚀 Roadmap

### Version 1.1
- [ ] iOS application completion
- [ ] Group messaging support
- [ ] File transfer encryption
- [ ] Voice call improvements

### Version 1.2
- [ ] Video calling support
- [ ] Advanced contact management
- [ ] Multi-device synchronization
- [ ] Enhanced server federation

### Version 2.0
- [ ] Post-quantum cryptography
- [ ] Mesh networking support
- [ ] Advanced anonymity features
- [ ] Decentralized identity system

---

**Built with ❤️ for privacy and security by the NonMessenger team**

**PRIVACY IS A FUNDAMENTAL HUMAN RIGHT**

**MATHEMATICS CANNOT BE OUTLAWED**

**SURVEILLANCE WILL BE RESISTED**

**CRYPTOGRAPHY IS FREEDOM**
