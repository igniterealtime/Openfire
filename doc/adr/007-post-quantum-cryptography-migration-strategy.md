# ADR-007: Post-Quantum Cryptography Assessment and Migration Strategy

## Status
Proposed

## Context

With the advent of large-scale quantum computers, traditional public-key cryptography (RSA, ECDH, DSA) will become vulnerable to Shor's algorithm, which can factor large numbers exponentially faster than classical computers. Additionally, symmetric encryption algorithms (AES, Blowfish) face reduced security from Grover's algorithm, which provides a quadratic speedup for brute-force attacks.

NIST published the first three post-quantum cryptography (PQC) standards in August 2024 (FIPS 203, 204, 205), establishing standardised algorithms resistant to quantum attacks. This ADR assesses Openfire's current cryptographic implementations and defines a migration strategy for the quantum computing era.

### Key Questions

1. Is Openfire's current encryption vulnerable to quantum attacks?
2. When should Openfire migrate to post-quantum cryptography?
3. What migration strategy provides the safest transition path?
4. Which components require PQC upgrades and which are already secure?

## Options Considered

### Option 1: Do Nothing

Accept quantum risk for TLS connections; continue using RSA-2048 until quantum computers become a practical threat.

**Consequences**:
- ✅ No development effort required
- ✅ No compatibility concerns with existing clients/servers
- ✅ No performance overhead
- ❌ "Harvest now, decrypt later" exposure for all historical traffic
- ❌ Reactive rather than proactive security posture
- ❌ May require emergency migration if quantum computers arrive sooner than expected
- ❌ Reputational risk if Openfire is seen as lagging on security

### Option 2: Direct PQC-Only Migration

Skip the hybrid phase entirely; migrate directly to PQC-only TLS when XMPP ecosystem support matures.

**Consequences**:
- ✅ Simpler implementation (no hybrid complexity)
- ✅ Smaller certificate sizes compared to hybrid
- ✅ Better long-term performance (no dual cryptography overhead)
- ❌ Breaks compatibility with non-PQC clients and servers
- ❌ No fallback if PQC algorithms have undiscovered flaws
- ❌ XMPP ecosystem may not be ready for years
- ❌ Higher risk - single point of cryptographic failure

### Option 3: Hybrid Migration (Recommended)

Deploy classical + PQC hybrid TLS first, then transition to PQC-only when the ecosystem is ready.

**Consequences**:
- ✅ Backward compatible with existing clients and servers
- ✅ Defence-in-depth: secure if either algorithm remains unbroken
- ✅ Industry-standard approach endorsed by NIST and IETF
- ✅ Gradual migration reduces deployment risk
- ✅ Time to address any PQC algorithm issues before full commitment
- ❌ Increased implementation complexity
- ❌ Larger certificates (contains both RSA and PQC keys)
- ❌ Performance overhead from dual cryptography (~10-50ms additional latency)
- ❌ Long migration timeline (10+ years for complete transition)

### Option 4: Wait for Java Native PQC

Defer migration until JDK has production-ready native PQC support (expected ~2027 in an LTS release).

**Consequences**:
- ✅ No Bouncy Castle dependency changes required
- ✅ Native JCA integration - cleaner implementation
- ✅ Better long-term maintainability
- ❌ 2-3 year delay increases "harvest now, decrypt later" exposure
- ❌ May miss industry migration window
- ❌ Less control over migration timeline
- ❌ Native implementations may be less mature initially than Bouncy Castle

## Executive Summary

**Property Encryption (Blowfish/AES)**: ✅ **Already quantum-resistant** - No further action required
- PBKDF2-HMAC-SHA512 with 256-bit keys provides 128-bit post-quantum security
- 256-bit salts provide quantum-resistant collision protection
- Meets NIST post-quantum cryptography standards

**TLS/SSL (Network Connections)**: ⚠️ **Vulnerable to future quantum attacks** - Migration strategy needed within 5-10 years
- RSA-2048 will be breakable by large-scale quantum computers (2030-2035)
- "Harvest now, decrypt later" attacks are active today
- Hybrid TLS (classical + PQC) required by 2028

**Timeline**: RSA-2048 remains secure through at least 2030, but adversaries are recording encrypted traffic today for future decryption.

**Recommended Action**:
1. 📋 Plan hybrid TLS implementation (classical + PQC) for 2026-2028
2. 🎯 Deploy hybrid TLS as default by 2028
3. 🔄 Begin PQC-only migration 2032-2035

## Quantum Threat Fundamentals

### Shor's Algorithm (Asymmetric Cryptography)

**Affected algorithms**: RSA, ECDH, DSA, ElGamal

**Impact**: Exponential speedup - can factor RSA-2048 in hours/days vs billions of years classically

**Openfire components at risk**:
- TLS certificates (RSA-2048)
- Server-to-Server (S2S) federation
- Client-to-Server (C2S) connections
- Admin console HTTPS
- BOSH HTTPS

