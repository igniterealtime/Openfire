# ADR-001: Separate Obfuscation from Encryption in Configuration Security

## Status

Accepted

## Context

Openfire's `AesEncryptor` class conflates two fundamentally different security concepts:

- **Obfuscation**: Hiding values from casual inspection using hardcoded public constants
- **Encryption**: Cryptographic protection using proper key management and random initialisation vectors

This conflation has led to security issues identified in OF-3074:

1. The master encryption key in `security.xml` is "encrypted" using hardcoded public constants
2. XML properties in `openfire.xml` are "encrypted" with a hardcoded IV, making encryption deterministic
3. The API makes it easy for plugin developers to accidentally use weak encryption

The core architectural issue is that a single class tries to serve two incompatible purposes:

- Backwards-compatible deterministic obfuscation (for config files)
- Cryptographically secure encryption (for sensitive data)

This makes security auditing difficult, encourages misuse, and conflates different threat models.

## Options Considered

### Option 1: Keep Single AesEncryptor Class (Status Quo)

**Description**: Continue using `AesEncryptor` for both obfuscation and encryption, but require IV parameter for new code while maintaining backward compatibility.

**Consequences**:

- ✅ Simplest change, minimal code modification
- ✅ No new classes to learn
- ✅ Backward compatibility maintained naturally
- ❌ Security intent remains unclear in code
- ❌ Easy to misuse (forget IV parameter)
- ❌ Difficult to audit (grep doesn't distinguish obfuscation from encryption)
- ❌ Conflates threat models (filesystem permissions vs cryptographic security)
- ❌ API encourages insecure usage as "easy path"
- ❌ Does not prevent future misuse by plugin developers

### Option 2: Add @RequiresIV Annotation

**Description**: Keep `AesEncryptor` but add compile-time or runtime checks using annotations to enforce IV usage.

**Consequences**:

- ✅ Some protection against misuse
- ✅ Can be enforced at build time
- ✅ Single class to maintain
- ❌ Still conflates obfuscation and encryption conceptually
- ❌ Annotation can be ignored or worked around
- ❌ Runtime checks add overhead
- ❌ Doesn't make intent explicit in code structure
- ❌ Complex annotation handling for backward compatibility

### Option 3: Split into Obfuscator and AesEncryptor Classes (CHOSEN)

**Description**: Create two separate classes with clear semantic differences:

- **`Obfuscator`**: Explicit deterministic obfuscation using hardcoded constants
- **`AesEncryptor`**: Real encryption requiring proper IV and key management

**Consequences**:

- ✅ Security by design: impossible to accidentally use weak encryption
- ✅ Clear intent: class name explicitly indicates purpose
- ✅ Audit-friendly: searching for `Obfuscator` finds all intentional obfuscation
- ✅ Self-documenting: code structure teaches correct usage
- ✅ Separate threat models: filesystem permissions (Obfuscator) vs cryptographic security (AesEncryptor)
- ✅ Future-proof: can deprecate `Obfuscator` later if needed
- ✅ Educational: forces developers to think about security intent
- ❌ More classes to maintain (but simpler individually)
- ❌ Breaking change for plugins using `AesEncryptor` directly
- ❌ One major version deprecation cycle needed

### Option 4: Deprecate Obfuscation Entirely

**Description**: Remove all obfuscation support, require proper encryption everywhere, migrate master key storage to OS keystore or environment variables.

**Consequences**:

- ✅ Highest security posture
- ✅ No legacy code to maintain
- ✅ Forces security best practices
- ❌ Major breaking change for all existing installations
- ❌ Complex migration for clustered deployments
- ❌ Platform-specific OS keystore integration required
- ❌ No backward compatibility path
- ❌ May not work in all deployment environments (containers, embedded systems)
- ❌ Out of scope for OF-3074 fix

## Decision

We will implement **Option 3: Split into `Obfuscator` and `AesEncryptor` classes**.

## Rationale

This decision provides the best balance of security improvement, clarity, and backward compatibility:

1. **Security by design**: The API structure prevents accidental weak encryption. Using `Obfuscator` requires explicitly choosing obfuscation, making the security trade-off visible.

2. **Clear intent**: Class names communicate purpose immediately:
   - `Obfuscator.obfuscate()` - clearly not secure encryption
   - `AesEncryptor.encrypt(value, iv)` - requires proper IV, indicating real encryption

3. **Audit-friendly**: Security audits can easily find all obfuscation by searching for `Obfuscator` usage.

4. **Manageable migration**: One major version deprecation cycle (5.1.0 deprecate, 6.0.0 remove) is acceptable for the security improvement gained.

5. **Addresses root cause**: Options 1 and 2 address symptoms but not the architectural problem. Option 4 is too disruptive for the scope of OF-3074.

The architectural split makes the different threat models explicit:

- **`Obfuscator`**: Security boundary is filesystem permissions
- **`AesEncryptor`**: Security boundary is cryptographic strength

## Consequences

### What Becomes Easier

- Security audits can quickly distinguish obfuscation from encryption
- Plugin developers cannot accidentally use weak encryption
- Code reviews can verify correct security intent
- Future deprecation of obfuscation (if desired) is straightforward
- Testing security properties is clearer

### What Becomes Harder

- Plugin developers must explicitly choose obfuscation vs encryption
- Need to maintain migration guide for plugins
- Two classes instead of one (but each is simpler)

### Breaking Changes

Plugins using `AesEncryptor` directly will need to migrate:

- Deprecated `encrypt(String)` method (no IV parameter) in 5.1.0
- Removed in 6.0.0
- Most plugins use `JiveGlobals.getPropertyEncryptor()` facade and are unaffected

### Follow-up Work

- See ADR-002 for XMLProperties encryption strategy

## References

- **OF-3074**: Prevent hardcoded IV when encrypting parameters
- **ADR-002**: Use Random IVs for XMLProperties Encryption
- **ADR-003**: Store IV as XML Attribute
