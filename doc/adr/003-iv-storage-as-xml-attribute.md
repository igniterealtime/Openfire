# ADR-003: Store IV as XML Attribute (Not Fernet)

## Status

Accepted

## Context

Building on ADR-002's decision to use random IVs for XMLProperties encryption, we must determine how to store the IV alongside each encrypted value.

**Key question**: What format should we use to store the IV with encrypted XML properties?

Two main approaches were considered:

1. **XML attribute**: Store IV as a separate attribute on the XML element
2. **Fernet tokens**: Use a self-contained token format that embeds IV, timestamp, and HMAC

## Options Considered

### Option A: Store IV as XML Attribute (CHOSEN)

**Description**: Store the IV as a Base64-encoded XML attribute alongside the encrypted value:

```xml
<property encrypted="true" iv="base64EncodedIV">ciphertext</property>
```

**Consequences**:

- ✅ Uses standard Java APIs only (`SecureRandom`, `Base64`)
- ✅ Simple implementation and maintenance
- ✅ Minimal overhead (16 bytes IV → ~24 chars Base64)
- ✅ Consistent with `JiveProperties` database approach (separate IV storage)
- ✅ Easy to inspect and debug
- ✅ Backward compatible (legacy format lacks `iv` attribute, signalling old encryption)
- ❌ IV stored separately from ciphertext (not self-contained)
- ❌ No authenticated encryption (no HMAC)

### Option B: Use Fernet Tokens

**Description**: Use Fernet token format with embedded IV, timestamp, and HMAC:

```xml
<property encrypted="true">fernetToken</property>
```

Token format: `Base64url(0x80 || Timestamp(8) || IV(16) || Ciphertext || HMAC-SHA256(32))`

**Consequences**:

- ✅ Standardised format (cryptography.io specification)
- ✅ Authenticated encryption (HMAC tamper detection)
- ✅ Self-contained (IV and timestamp embedded)
- ✅ Token expiry/rotation support
- ❌ **No standard Java library** (requires nascent third-party lib or custom implementation)
- ❌ Authentication not valuable for filesystem configs
- ❌ Timestamps not useful (config properties are permanent)
- ❌ Larger overhead (57 bytes vs 16 bytes for IV alone)
- ❌ Inconsistent with `JiveProperties` database approach
- ❌ More complex implementation and maintenance

## Decision

We will store the IV as an **XML attribute**, not use Fernet tokens.

## Rationale

### Why XML Attribute (not Fernet)

1. **No Java standard library**: Fernet would require:
   - Nascent third-party library (`com.macasaet.fernet`)
   - OR risky custom implementation
   - Google Tink does NOT support Fernet (verified)

2. **Authentication not valuable**: HMAC tamper detection provides no security benefit for configuration files:
   - Attacker with write access to `openfire.xml` can already:
     - Modify unencrypted settings
     - Inject malicious plugins
     - Change admin passwords
     - Redirect server configuration
   - Filesystem permissions are the true security boundary

3. **Timestamps not useful**:
   - Configuration properties are permanent, not ephemeral
   - No need for token expiry or rotation
   - Adds overhead without benefit

4. **Consistency with existing patterns**:
   - `JiveProperties` (database) uses separate IV storage in a dedicated column
   - XMLProperties should match this established approach

5. **Simplicity and maintainability**:
   - Standard Java APIs only
   - Minimal overhead
   - Easy to implement, test, and maintain
   - Sufficient security for the threat model

## Consequences

### XML Format

**Legacy format** (still readable, signals old encryption):

```xml
<property encrypted="true">oldEncryptedValue</property>
```

**New format**:

```xml
<property encrypted="true" iv="Y2Q5MWE3YzUyNzhiMzll">newEncryptedValue</property>
```

### Backward Compatibility

- Reading: Check for `iv` attribute; if missing, use legacy decrypt path
- Writing: Always use new format with random IV
- Auto-upgrade: Legacy properties re-encrypted on first access
- Migration: Seamless, no manual intervention required

### Trade-offs Accepted

**No authenticated encryption**: Acceptable because filesystem permissions are the true security boundary. An attacker with write access to `openfire.xml` has already compromised the system in ways that HMAC cannot prevent.

**No timestamp/expiry**: Acceptable because configuration properties are permanent. There is no rotation requirement for config values.

### Future Extensibility: Algorithm Identifiers

**Question raised during PR #3062 review**: Should we add an `algorithm` attribute (e.g., `algorithm="AES-128-CBC"`) to future-proof the format?

**Decision**: No. We will not add speculative attributes.

**Rationale**:

1. **YAGNI**: The current format is sufficient. Adding unused attributes increases complexity without benefit.

2. **Algorithm is global, not per-property**: The encryption algorithm is configured globally in `security.xml`. All properties use the same algorithm. An `algorithm` attribute would be redundant.

3. **Format change signals**: The absence of the `iv` attribute already signals legacy format. Future encryption changes can use similar absence-based detection or introduce new attributes when actually needed.

4. **Consistency**: Database properties (`ofProperty` table) don't have an algorithm column—they rely on global configuration. XML properties should match this approach.

5. **Algorithm changes require format changes anyway**: Moving to AES-GCM would require a different ciphertext structure (authentication tag), so an algorithm attribute wouldn't simplify migration.

If future encryption enhancements require additional metadata, we can add attributes at that time with clear backward compatibility logic, following the same pattern established with the `iv` attribute.

## References

- **OF-3074**: Prevent hardcoded IV when encrypting parameters
- **ADR-001**: Separate Obfuscation from Encryption
- **ADR-002**: Use Random IVs for XMLProperties Encryption
