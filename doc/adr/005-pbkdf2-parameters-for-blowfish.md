# ADR-005: PBKDF2 Cryptographic Parameters for Blowfish Key Derivation

## Status
Accepted

## Context

As part of OF-3075, we are replacing Blowfish's weak single-round SHA1 key derivation with a modern key derivation function (KDF). This requires selecting specific cryptographic parameters that will persist for years across many Openfire installations.

**Current weakness** (SHA1 key derivation):
```java
MessageDigest digest = MessageDigest.getInstance("SHA1");
digest.update(password.getBytes());
byte[] key = digest.digest(); // Single-round, no salt, fast
```

**Problems with current approach**:
- SHA1 is fast (enables brute-force attacks)
- No salt (same password always produces same key)
- Single iteration (vulnerable to rainbow table attacks)
- Not designed for password/key derivation

**Decision scope**: Select the key derivation algorithm, hash function, iteration count, and salt size.

**Constraints**:
- Must work in Java 17 standard library (no additional dependencies preferred)
- Must be computationally expensive enough to resist brute-force
- Must be fast enough for acceptable server startup time
- Must meet or exceed current security standards (OWASP, NIST)
- Must be widely accepted in enterprise environments

**Important context**: The input being hashed is the **master encryption key**, which is:
- Randomly generated (not user-chosen)
- High entropy (~256 bits of randomness)
- Used infrequently (only at server startup for key derivation)

This differs from user password hashing, where passwords are:
- User-chosen (potentially weak)
- Low entropy (common patterns, dictionary words)
- Validated frequently (every login)

**Implications**: For a random 256-bit master key, brute-force attacks are computationally infeasible even with low iteration counts. However, we still apply strong KDF for defense-in-depth and to follow cryptographic best practices.

## Options Considered

### Option 1: PBKDF2-HMAC-SHA256, 600,000 iterations, 16-byte salt

**Description**: Use PBKDF2 with SHA256 following current OWASP 2023 recommendations for password hashing.

**Implementation**:
```java
SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 600000, 256);
SecretKey key = factory.generateSecret(spec);
```

**Consequences**:

✅ **Meets OWASP 2023 password hashing recommendations**: OWASP Password Storage Cheat Sheet recommends 600,000 iterations for PBKDF2-HMAC-SHA256 (user passwords)

✅ **In Java standard library**: No external dependencies required

✅ **Widely supported**: Works on all Java 8+ platforms

❌ **Very slow**: ~1200ms estimated key derivation on typical server hardware

❌ **Overkill for random master key**: OWASP recommendations target user-chosen passwords (low entropy), not random keys

❌ **Impacts developer experience**: Slow demoboot restarts during development

❌ **SHA256 less future-proof than SHA512**: Smaller internal state

**Performance** (estimated):
- ~1200ms key derivation
- Noticeable delay, especially in development

**Analysis**: These parameters are designed for user-chosen passwords vulnerable to dictionary attacks. Our random master key doesn't benefit proportionally from such high iteration counts.

---

### Option 2: PBKDF2-HMAC-SHA512, 210,000 iterations, 32-byte salt

**Description**: Use PBKDF2 with SHA512 following current OWASP 2023 recommendations for password hashing with SHA512.

**Implementation**:
```java
SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 210000, 256);
SecretKey key = factory.generateSecret(spec);
```

**Consequences**:

✅ **Meets OWASP 2023 recommendations**: OWASP recommends 210,000 iterations for PBKDF2-HMAC-SHA512 (user passwords)

✅ **SHA512 more future-proof**: Larger internal state, more resistant to theoretical attacks

✅ **Larger salt**: 32 bytes provides better collision resistance

✅ **In Java standard library**: No external dependencies required

❌ **Still quite slow**: ~420ms estimated key derivation

❌ **Still overkill for random master key**: Designed for low-entropy passwords

❌ **Impacts developer experience**: Noticeable delay in demoboot restarts

**Performance** (estimated):
- ~420ms key derivation
- Noticeable but acceptable

**Analysis**: While meeting OWASP recommendations for user passwords, the benefit for a random high-entropy master key doesn't justify the performance cost.

---

### Option 3: PBKDF2-HMAC-SHA512, 100,000 iterations, 32-byte salt (CHOSEN)

**Description**: Use PBKDF2 with SHA512, with iteration count balanced for random master key use case.

