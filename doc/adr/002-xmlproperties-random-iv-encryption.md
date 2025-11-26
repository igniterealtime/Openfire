# ADR-002: Use Random IVs for XMLProperties Encryption

## Status

Accepted

## Context

With the architectural split decided in ADR-001, we must determine how XML properties in `openfire.xml` should be protected. These properties are currently marked as "encrypted" but use a hardcoded IV, making encryption deterministic.

**Key question**: Should XMLProperties use `Obfuscator` (accepting it as obfuscation) or upgrade to real encryption with random IVs?

**Security boundary analysis**:

- **`openfire.xml`**: Contains encrypted property values
- **`security.xml`**: Contains the master encryption key (obfuscated with legacy constants)

**Critical insight**: The master encryption key in `security.xml` is randomly generated, NOT the hardcoded legacy constants. The legacy constants are only used to obfuscate this randomly generated key.

### Threat Scenarios

**Scenario A: Attacker has BOTH files** (`openfire.xml` + `security.xml`):

- Can deobfuscate master key using public legacy constants
- Can decrypt ALL encrypted properties regardless of IV strategy
- **Conclusion**: IV protection is irrelevant in this scenario

**Scenario B: Attacker has ONLY `openfire.xml`** (without `security.xml`):

- Cannot decrypt individual properties (no master key)
- **With hardcoded IV**: Vulnerable to pattern analysis, rainbow tables, pre-computed dictionary attacks
- **With random IV**: Protected against pattern analysis even without master key
- **Real-world examples**:
  - Bug in logging code logs encrypted property values
  - Backup system separates config files
  - Data breach exposes `openfire.xml` but filesystem permissions protected `security.xml`
- **Conclusion**: Random IVs provide meaningful protection in realistic leakage scenarios

## Options Considered

### Option A: Use Obfuscator for XMLProperties

**Description**: Accept XMLProperties as obfuscation, update to use `Obfuscator` class explicitly.

**Consequences**:

- ✅ Simplest implementation
- ✅ Backward compatible (same deterministic output)
- ✅ No IV storage complexity
- ✅ Consistent with master key approach (both obfuscated)
- ❌ No protection against pattern analysis
- ❌ Same value encrypted multiple times produces identical output
- ❌ Vulnerable to rainbow table attacks if `openfire.xml` leaks
- ❌ Less secure than database properties (`JiveProperties` uses random IV)

### Option B: Upgrade to Real Encryption with Random IVs (CHOSEN)

**Description**: Use real encryption with a unique random IV for each property, stored alongside the encrypted value.

**Consequences**:

- ✅ Real encryption with cryptographic security
- ✅ Pattern analysis prevention (random IVs)
- ✅ Rainbow table resistance
- ✅ Protection against `openfire.xml` leakage without `security.xml`
- ✅ Consistent with `JiveProperties` database approach (both use random IV)
- ✅ Defense in depth for realistic leakage scenarios
- ❌ Slightly more complex (IV storage required)
- ❌ Larger XML files (additional IV data per property)

## Decision

We will upgrade XMLProperties to use **real encryption with random IVs**.

## Rationale

### Why Real Encryption (not Obfuscator)

Real-world scenarios exist where `openfire.xml` leaks without `security.xml`:

- **Logging bugs**: Code accidentally logs encrypted values
- **Backup separation**: Different backup policies for config vs secrets
- **Version control**: Accidental commits of config files
- **Partial data breach**: Attacker gains access to config directory but not security directory

Random IVs provide meaningful protection in these scenarios by preventing:

- Pattern analysis (same value → different ciphertext)
- Rainbow tables (pre-computed attacks ineffective)
- Dictionary attacks (cannot identify common values)

**Defense in depth principle**: Even though an attacker with both files can decrypt everything, we should still protect against realistic partial leakage scenarios.

### Consistency with Database Properties

The `JiveProperties` class (database-stored properties) already uses random IVs. XMLProperties should match this security level for consistency across the codebase.

## Consequences

### Security Properties

- **Pattern resistance**: Same value encrypted multiple times produces different ciphertexts
- **Rainbow table resistance**: Pre-computed attacks ineffective with per-property random IVs
- **Leakage protection**: `openfire.xml` leakage without `security.xml` does not expose patterns
- **Backward compatible**: Old encrypted properties still decrypt correctly
- **Defense in depth**: Protects against realistic partial leakage scenarios

### What Becomes Easier

- XML properties have same security characteristics as database properties
- Pattern analysis attacks prevented
- Rainbow table attacks prevented
- Leakage of `openfire.xml` alone does not expose value patterns

### What Becomes Harder

- Slightly larger XML files (IV data per encrypted property)
- Must store IV alongside each encrypted value

### Follow-up Work

- See ADR-003 for IV storage format decision (how to store the IV)

## References

- **OF-3074**: Prevent hardcoded IV when encrypting parameters
- **ADR-001**: Separate Obfuscation from Encryption
- **ADR-003**: Store IV as XML Attribute