### Grover's Algorithm (Symmetric Cryptography)

**Affected algorithms**: AES, Blowfish, SHA-256, SHA-512

**Impact**: Quadratic speedup - effectively halves key length
- AES-256 → AES-128 effective strength
- 256-bit keys → 128-bit post-quantum security
- 160-bit keys → 80-bit post-quantum security

**Openfire components affected**:
- Property encryption (Blowfish)
- Configuration encryption (AES)
- Key derivation functions (PBKDF2)

**NIST Assessment**: AES-256 remains secure post-quantum. Breaking AES-256 with Grover's algorithm still requires 2^128 operations, which is computationally prohibitive even for quantum systems.

## Current Openfire Encryption Analysis

### 1. Property Encryption - ✅ QUANTUM-RESISTANT

**Current Implementation**:
```java
// xmppserver/src/main/java/org/jivesoftware/util/Blowfish.java
static byte[] deriveKeyPBKDF2(String password, byte[] salt) throws Exception {
    // PBKDF2-HMAC-SHA512 with 100,000 iterations
    final int iterations = 100_000;
    final int keyLength = 256; // 256 bits for strong key derivation

    javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
        password.toCharArray(),
        salt,
        iterations,
        keyLength
    );

    javax.crypto.SecretKeyFactory factory =
        javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
    return factory.generateSecret(spec).getEncoded();
}
```

**Cryptographic Properties**:
- **Key derivation**: PBKDF2-HMAC-SHA512
- **Derived key size**: 256 bits
- **Salt**: 32 bytes (256 bits)
- **Iterations**: 100,000