**Implementation**:
```java
SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 100000, 256);
SecretKey key = factory.generateSecret(spec);
```

**Consequences**:

✅ **Exceeds NIST minimums**: NIST SP 800-132 recommends minimum 1,000 iterations for general use, 10,000 for password authentication (our 100,000 is 10x-100x higher)

✅ **Appropriate for random key**: Significantly strengthens even high-entropy input without excessive cost

✅ **SHA512 future-proof**: Larger internal state, no length extension concerns

✅ **Large salt**: 32 bytes (256 bits) provides quantum-resistant collision resistance

✅ **In Java standard library**: No external dependencies required

✅ **Acceptable performance**: ~200ms estimated (acceptable for startup-time operation)

✅ **Good developer experience**: Minimal impact on demoboot restarts

❌ **Below OWASP 2023 password recommendations**: OWASP recommends 210,000 for SHA512 (but those are for user-chosen passwords)

❌ **May need increase if threat model changes**: If master key entropy is reduced

**Performance** (estimated):
- ~200ms key derivation on typical server hardware
- Negligible impact on startup time

**Security analysis**:
- Random 256-bit master key: brute-force infeasible (2^256 attempts)
- 100,000 iterations adds computational cost without changing infeasibility
- Provides defense-in-depth if master key generation has unforeseen weakness
- Protects against timing attacks and strengthens against future attacks

---

### Option 4: PBKDF2-HMAC-SHA512, 200,000 iterations, 32-byte salt

**Description**: Use PBKDF2 with SHA512, approaching OWASP recommendations but not full 210,000.

**Implementation**:
```java
SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 200000, 256);
SecretKey key = factory.generateSecret(spec);
```

**Consequences**:

✅ **Nearly meets OWASP 2023 recommendations**: 95% of recommended iterations

✅ **Maximum security margin**: Highest iteration count considered

✅ **SHA512 + large salt**: Strongest PBKDF2 configuration considered

❌ **Diminishing returns for random key**: 100k → 200k doubles cost but master key brute-force already infeasible

❌ **~400ms startup delay** (estimated): Noticeable in development

❌ **Impacts developer experience**: Slower demoboot restarts

❌ **Not justified for random key**: Performance cost not proportional to security benefit

**Performance** (estimated):
- ~400ms key derivation
- Noticeable delay

**Diminishing returns analysis**: For user-chosen passwords, 200k vs 100k iterations meaningfully increases attack cost. For random 256-bit keys, both make brute-force infeasible - the additional security benefit is marginal.

---

### Option 5: Argon2id with default parameters

**Description**: Use Argon2id (winner of Password Hashing Competition 2015), the modern recommended algorithm for password hashing.

**Implementation**:
```java
// Uses existing dependency: org.bouncycastle:bcprov-jdk18on:1.78.1
Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
    .withIterations(3)
    .withMemoryAsKB(65536) // 64 MB
    .withParallelism(4)
    .withSalt(salt)
    .build();
Argon2BytesGenerator generator = new Argon2BytesGenerator();
generator.init(params);
byte[] key = new byte[32];
generator.generateBytes(password.toCharArray(), key);
```

**Consequences**:

✅ **State-of-the-art algorithm**: Modern, actively maintained, designed specifically for password hashing

✅ **Memory-hard**: Resistant to GPU and ASIC attacks (requires significant RAM per attempt)

✅ **Configurable memory cost**: Can tune memory/time trade-off

✅ **OWASP recommended**: OWASP Password Storage Cheat Sheet lists Argon2id as first choice for new applications

✅ **Future-proof**: Likely to remain secure longer than PBKDF2

✅ **Bouncy Castle already available**: Openfire already depends on org.bouncycastle:bcprov-jdk18on:1.78.1

❌ **Requires Bouncy Castle APIs**: More complex than Java stdlib (javax.crypto), different programming model

❌ **More complex configuration**: Multiple parameters to tune (memory, iterations, parallelism)

❌ **Overkill for master key**: Memory-hardness primarily benefits user password hashing (low entropy passwords)

❌ **Harder to implement correctly**: More parameters = more ways to configure incorrectly

❌ **Memory usage**: 64MB per derivation may cause issues in memory-constrained environments

