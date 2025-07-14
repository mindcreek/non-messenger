# NonMessenger Content Policy

## Responsible Privacy Protection

NonMessenger is designed to provide strong privacy protection while maintaining responsible content limitations to prevent misuse and ensure legal compliance across jurisdictions.

## 🔒 **Core Design Principles**

### **Text-Only Communication**
NonMessenger is intentionally designed for **secure text communication and voice calls only**. This design choice serves multiple purposes:

1. **Legal Compliance**: Prevents distribution of illegal content
2. **Performance Optimization**: Ensures fast, reliable messaging
3. **Security Focus**: Reduces attack surface and complexity
4. **Resource Efficiency**: Minimizes bandwidth and storage requirements

### **Message Size Limitations**

**Maximum Message Size: 2048 bytes (2KB)**

This limitation is enforced at multiple levels:
- **Client-side validation** (Android, Desktop, iOS)
- **Server-side enforcement** (Message pool servers)
- **Cryptographic layer** (Encryption functions)

## 📋 **Technical Implementation**

### **Enforcement Mechanisms**

1. **Pre-Encryption Validation**
   ```
   Message size check: ≤ 2048 bytes UTF-8
   Content type: Text only
   Binary content: Rejected
   ```

2. **Server-Side Limits**
   ```
   Total payload: ≤ 4KB (including encryption overhead)
   Encrypted content: ≤ 3KB
   Metadata: ≤ 1KB
   ```

3. **Network Protocol**
   ```
   HTTP 413 error for oversized messages
   Clear error messages explaining limits
   Automatic rejection of large payloads
   ```

### **Error Messages**

When size limits are exceeded, users receive clear feedback:

```
"Message too large: X bytes. Maximum allowed: 2048 bytes (2KB). 
NonMessenger is designed for secure text communication only."
```

## 🛡️ **Content Restrictions**

### **Prohibited Content Types**

NonMessenger's 2KB limit inherently prevents:

1. **Image Files**: Even small images exceed 2KB
2. **Video Content**: All video formats are too large
3. **Audio Files**: Audio files cannot fit in 2KB
4. **Documents**: Most document files exceed the limit
5. **Executable Files**: All executables are too large
6. **Archive Files**: Compressed files typically exceed 2KB

### **Allowed Content**

- **Text messages** up to 2048 bytes
- **Unicode characters** (emojis, international text)
- **URLs and links** (within size limit)
- **Short formatted text** (markdown-style)
- **Encrypted voice calls** (real-time, not stored)

## 🎯 **Intended Use Cases**

### **Legitimate Uses**
- **Personal communication** between friends and family
- **Business messaging** for sensitive communications
- **Journalism** and source protection
- **Political activism** in oppressive regimes
- **Medical communications** requiring privacy
- **Legal communications** requiring confidentiality

### **Technical Specifications**

```
Maximum text length: ~2000 characters (depending on encoding)
Supported encodings: UTF-8
Voice calls: Real-time encrypted streams (not stored)
Message retention: 24 hours maximum on servers
Perfect forward secrecy: Each message uses unique keys
```

## ⚖️ **Legal Compliance**

### **Harm Prevention**

The 2KB limit serves as a technical safeguard against:

1. **Child Exploitation Material**: Cannot transmit images or videos
2. **Copyright Infringement**: Cannot share large copyrighted files
3. **Malware Distribution**: Cannot transmit executable files
4. **Illegal Content Sharing**: File sharing is technically impossible

### **Jurisdictional Compliance**

This design ensures compliance with laws in most jurisdictions while maintaining:
- **Strong encryption** for legitimate privacy needs
- **Content limitations** to prevent criminal misuse
- **Technical barriers** against illegal file sharing
- **Audit trail** through size limit enforcement

## 🔧 **Implementation Details**

### **Client-Side Validation**

All client applications implement pre-send validation:

```kotlin
// Android example
if (messageBytes.size > 2048) {
    throw IllegalArgumentException(
        "Message too large: ${messageBytes.size} bytes. " +
        "Maximum allowed: 2048 bytes (2KB)."
    )
}
```

### **Server-Side Enforcement**

Message pool servers reject oversized content:

```javascript
// Server example
if (messageSize > 4096) {
    return res.status(413).json({ 
        error: "Message payload too large",
        maxSize: 4096,
        currentSize: messageSize
    });
}
```

### **Cryptographic Layer**

Encryption functions validate size before processing:

```rust
// Desktop example
if message_bytes.len() > 2048 {
    return Err(anyhow!(
        "Message too large: {} bytes. Maximum: 2048 bytes.",
        message_bytes.len()
    ));
}
```

## 📊 **Monitoring and Compliance**

### **Automated Enforcement**

- **Real-time size checking** at all layers
- **Automatic rejection** of oversized content
- **Clear error messaging** to users
- **No manual content review** required

### **Privacy Protection**

- **No content inspection** by servers
- **End-to-end encryption** maintained
- **Zero-knowledge architecture** preserved
- **Size limits only** - no content analysis

## 🌍 **Global Accessibility**

### **Language Support**

The 2KB limit accommodates:
- **~2000 ASCII characters**
- **~1000 Unicode characters** (average)
- **~500 complex Unicode characters** (worst case)
- **All major languages** and writing systems

### **Cultural Considerations**

- **Emoji support** within size limits
- **International character sets**
- **Right-to-left languages**
- **Complex script support**

## 🔄 **Future Considerations**

### **Potential Adjustments**

While the current 2KB limit serves our goals well, future versions might consider:

1. **Slightly larger limits** for specific use cases
2. **Compressed text** for efficiency
3. **Chunked messages** for longer communications
4. **Voice message transcription** (text-only)

### **Maintaining Principles**

Any future changes will maintain:
- **No file sharing capabilities**
- **No image/video transmission**
- **Strong encryption standards**
- **Legal compliance focus**

## 📞 **Voice Communication**

### **Real-Time Only**

Voice calls in NonMessenger are:
- **Real-time encrypted streams**
- **Not stored on servers**
- **Not recorded or cached**
- **Ephemeral by design**

This ensures voice communication cannot be used for:
- **File transfer** (no storage mechanism)
- **Content distribution** (real-time only)
- **Illegal content sharing** (no persistence)

## 🎯 **Conclusion**

NonMessenger's content policy balances:
- **Maximum privacy protection** for legitimate users
- **Responsible limitations** to prevent criminal misuse
- **Technical enforcement** without content inspection
- **Legal compliance** across multiple jurisdictions

This approach ensures NonMessenger can provide strong privacy protection while remaining a responsible platform that cannot be easily misused for illegal content distribution.

**The 2KB limit is not a restriction on freedom - it's a design choice that enables freedom by ensuring the platform remains legally viable and technically focused on its core mission: secure text communication.**