**Quantum Threat Analysis**:
- **Post-quantum strength**: ~128 bits (256-bit key halved by Grover's algorithm)
- **NIST recommendation**: 128-bit post-quantum minimum
- **Assessment**: ✅ **Strong quantum resistance**

**Security Properties**:
- 256-bit keys provide 128-bit post-quantum security (sufficient)
- 256-bit salts provide quantum-resistant collision protection
  - Birthday paradox: 2^128 collision probability (quantum-resistant)
  - Grover's algorithm: 2^128 brute-force operations (infeasible)
- 100,000 iterations slow brute-force (quantum or classical)
- SHA-512 cryptographically secure (no known collision attacks)

**Verdict**: ✅ **Quantum-resistant** - No further changes needed.

### 2. AES Encryption - ✅ QUANTUM-RESISTANT

**Current Implementation**:
```java
// xmppserver/src/main/java/org/jivesoftware/util/AesEncryptor.java
private static final String ALGORITHM = "AES/GCM/NoPadding";
```

**Cryptographic Properties**:
- **Algorithm**: AES (symmetric cipher)
- **Mode**: GCM (Galois/Counter Mode) - provides authenticated encryption (see ADR-006)
- **Padding**: None required (GCM is a stream cipher mode)
- **Key size**: Typically 256 bits (configurable)

**Quantum Threat Analysis**:
- **Attack vector**: Grover's algorithm
- **Impact**: AES-256 → AES-128 effective strength
- **Post-quantum strength**: 128 bits (sufficient)
- **NIST Assessment**: "Breaking AES-256 with Grover's algorithm requires 2^128 operations, which remains computationally prohibitive even for quantum systems"

**Verdict**: ✅ **Secure against quantum attacks** (with 256-bit keys)

### 3. TLS/SSL (XMPP Connections) - ⚠️ VULNERABLE

**Current Implementation**:
```java
// xmppserver/src/main/java/org/jivesoftware/openfire/keystore/IdentityStore.java
String algorithm = JiveGlobals.getProperty("cert.algorithm", "RSA");

// Default configuration
keySize = JiveGlobals.getIntProperty("cert.rsa.keysize", 2048);  // RSA-2048
signAlgorithm = JiveGlobals.getProperty("cert.rsa.algorithm",
                                        "SHA256WITHRSAENCRYPTION");
```

**Cryptographic Properties**:
- **Algorithm**: RSA (asymmetric cipher)
- **Key size**: 2048 bits (default)
- **Signature**: SHA256withRSA
- **Alternative**: DSA-1024 (even weaker)

**Quantum Threat Analysis**:
- **Attack vector**: Shor's algorithm (exponential speedup)
- **Impact**: Can factor RSA-2048 in hours/days with large-scale quantum computer
- **Current requirement**: ~20 million stable qubits
  - 2025 study (Google Quantum AI): RSA-2048 breakable with <1 million noisy qubits in ~1 week
- **Verdict**: ⚠️ **Vulnerable when large-scale quantum computers exist**

**Affected Openfire Components**:
- **Client-to-Server (C2S) TLS**: Port 5222
- **Server-to-Server (S2S) TLS**: Port 5269
- **Admin Console HTTPS**: Port 9091
- **BOSH HTTPS**: Port 7443
- **WebSocket TLS**: Port 7443
- **Certificate generation**: RSA key pairs
- **Certificate validation**: RSA signature verification

## Quantum Computing Timeline (2025 Perspective)

### Current State (2025)

**Quantum processor capabilities**:
- **Available qubits**: ~1,000+ physical qubits (IBM, Google, IonQ)
- **Error rates**: High noise/error rates (not fault-tolerant)
- **RSA-2048 threat**: Cannot break RSA-2048 with current hardware

**Security assessment**:
- ✅ **RSA-2048 completely secure today**
- ✅ **AES-256 completely secure today**
- ✅ **Blowfish with PBKDF2 completely secure today**
- ✅ **No immediate threat to any Openfire encryption**

### Near-term (2025-2030)

**Threat level**: Low (no cryptographically relevant quantum computers expected)

**Key concerns**:
- ⚠️ **"Harvest now, decrypt later" attacks active**
  - Adversaries recording encrypted XMPP traffic today
  - Plan to decrypt in 10-15 years when quantum computers available
  - Affects: C2S messages, S2S federation traffic, admin sessions

**Recommended actions**:
- Begin planning PQC migration
- Prototype hybrid TLS implementations
- Monitor XMPP ecosystem PQC adoption
- Upgrade Bouncy Castle to 1.79+ (PQC support)

### Medium-term (2030-2035)

**Threat level**: Moderate

**Quantum computer estimates**:
- **Global Risk Institute**: 17-34% probability of breaking RSA-2048 in 24 hours by 2034
- **Required qubits**: ~1-20 million (depending on error correction)
- **Implementation status**: Early cryptographically relevant quantum computers emerging

**Security assessment**:
- ⚠️ **RSA-2048 becomes questionable** (2032-2034)
- ✅ **AES-256 remains secure**
- ✅ **Blowfish/PBKDF2 remains secure**
- 🎯 **Action required**: Deploy hybrid TLS in production

**Industry context**:
> "2025 is described as probably our last chance to start migration to post quantum cryptography before we are all undone by cryptographically relevant quantum computers."
> — SecurityWeek Cyber Insights 2025

### Long-term (2035+)

**Threat level**: High

**Quantum computer estimates**:
- **Global Risk Institute**: 79% probability of breaking RSA-2048 by 2044
- **Implementation status**: Cryptographically relevant quantum computers widely available

**Security assessment**:
- ❌ **RSA-2048 considered broken**
- ❌ **ECDH/DSA also broken**
- ✅ **AES-256 remains secure**
- ✅ **Blowfish/PBKDF2 remains secure**
- 🚨 **Action required**: RSA/ECDH deprecated, PQC mandatory

## NIST Post-Quantum Cryptography Standards (August 2024)

### Published Standards

#### FIPS 203 - ML-KEM (Module-Lattice-Based Key Encapsulation Mechanism)

**Original algorithm**: CRYSTALS-Kyber

**Purpose**: Key establishment for secure communication (replaces RSA/ECDH key exchange)

**Algorithm type**: Lattice-based cryptography

**Security levels**:
- ML-KEM-512: Category 1 (equivalent to AES-128)
- ML-KEM-768: Category 3 (equivalent to AES-192) ← **Recommended**
- ML-KEM-1024: Category 5 (equivalent to AES-256)

**Status**: ✅ Standardised August 2024

**Use case in Openfire**: TLS key exchange for C2S, S2S, HTTPS

#### FIPS 204 - ML-DSA (Module-Lattice-Based Digital Signature Algorithm)

**Original algorithm**: CRYSTALS-Dilithium

**Purpose**: Digital signatures for authentication (replaces RSA/ECDSA/DSA signatures)

**Algorithm type**: Lattice-based cryptography

**Security levels**:
- ML-DSA-44: Category 2
- ML-DSA-65: Category 3 ← **Recommended**
- ML-DSA-87: Category 5

**Status**: ✅ Standardised August 2024

**Use case in Openfire**: TLS certificate signatures, certificate validation

#### FIPS 205 - SLH-DSA (Stateless Hash-based Digital Signature Algorithm)

**Original algorithm**: SPHINCS+

**Purpose**: Alternative digital signature algorithm (hash-based, not lattice-based)

**Algorithm type**: Hash-based cryptography

**Advantages**:
- Conservative security assumption (only relies on hash function security)
- No structured problem (unlike lattice-based)

**Disadvantages**:
- Larger signature sizes vs ML-DSA
- Slower performance

**Status**: ✅ Standardised August 2024

**Use case in Openfire**: Backup signature algorithm if ML-DSA compromised

### Additional Algorithms (Round 4)

**NIST continues evaluation**:
- **Falcon**: Compact lattice-based signatures (smaller than Dilithium)
- **BIKE**: Code-based KEM
- **Classic McEliece**: Conservative code-based KEM
- **HQC**: Code-based KEM

**Status**: Not yet standardised, but considered for specific use cases

## Java and Bouncy Castle PQC Support

### Current Openfire Dependency

**Bouncy Castle version**: 1.78.1

```xml
<!-- pom.xml:133 -->
<bouncycastle.version>1.78.1</bouncycastle.version>

<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
    <version>${bouncycastle.version}</version>
</dependency>
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcpkix-jdk18on</artifactId>
    <version>${bouncycastle.version}</version>
</dependency>
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcpg-jdk18on</artifactId>
    <version>${bouncycastle.version}</version>
</dependency>
```

**PQC support in 1.78.1**: ⚠️ Round 3 candidates only (Kyber, Dilithium pre-standardisation)

### Bouncy Castle 1.79+ (December 2024)

**Post-quantum algorithms supported**:

✅ **ML-KEM** (CRYSTALS-Kyber) - FIPS 203 standardised version
✅ **ML-DSA** (CRYSTALS-Dilithium) - FIPS 204 standardised version
✅ **SLH-DSA** (SPHINCS+) - FIPS 205 standardised version
✅ **Falcon** - Compact lattice-based signatures
✅ **BIKE, Classic McEliece, HQC** - Code-based KEMs

**API compatibility**: Fully compatible with Java Cryptography Architecture (JCA)

**Example implementation**:
```java
// Add Bouncy Castle provider
Security.addProvider(new BouncyCastleProvider());

// Generate ML-KEM (Kyber) key pair
KeyPairGenerator keyGen = KeyPairGenerator.getInstance("Kyber", "BC");
keyGen.initialize(KyberParameterSpec.kyber768);  // ML-KEM-768 (recommended)
KeyPair kyberKeyPair = keyGen.generateKeyPair();

// Generate ML-DSA (Dilithium) key pair
KeyPairGenerator sigGen = KeyPairGenerator.getInstance("Dilithium", "BC");
sigGen.initialize(DilithiumParameterSpec.dilithium3);  // ML-DSA-65 (recommended)
KeyPair dilithiumKeyPair = sigGen.generateKeyPair();
```

**Upgrade recommendation**: ✅ Upgrade to Bouncy Castle 1.79+ to enable PQC algorithm support

### Java Native PQC Support (Coming Soon)

**Java 24 (Expected March 2025)**:

**JEP 496**: Quantum-Resistant Module-Lattice-Based Key Encapsulation Mechanism
- Native JCA support for ML-KEM (Kyber)
- No external dependencies required
- Status: Preview feature in JDK 24

**JEP 497**: Quantum-Resistant Module-Lattice-Based Digital Signature Algorithm
- Native JCA support for ML-DSA (Dilithium)
- No external dependencies required
- Status: Preview feature in JDK 24

**Current status (January 2025)**: Not yet in production JDK builds

**Timeline**:
- **Java 24 (March 2025)**: Preview features
- **Java 25-26 (2026-2027)**: Production-ready
- **Java LTS (~2027)**: Stable PQC support in LTS release

**Implication**: Bouncy Castle provides the most mature Java PQC implementation today. Native JCA support coming 2025-2027.

## "Harvest Now, Decrypt Later" Attack

### Threat Description

**Attack scenario**:
1. Adversary records encrypted network traffic today (2025)
2. Stores encrypted data for 10-15 years
3. Decrypts data in 2035-2040 when large-scale quantum computers available
4. Accesses historical confidential communications

**Why it matters**:
- ⚠️ **Attack is happening RIGHT NOW** (adversaries are recording traffic)
- 🔒 **Encryption appears secure today** (RSA-2048 unbreakable classically)
- ⏱️ **Future vulnerability** (quantum computers will break historical encryption)
- 📜 **Long-lived data at risk** (messages from 2025 compromised in 2040)

### Affected Data in Openfire

**High-risk data** (TLS-encrypted, RSA vulnerable):
- ✅ **XMPP messages**: C2S and S2S encrypted with TLS/RSA
- ✅ **Admin console sessions**: HTTPS with RSA (credentials, configuration changes)
- ✅ **Federation traffic**: S2S between Openfire servers (organisational communications)
- ✅ **File transfers**: If using TLS (documents, images)
- ✅ **Multi-User Chat (MUC)**: Group conversations over TLS

**Examples of sensitive data at risk**:
- Corporate communications (strategy discussions, M&A negotiations)
- Healthcare messages (HIPAA-protected patient data)
- Government communications (classified/sensitive information)
- Financial transactions (banking, trading communications)
- Personal conversations (privacy-sensitive messages)

### NOT Affected (Quantum-Resistant Today)

**Low-risk data** (Symmetric encryption, quantum-resistant):
- ✅ **Database properties**: Encrypted with Blowfish/PBKDF2 (quantum-resistant)
- ✅ **Passwords**: Hashed with bcrypt/scrypt (quantum-resistant with proper parameters)
- ✅ **Configuration files**: Encrypted with AES (quantum-resistant)
- ✅ **security.xml**: Local file, not transmitted over network

### Risk Assessment by Use Case

| Use Case | Risk Level | Reason |
|----------|------------|--------|
| **Public XMPP chat** | Low | No confidentiality expectation |
| **Corporate internal messaging** | High | Business-sensitive communications |
| **Healthcare messaging (HIPAA)** | Critical | Regulatory compliance requirements |
| **Government/military** | Critical | National security implications |
| **Financial services** | High | Trading strategies, M&A discussions |
| **Personal messaging** | Moderate | Privacy expectations |

### Mitigation Timeline

**Immediate (2025-2026)**:
- Document risk in security policies
- Inform users of "harvest now, decrypt later" threat
- Prioritise hybrid TLS implementation

**Short-term (2026-2028)**:
- Deploy hybrid TLS (classical + PQC)
- New connections quantum-resistant
- Historical data still vulnerable

**Long-term (2028+)**:
- PQC-only connections default
- Minimise exposure window for new data
- Historical data (2025-2028) remains at risk

**Cannot be fully mitigated**: Data recorded before PQC deployment will remain vulnerable forever.

## Recommended Migration Strategy

### Phase 1: Immediate (2025) - Planning & Preparation

**Timeline**: Now - Q4 2025

**Objective**: Establish foundation for PQC migration without production changes

**Actions**:

1. **Upgrade Bouncy Castle to 1.79+**
   - Enable ML-KEM, ML-DSA, SLH-DSA support
   - Test PQC algorithm availability
   - Update pom.xml:
     ```xml
     <bouncycastle.version>1.79.0</bouncycastle.version>
     ```

2. **Document current state (this ADR)**
   - Quantum threat assessment
   - Component-by-component security analysis
   - Migration strategy roadmap

3. **Research XMPP ecosystem**
   - Survey other XMPP servers (ejabberd, Prosody, Tigase)
   - Identify PQC roadmaps and timelines
   - Coordinate with XMPP Standards Foundation (XSF)

4. **Monitor standards development**
   - IETF TLS 1.3 post-quantum extensions
   - Java 24 JEP 496/497 progress
   - NIST Round 4 algorithm standardisation

5. **Create prototype environment**
   - Development Openfire instance with Bouncy Castle 1.79+
   - Test ML-KEM key generation
   - Test ML-DSA certificate generation
   - Measure performance impact

**Deliverables**:
- ✅ ADR-007 (this document)
- 📋 Bouncy Castle 1.79+ upgrade
- 📋 PQC prototype environment
- 📋 XMPP ecosystem survey report

**Urgency**: Low - No production changes required, planning phase only

### Phase 2: Short-term (2026-2028) - Hybrid TLS Implementation

**Timeline**: Q1 2026 - Q4 2028

**Objective**: Deploy hybrid cryptography (classical + PQC) for TLS connections

**Hybrid Cryptography Approach**:
- Combines classical algorithm (RSA-2048) with PQC algorithm (ML-KEM-768)
- Secure if **EITHER** classical **OR** PQC algorithm remains unbroken
- Provides backward compatibility with non-PQC clients/servers
- Industry-standard transition mechanism

**Security guarantee**:
```
Hybrid Security = RSA-2048 AND/OR ML-KEM-768
- If quantum computer breaks RSA: ML-KEM-768 protects connection
- If ML-KEM-768 has cryptographic flaw: RSA-2048 protects connection
- Both must be broken simultaneously to compromise connection
```

**Implementation Steps**:

1. **Add hybrid certificate generation** (Q1-Q2 2026)
   ```java
   // IdentityStore.java enhancement
   public synchronized void addHybridDomainCertificate() throws Exception {
       String algorithm = JiveGlobals.getProperty("cert.algorithm", "RSA");
       String pqcAlgorithm = JiveGlobals.getProperty("cert.pqc.algorithm", "Kyber");

       // Generate classical key pair (RSA-2048)
       KeyPair rsaKeyPair = generateKeyPair("RSA", 2048);

       // Generate PQC key pair (ML-KEM-768)
       Security.addProvider(new BouncyCastleProvider());
       KeyPairGenerator kyberGen = KeyPairGenerator.getInstance("Kyber", "BC");
       kyberGen.initialize(KyberParameterSpec.kyber768);
       KeyPair kyberKeyPair = kyberGen.generateKeyPair();

       // Create hybrid certificate (both keys embedded)
       X509Certificate cert = CertificateManager.createHybridCertificate(
           rsaKeyPair, kyberKeyPair, validityInDays, name, signAlgorithm
       );

       // Store in keystore
       store.setKeyEntry(alias, rsaKeyPair.getPrivate(), password,
                         new X509Certificate[]{cert});
   }
   ```

2. **Implement hybrid TLS handshake** (Q2-Q3 2026)
   - Negotiate hybrid cipher suites
   - Perform dual key exchange (RSA + Kyber)
   - Combine shared secrets: `final_secret = KDF(rsa_secret || kyber_secret)`

3. **Add admin console configuration** (Q3 2026)
   - **TLS Mode setting**: `classical | hybrid | pqc-only`
   - **PQC Algorithm selection**: `ML-KEM-512 | ML-KEM-768 | ML-KEM-1024`
   - **Signature Algorithm**: `ML-DSA-44 | ML-DSA-65 | ML-DSA-87`
   - **Default (2026-2028)**: `classical` (opt-in hybrid)
   - **Default (2028-2032)**: `hybrid` (default enabled)
   - **Default (2032+)**: `hybrid` (transition to `pqc-only`)

4. **S2S negotiation enhancement** (Q4 2026 - Q1 2027)
   - Advertise hybrid cipher suite support in XMPP stream features
   - Fallback to classical TLS if peer doesn't support hybrid
   - Log PQC negotiation success/failure for monitoring

5. **Client compatibility testing** (Q1-Q2 2027)
   - Test Spark client with hybrid TLS
   - Test other XMPP clients (Conversations, Gajim, Pidgin)
   - Document client compatibility matrix

6. **Performance benchmarking** (Q2 2027)
   - Measure handshake latency (classical vs hybrid)
   - Measure CPU usage (key generation, signing, verification)
   - Measure certificate size increase
   - Tune buffer sizes and timeouts

7. **Production deployment** (Q3-Q4 2027)
   - Enable hybrid TLS in production (opt-in)
   - Monitor adoption rates
   - Collect performance metrics
   - Address compatibility issues

8. **Default to hybrid** (Q1 2028)
   - Change default from `classical` to `hybrid`
   - Classical-only TLS still supported (fallback)
   - Document migration procedure for administrators

**Deliverables**:
- Hybrid certificate generation
- Hybrid TLS handshake implementation
- Admin console PQC configuration page
- S2S negotiation with hybrid support
- Client compatibility matrix
- Performance benchmarking report
- Production deployment guide

**Urgency**: Moderate - Plan to deploy by 2028 to protect against "harvest now, decrypt later"

### Phase 3: Medium-term (2028-2032) - Hybrid TLS Default

**Timeline**: Q1 2028 - Q4 2032

**Objective**: Make hybrid TLS the default, monitor PQC adoption, prepare for PQC-only migration

**Actions**:

1. **Hybrid TLS default** (Q1 2028)
   - Default `cert.tls.mode = hybrid`
   - Classical-only available as legacy fallback
   - Log warnings for classical-only connections

2. **Monitor XMPP ecosystem** (2028-2032)
   - Track other XMPP servers' PQC adoption
   - Coordinate with XSF on migration timelines
   - Participate in interoperability testing events

3. **Certificate authority coordination** (2028-2030)
   - Work with CAs to support hybrid certificates
   - Test commercial CA-issued hybrid certificates
   - Document CA compatibility

4. **Performance optimisation** (2028-2032)
   - Hardware acceleration for ML-KEM/ML-DSA (if available)
   - Optimise Bouncy Castle usage
   - Reduce handshake latency

5. **Deprecation warnings** (2030-2032)
   - Log warnings for classical-only TLS connections
   - Admin console alerts for RSA-only certificates
   - Prepare users for PQC-only transition

**Deliverables**:
- Hybrid TLS default in Openfire
- PQC adoption metrics
- CA hybrid certificate support
- Performance optimisation improvements
- Deprecation warning system

**Urgency**: Moderate - Maintain hybrid support, prepare for PQC-only future

### Phase 4: Long-term (2032-2035) - PQC-Only Migration

**Timeline**: Q1 2032 - Q4 2035

**Objective**: Deprecate classical-only TLS, migrate to PQC-only connections

**Actions**:

1. **Assess quantum threat** (Q1 2032)
   - Review latest quantum computer capabilities
   - Assess RSA-2048 security status
   - Decide on PQC-only timeline (2033, 2034, or 2035)

2. **PQC-only mode available** (Q2 2032)
   - Add `cert.tls.mode = pqc-only` option
   - Reject classical-only TLS connections
   - Test in production with early adopters

3. **Coordinate ecosystem migration** (2032-2034)
   - Work with XMPP community on PQC-only timeline
   - Ensure major clients support PQC
   - Coordinate with other server implementations

4. **Default to PQC-only** (2034-2035)
   - Default `cert.tls.mode = pqc-only`
   - Classical algorithms available only for legacy support
   - Log errors for classical-only connection attempts

5. **Remove RSA/ECDH support** (2035+)
   - Deprecate RSA key generation
   - Remove classical-only cipher suites
   - PQC-only Openfire

**Deliverables**:
- PQC-only mode implementation
- Ecosystem coordination plan
- Migration timeline
- Final RSA/ECDH deprecation

**Urgency**: Low today - Timeline depends on quantum computer development

### Migration Timeline Summary

```
2025: ████ Planning & Preparation (Bouncy Castle upgrade, prototyping)
2026: ████ Hybrid TLS Development
2027: ████ Hybrid TLS Testing & Production Deployment
2028: ████ Hybrid TLS Default
2029: ████ Monitor & Optimise
2030: ████ Monitor & Optimise
2031: ████ Monitor & Optimise
2032: ████ PQC-Only Development & Testing
2033: ████ Coordinate Ecosystem Migration
2034: ████ PQC-Only Default
2035: ████ Remove RSA/ECDH Support
```

## Decision

### Property Encryption

**Decision**: No changes required.

**Rationale**:
- Current PBKDF2-HMAC-SHA512 implementation provides ~128-bit post-quantum security
- 256-bit keys and 256-bit salts provide quantum-resistant collision protection
- 100,000 iterations slow brute-force attacks (quantum or classical)
- Meets NIST post-quantum cryptography standards
- No further changes needed

**Status**: ✅ Quantum-resistant today

### TLS/SSL Encryption

**Decision**: Implement hybrid TLS (classical + PQC) starting 2026-2028.

**Rationale**:
- RSA-2048 remains secure today but vulnerable to future quantum computers
- "Harvest now, decrypt later" attacks create urgency for PQC adoption
- Hybrid approach provides security if either classical OR PQC algorithm remains unbroken
- Backward compatibility with non-PQC clients/servers
- Industry-standard migration path (NIST, IETF, XMPP community)
- Bouncy Castle 1.79+ provides mature ML-KEM/ML-DSA implementation

**Timeline**:
- **2026-2028**: Develop and deploy hybrid TLS (opt-in)
- **2028-2032**: Hybrid TLS default
- **2032-2035**: Transition to PQC-only
- **2035+**: Deprecate RSA/ECDH

### Technology Choices

**Key Encapsulation Mechanism (KEM)**: ML-KEM-768 (CRYSTALS-Kyber)
- NIST FIPS 203 standardised
- Category 3 security (equivalent to AES-192)
- Balanced security vs performance
- Supported by Bouncy Castle 1.79+

**Digital Signature Algorithm (DSA)**: ML-DSA-65 (CRYSTALS-Dilithium)
- NIST FIPS 204 standardised
- Category 3 security
- Reasonable signature size
- Supported by Bouncy Castle 1.79+

**Backup Signature Algorithm**: SLH-DSA (SPHINCS+)
- NIST FIPS 205 standardised
- Hash-based (conservative security assumption)
- Larger signatures, but more robust if lattice-based crypto compromised

**Hybrid Approach**: RSA-2048 + ML-KEM-768, SHA256withRSA + ML-DSA-65
- Security if either algorithm remains unbroken
- Backward compatibility
- Industry best practice

### Upgrade Strategy

**Bouncy Castle**: Upgrade to 1.79+ (Q1-Q2 2025)
- Enables ML-KEM, ML-DSA, SLH-DSA support
- NIST-standardised algorithms (not Round 3 candidates)
- Mature API, production-ready

**Java**: Monitor JEP 496/497 progress
- Java 24 (March 2025): Preview features
- Java 25-26 (2026-2027): Production-ready native JCA support
- Migration from Bouncy Castle to native JCA when stable (2027+)

## Consequences

### Property Encryption (Current State)

✅ **Strong quantum resistance**: 256-bit PBKDF2 keys provide 128-bit post-quantum security

✅ **NIST compliant**: Exceeds NIST minimum recommendations

✅ **Future-proof**: No further changes needed for quantum era

✅ **Backward compatible**: Supports both SHA1 (legacy) and PBKDF2 (current) KDFs

✅ **Secure salt generation**: 256-bit salts quantum-resistant

✅ **Already implemented**: No migration required for new installations

### TLS/SSL Hybrid Migration

✅ **Protection against quantum attacks**: Hybrid TLS secure even with quantum computers

✅ **Backward compatibility**: Fallback to classical TLS for non-PQC clients/servers

✅ **Defence-in-depth**: Secure if either classical OR PQC algorithm remains unbroken

✅ **Industry alignment**: Follows NIST, IETF, XMPP community standards

✅ **Gradual migration**: Phased approach reduces risk

❌ **Increased complexity**: Hybrid TLS more complex than classical-only

❌ **Certificate size increase**: Hybrid certificates larger (both RSA + Kyber keys)

❌ **Performance overhead**: Hybrid handshake slower than classical (~10-50ms additional latency)

❌ **Client compatibility**: Older XMPP clients may not support hybrid TLS (fallback to classical)

❌ **Long migration timeline**: 10-15 years for complete PQC-only migration

### "Harvest Now, Decrypt Later" Mitigation

✅ **Future connections protected**: Hybrid TLS (2028+) protects against future quantum decryption

✅ **Reduced exposure window**: Earlier PQC adoption reduces at-risk data

❌ **Historical data vulnerable**: Data transmitted 2025-2028 remains at risk

❌ **Cannot be fully mitigated**: Pre-PQC data vulnerable forever

❌ **Requires user awareness**: Users must understand quantum threat timeline

### Ecosystem Coordination

✅ **XMPP community alignment**: Migration coordinated with other XMPP servers

✅ **Certificate authority support**: CAs will support hybrid certificates (2028+)

✅ **Client support**: Major XMPP clients expected to support PQC (2027+)

❌ **Fragmentation risk**: Incompatible PQC implementations across ecosystem

❌ **Delayed adoption**: Some clients/servers may lag in PQC support

❌ **Interoperability challenges**: Testing required across diverse XMPP implementations

### Bouncy Castle Dependency

✅ **Already integrated**: No new dependency required

✅ **Mature implementation**: Bouncy Castle 1.79+ production-ready

✅ **Active maintenance**: Regular updates, security patches

✅ **JCA compatible**: Standard Java cryptography API

❌ **External dependency**: Reliance on third-party library (not native Java)

❌ **Version upgrade required**: Must upgrade from 1.78.1 to 1.79+

❌ **Potential API changes**: Future Bouncy Castle versions may break compatibility

### Performance Impact

✅ **Symmetric encryption unaffected**: AES/Blowfish performance unchanged

✅ **Modern hardware**: CPU performance improvements offset PQC overhead

❌ **TLS handshake slower**: Hybrid handshake ~10-50ms additional latency

❌ **Certificate size larger**: Increased network bandwidth for certificate exchange

❌ **CPU usage higher**: ML-KEM/ML-DSA computation more expensive than RSA

❌ **Memory usage higher**: Larger keys and certificates require more memory

### Security Posture

✅ **Property encryption**: Quantum-resistant today

✅ **TLS connections**: Quantum-resistant by 2028 (hybrid deployment)

✅ **Defence-in-depth**: Multiple layers of quantum-resistant encryption

✅ **Standards-compliant**: Follows NIST, OWASP, IETF recommendations

⚠️ **Transition window**: Data transmitted 2025-2028 vulnerable to "harvest now, decrypt later"

⚠️ **Quantum timeline uncertainty**: If quantum computers arrive earlier than expected, migration may be too slow

## Related

- **ADR-001**: Separate Obfuscation from Encryption (architectural split)
- **ADR-004**: Manual Migration Tool for Blowfish PBKDF2 Upgrade
- **ADR-005**: PBKDF2 Cryptographic Parameters for Blowfish Key Derivation
- **ADR-006**: AES CBC to GCM Migration (authenticated encryption)

## References

### Standards & Publications

- **NIST FIPS 203**: Module-Lattice-Based Key-Encapsulation Mechanism Standard (August 2024)
- **NIST FIPS 204**: Module-Lattice-Based Digital Signature Standard (August 2024)
- **NIST FIPS 205**: Stateless Hash-Based Digital Signature Standard (August 2024)
- **NIST SP 800-132**: Recommendation for Password-Based Key Derivation
- **NIST SP 800-63B**: Digital Identity Guidelines (Authentication and Lifecycle Management)
- **RFC 8018**: PKCS #5: Password-Based Cryptography Specification Version 2.1
- **OWASP Password Storage Cheat Sheet (2023)**: Password storage best practices

### Research & Industry Reports

- **Global Risk Institute**: Quantum Threat Timeline Report (2023)
  - 17-34% probability of breaking RSA-2048 by 2034
  - 79% probability of breaking RSA-2048 by 2044
- **Google Quantum AI**: "Factoring RSA-2048 with under 1 million noisy qubits" (2025)
- **SecurityWeek Cyber Insights 2025**: Quantum and the Threat to Encryption
  - "2025 is probably our last chance to start migration to post quantum cryptography"
- **Fortinet**: Understanding Shor's and Grover's Algorithms
- **Freemindtronic**: Quantum Threats to Encryption: RSA, AES & ECC Defence

### Technical Resources

- **Bouncy Castle 1.79**: Latest NIST PQC Standards and more
- **Java JEP 496**: Quantum-Resistant Module-Lattice-Based Key Encapsulation Mechanism
- **Java JEP 497**: Quantum-Resistant Module-Lattice-Based Digital Signature Algorithm
- **InfoQ**: Post-Quantum Cryptography in Java (December 2024)
- **Stack Overflow**: Implementing CRYSTALS-Kyber using BouncyCastle Java

### XMPP Ecosystem

- **XMPP Standards Foundation (XSF)**: https://xmpp.org/
- **ejabberd**: XMPP server implementation
- **Prosody**: Lightweight XMPP server
- **Tigase**: Scalable XMPP server
- **Spark**: XMPP client (Ignite Realtime)
- **Conversations**: Modern XMPP client (Android)