**Why memory-hardness matters less for random keys**:
- User passwords: Low entropy (dictionary words), memory-hardness defeats GPU/ASIC farms
- Master key: High entropy (random 256 bits), brute-force already infeasible regardless of memory cost
- Memory-hardness primarily valuable when attacker can try billions of passwords
- Random 256-bit key: attacker cannot try even a tiny fraction of keyspace

**Performance** (estimated):
- ~200-500ms depending on memory configuration
- Memory usage: 64MB per derivation

**Analysis**: While Argon2id is technically superior for user password hashing, its advantages don't apply to random high-entropy master keys. Using Bouncy Castle APIs adds complexity without proportional benefit.

---

### Option 6: bcrypt with cost factor 12

**Description**: Use bcrypt, a widely deployed password hashing algorithm.

**Implementation** (requires NEW dependency):
```java
// Requires adding: org.springframework.security:spring-security-crypto
// or: org.mindrot:jbcrypt (new dependency not currently in project)
String hashed = BCrypt.hashpw(password, BCrypt.gensalt(12));
```

**Consequences**:

✅ **Widely deployed**: Used by many applications, proven track record

✅ **Automatic salt generation**: Built-in salt handling

✅ **Simple API**: Easy to use correctly

❌ **Requires NEW external dependency**: Not in Java stdlib, not in current dependencies

❌ **Limited output size**: 192-bit output (vs 256-bit for PBKDF2/Argon2)

❌ **Fixed memory cost**: Cannot configure memory hardness like Argon2

❌ **Less configurable**: Cost factor is only tuning parameter

❌ **Designed for passwords**: Not specifically designed for key derivation

**Analysis**: Requires adding a new dependency for marginal benefit over PBKDF2 from stdlib.

---

### Option 7: scrypt with standard parameters

**Description**: Use scrypt, a memory-hard key derivation function.

**Implementation**:
```java
// Uses existing dependency: org.bouncycastle:bcprov-jdk18on:1.78.1
SCrypt.generate(password.getBytes(), salt, 16384, 8, 1, 32);
```

**Consequences**:

✅ **Memory-hard**: Resistant to hardware attacks

✅ **Well-studied**: Older than Argon2, more deployment history

✅ **OWASP accepted**: Listed as acceptable alternative

✅ **Bouncy Castle already available**: Already a dependency

❌ **Requires Bouncy Castle APIs**: More complex than Java stdlib

❌ **Superseded by Argon2**: Argon2 generally preferred for new implementations

❌ **More complex than PBKDF2**: Multiple parameters to configure

❌ **Overkill for master key**: Same reasoning as Argon2 - memory-hardness not beneficial for random keys

**Analysis**: Memory-hardness advantages don't apply to random master keys. PBKDF2 simpler and sufficient.

---

## Decision

We will use **Option 3: PBKDF2-HMAC-SHA512, 100,000 iterations, 32-byte salt**.

## Rationale

### Why PBKDF2 over Argon2/bcrypt/scrypt?

**PBKDF2 advantages for random master key use case**:
1. **In Java standard library**: No Bouncy Castle APIs needed, just javax.crypto.SecretKeyFactory
2. **Proven and stable**: NIST approved (SP 800-132), used widely since 2000
3. **Sufficient for random key**: Memory-hardness (Argon2/scrypt) doesn't benefit random high-entropy keys
4. **Enterprise acceptance**: Widely accepted in security audits and compliance frameworks
5. **Simpler implementation**: Fewer parameters than Argon2/scrypt (just iterations, salt, output length)
6. **Lower complexity**: Standard Java APIs vs Bouncy Castle cryptographic provider APIs

**Why memory-hardness (Argon2/scrypt) not needed for our use case**:

Memory-hard functions defend against **parallelised GPU/ASIC attacks on low-entropy passwords**:
- User password: "Summer2023!" → attacker tries billions of common passwords
- Memory-hardness: Each attempt needs 64MB RAM → limits GPU parallelisation

Our master key is **randomly generated** (256 bits entropy):
- Brute-force space: 2^256 possible keys (computationally infeasible)
- Even 1 iteration: attacker cannot try meaningful fraction of keyspace
- Memory-hardness: No benefit when brute-force already impossible

**Real benefit of iterations for random key**: Defense-in-depth against unknown weaknesses in key generation or future attacks, not brute-force protection.

**PBKDF2 appropriate when**:
- Input has high entropy (our case: random master key)
- Simplicity and standards compliance prioritised
- Standard library implementation preferred
- Proven track record matters (enterprise, compliance)

**Argon2/scrypt preferred when**:
- Hashing user-chosen passwords (low entropy, vulnerable to dictionary attacks)
- Maximising resistance to GPU/ASIC attacks
- Memory-hardness provides meaningful security benefit

### Why SHA512 over SHA256?

1. **Larger internal state**: 512-bit vs 256-bit (more resistant to theoretical attacks)
2. **No length extension vulnerability**: While not exploitable in HMAC, SHA512 theoretically stronger
3. **Future-proofing**: More conservative choice
4. **Enterprise preference**: Many security policies explicitly specify SHA512
5. **Performance acceptable**: Only ~2x slower than SHA256, but operation is infrequent (startup only)
6. **No meaningful downside**: Cost difference negligible for startup-time operation

### Why 100,000 iterations (not 210,000 or 600,000)?

**Our decision must account for different threat models**:

**OWASP 2023 recommendations** (for user-chosen passwords):
- PBKDF2-HMAC-SHA256: 600,000 iterations
- PBKDF2-HMAC-SHA512: 210,000 iterations
- **Target threat**: Low-entropy user passwords (dictionary attacks, rainbow tables)

**NIST SP 800-132** (for key derivation):
- Minimum 1,000 iterations (general)
- Minimum 10,000 iterations (password authentication)
- **Target threat**: Various threat models including low and high entropy inputs

**Our use case**: Random 256-bit master key (high entropy)
- Brute-force already infeasible (2^256 keyspace)
- Iterations provide defense-in-depth, not primary security
- 100,000 iterations = 10x-100x NIST minimums
- Below OWASP password recommendations, but those target low-entropy inputs

**100,000 provides balanced security**:

**vs NIST minimums (1,000 - 10,000)**:
- ✅ 10x-100x higher than NIST requirements
- ✅ Significant defense-in-depth even for high-entropy input
- ✅ Protects against unforeseen weaknesses in key generation

**vs OWASP password recommendations (210,000+)**:
- ✅ ~200ms vs ~420ms startup time (acceptable trade-off)
- ✅ Minimal developer friction (fast demoboot restarts)
- ❌ Below OWASP user password recommendations (but we have random key, not user password)
- ✅ Can increase later if threat model changes (migration tool exists)

**Performance analysis**:
- 100,000 iterations: ~200ms (estimated)
- 210,000 iterations: ~420ms (estimated) - 2.1x slower
- Marginal security benefit for random key doesn't justify 2x performance cost

**Future adjustment**: If iteration count needs increase (changing threat models, new attacks, reduced master key entropy), migration tool already exists to re-encrypt all properties.

### Why 32-byte (256-bit) salt?

**OWASP recommendation**: Minimum 16 bytes (128 bits)
**NIST recommendation**: Minimum 16 bytes

**32 bytes chosen for**:
1. **Quantum resistance**: 256-bit salts remain secure against quantum attacks (Grover's algorithm)
2. **Collision resistance**: Birthday paradox: 128-bit = 2^64 collision probability, 256-bit = 2^128
3. **No performance cost**: Salt size doesn't affect key derivation speed (only stored, not computed)
4. **Future-proof**: Unlikely to need increase
5. **Matches key size**: 256-bit salt for 256-bit derived key (aesthetic consistency)

**Storage cost**: 32 bytes Base64-encoded = 44 characters in security.xml (negligible)

## Consequences

### Implementation

**Key derivation code** (`Blowfish.java`):
```java
public static byte[] deriveKeyPBKDF2(String password, byte[] salt) throws Exception {
    SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
    PBEKeySpec spec = new PBEKeySpec(
        password.toCharArray(),
        salt,
        100000,  // iterations
        256      // key length in bits
    );

    SecretKey secretKey = factory.generateSecret(spec);
    return secretKey.getEncoded();
}
```

**Salt generation** (one-time, on first startup):
```java
byte[] salt = new byte[32];
new SecureRandom().nextBytes(salt);
String saltBase64 = Base64.getEncoder().encodeToString(salt);
securityProperties.setProperty("encrypt.blowfish.salt", saltBase64);
```

### Security Properties

✅ **Exceeds NIST SP 800-132 minimums**: 100,000 iterations vs 1,000-10,000 recommended

✅ **Appropriate for random key**: Significantly strengthens even high-entropy input

✅ **Resistant to brute-force**: Random 256-bit key + 100,000 iterations = computationally infeasible attack

✅ **Resistant to rainbow tables**: Random per-installation salt makes pre-computed tables useless

✅ **Quantum-resistant salt**: 256-bit salt remains secure against quantum attacks

✅ **Enterprise acceptable**: SHA512 + high iterations meets most security policies

✅ **Defense-in-depth**: Protects against unknown weaknesses in master key generation

⚠️ **Below OWASP 2023 password recommendations**: But those target low-entropy user passwords; our random master key has different threat model

### Performance Impact

**Startup time addition**: ~200ms (estimated) on typical server hardware
- Acceptable for infrequent operation (only at startup)
- Negligible compared to overall startup time (10-30 seconds)

**Development impact**: ~200ms per demoboot restart
- Acceptable trade-off for security improvement
- Minimal friction for developers

**Production impact**: None (only runs once at startup)

### What Becomes Easier

- Security audits can verify compliance with NIST standards
- Parameters exceed NIST minimums, providing compliance buffer
- Can demonstrate significant security improvement over legacy SHA1
- Enterprise deployments can satisfy reasonable security policy requirements
- Clear documentation of trade-offs aids security review

### What Becomes Harder

- Parameters below OWASP 2023 password recommendations requires justification
- Must explain difference between user password hashing vs master key derivation in audits
- Cannot easily change parameters without migration (but migration tool exists)

### Standards Compliance

✅ **NIST SP 800-132** (Password-Based Key Derivation):
- Recommends minimum 1,000 iterations (general use)
- Recommends minimum 10,000 iterations (password authentication)
- Our choice: 100,000 iterations (10x-100x higher)

✅ **NIST SP 800-63B** (Digital Identity Guidelines):
- Recommends salted key derivation for stored secrets
- Our choice: 32-byte random salt (exceeds recommendation)

⚠️ **OWASP Password Storage Cheat Sheet** (2023):
- Recommends 210,000 iterations for PBKDF2-HMAC-SHA512 (user passwords)
- Our choice: 100,000 iterations (48% of recommendation)
- **Justification**: OWASP targets user-chosen passwords (low entropy); our master key is random (high entropy ~256 bits)

**Note on OWASP compliance**: OWASP recommendations are designed for the specific threat model of user-chosen passwords vulnerable to dictionary attacks. For randomly generated master keys, the threat model differs significantly. Our 100,000 iterations provides substantial computational cost while maintaining acceptable performance.

### Future Adjustment Path

If iteration count needs increase (changing threat models, new attacks, performance improvements allow):
1. Update `PBKDF2_ITERATIONS` constant in `Blowfish.java`
2. Use existing migration tool to re-encrypt all properties
3. Admin triggers migration via console
4. All properties re-encrypted with new iteration count

No database schema change needed (iteration count not stored per-property).

## Threat Model Clarification

**If master key has full 256-bit entropy**:
- Brute-force is infeasible with ANY iteration count (2^256 keyspace)
- Iterations provide defense-in-depth against implementation flaws

**If master key entropy is reduced** (unforeseen weakness):
- Higher iterations provide additional security margin
- Can migrate to higher count if weakness discovered

**Current security analysis**:
- Master key: Randomly generated using SecureRandom
- Expected entropy: ~256 bits (full entropy)
- Brute-force: Computationally infeasible
- Iterations: Defense-in-depth against unknown weaknesses

## References

- **OWASP Password Storage Cheat Sheet** (2023): https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
  - PBKDF2-HMAC-SHA256: 600,000 iterations (user passwords)
  - PBKDF2-HMAC-SHA512: 210,000 iterations (user passwords)
- **NIST SP 800-132**: Recommendation for Password-Based Key Derivation (Part 1: Storage Applications)
  - Minimum 1,000 iterations (general use)
  - Minimum 10,000 iterations (password authentication)
- **NIST SP 800-63B**: Digital Identity Guidelines (Authentication and Lifecycle Management)
- **RFC 8018**: PKCS #5 v2.1 - Password-Based Cryptography Specification
- **OF-3075**: Weak SHA1 hash used as key for Blowfish
