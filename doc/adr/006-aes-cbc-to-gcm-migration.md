# ADR-006: Migrate AES Encryption from CBC to GCM Mode

## Status

Accepted

## Context

The `AesEncryptor` class uses `AES/CBC/PKCS7Padding` for symmetric encryption. CBC mode with PKCS7 padding is susceptible to padding oracle attacks, where an attacker can potentially recover plaintext by observing error responses when submitting modified ciphertext.

This vulnerability was identified in OF-3077: "Potential padding oracle CBC-mode encryption".

### Historical Context

Investigation during implementation revealed important historical context:

1. **OF-1533 (2018)**: `JiveProperties` (database-backed properties) added random IV support with CBC mode. This means production Openfire installations from 2018 onwards have encrypted database properties using `AES/CBC/PKCS7Padding` with random IVs.

2. **Pre-OF-1533**: Legacy encryption used a hardcoded IV with CBC mode for both XML properties and database properties.

3. **OF-3074 (2025)**: Added random IV support to `XMLProperties` (XML file-backed properties), still using CBC mode. Note: OF-3074 and OF-3077 are releasing together, so there is no production data encrypted with CBC + random IV in XML properties.

4. **OF-3077 (2025)**: This change - switching from CBC to GCM mode for new encryptions.

### Key Discovery

The existence of OF-1533 (2018) means there is approximately **7 years of production data** encrypted with CBC mode and random IVs in Openfire database tables (`ofProperty`). Any migration strategy must maintain backward compatibility with this data.

## Options Considered

### Option 1: Replace CBC with GCM, No Fallback

**Description**: Simply change the algorithm from CBC to GCM. Old data becomes unreadable.

**Consequences**:
- ✅ Simple implementation
- ✅ Clean codebase with single encryption mode
- ❌ **Breaks all existing Openfire installations** from 2018 onwards
- ❌ Database properties become unreadable after upgrade
- ❌ Requires manual data migration for every installation
- ❌ No automated upgrade path

### Option 2: GCM for New, CBC Fallback for Decryption (CHOSEN)

**Description**: Use GCM for all new encryptions. During decryption, try GCM first; if it fails, fall back to CBC.

**Consequences**:
- ✅ Seamless upgrade for existing installations
- ✅ All existing data remains readable
- ✅ New data benefits from authenticated encryption
- ✅ Gradual migration as properties are re-encrypted
- ✅ No manual intervention required
- ❌ Slight performance overhead for legacy data (two decryption attempts)
- ❌ CBC code must be maintained for backward compatibility
- ❌ Mixed encryption modes in database during transition period
- ❌ **Legacy CBC data remains vulnerable** until re-encrypted (see Residual Security Risk)

### Option 3: Add Version Prefix to Ciphertext

**Description**: Prepend a version byte to new GCM-encrypted data. During decryption, check prefix to determine algorithm.

**Consequences**:
- ✅ Explicit algorithm identification
- ✅ No fallback attempts needed
- ✅ Clear distinction between old and new data
- ❌ More complex implementation
- ❌ Requires format change for encrypted values
- ❌ Still need CBC fallback for data without prefix
- ❌ Database schema considerations for prefix storage

### Option 4: Forced Migration with Upgrade Script

**Description**: Provide an upgrade script that re-encrypts all properties during Openfire upgrade.

**Consequences**:
- ✅ Clean transition to GCM-only
- ✅ Removes need for fallback code eventually
- ❌ Complex upgrade process
- ❌ Requires database downtime
- ❌ Risk of data loss if migration fails
- ❌ Must handle encrypted properties where master key is external

## Decision

We will implement **Option 2: GCM for New, CBC Fallback for Decryption**.

## Rationale

1. **Backward compatibility is paramount**: Openfire installations have 7 years of accumulated encrypted data. Breaking this would cause significant disruption.

2. **Transparent upgrade**: Administrators should be able to upgrade Openfire without manual data migration steps.

3. **Gradual migration**: As properties are updated, they will be re-encrypted with GCM. Over time, the proportion of GCM-encrypted data will increase.

4. **Minimal risk**: The fallback approach has no risk of data loss. The worst case is a slight performance overhead for legacy data.

5. **Security improvement**: New data immediately benefits from GCM's authenticated encryption, which detects tampering.

### Why the Fallback Uses CBC, Not GCM

An important design decision: when no IV is provided (legacy mode), the implementation falls back to CBC rather than using GCM with a static IV. This is deliberate because **GCM with a reused IV is catastrophically worse than CBC with a reused IV**:

**CBC with hardcoded IV:**
- Produces deterministic encryption (same plaintext → same ciphertext)
- Enables pattern analysis attacks
- But damage is limited - no key recovery, no message forgery

**GCM with reused IV:**
- Catastrophic failure mode
- XORing two ciphertexts encrypted with the same IV reveals the XOR of the plaintexts
- Critically: attacker can recover the GHASH authentication key
- Once the authentication key is known, attacker can forge valid authentication tags for arbitrary messages
- This breaks both confidentiality AND authenticity completely

Therefore, for backward compatibility with legacy data that was encrypted without a random IV, CBC is the safer choice. GCM is only used when the caller provides a proper random IV.

## Residual Security Risk

The CBC fallback mechanism maintains backward compatibility but also **preserves the padding oracle vulnerability for legacy CBC-encrypted data**. This data remains theoretically vulnerable until it is re-encrypted with GCM.

### Threat Model for Openfire

For a padding oracle attack to succeed, an attacker must:

1. Submit modified ciphertext to the system
2. Observe differential responses (padding error vs successful decryption)
3. Repeat this process many times to recover plaintext byte-by-byte

For Openfire's encrypted configuration properties, this attack surface is limited:

- **Database properties (`ofProperty`)**: Attackers would need database write access to modify encrypted values, then observe application behaviour. If they have database access, they likely have more direct attack vectors.

- **XML properties (`openfire.xml`, `security.xml`)**: Attackers would need filesystem write access. Again, this level of access typically enables more direct attacks.

- **No external API in core Openfire**: The admin console and cluster synchronisation accept plaintext values; encryption occurs server-side. An attacker cannot submit pre-encrypted ciphertext to trigger decryption. (Note: third-party plugins may differ.)

### Risk Assessment

| Data Category | Vulnerability Status | Practical Risk |
|--------------|---------------------|----------------|
| New data (2025+) | Protected by GCM | None |
| Legacy DB properties (2018-2025) | CBC fallback preserves vulnerability | Low - requires DB access |
| Legacy XML properties (pre-2025) | CBC with hardcoded IV | Low - requires filesystem access |

### Accepted Risk

This residual risk is accepted because:

1. **Limited attack surface**: Exploiting the padding oracle requires access levels that typically enable more direct attacks
2. **Forward protection**: All new encrypted data is immediately protected by GCM
3. **Gradual migration**: Legacy data will be re-encrypted with GCM when properties are updated
4. **Operational harm of alternatives**: Breaking backward compatibility would cause greater operational disruption than the theoretical security risk

### Migration Path

Properties are re-encrypted with GCM when their values are updated through normal Openfire operation. There is no automatic background migration process. Administrators concerned about legacy data can manually update encrypted properties to trigger re-encryption.

## Consequences

### What Becomes Easier

- Upgrading Openfire without data migration
- Security audits (GCM is well-understood authenticated encryption)
- Future removal of CBC code (once all legacy data is migrated)

### What Becomes Harder

- Maintaining two encryption code paths
- Testing both GCM and CBC paths
- Explaining the mixed encryption modes to administrators

### Security Improvements (for new data)

- **Authentication**: GCM provides integrity verification; tampered ciphertext is detected
- **No padding oracle**: GCM doesn't use padding, eliminating the vulnerability for newly encrypted data
- **Modern standard**: GCM is the recommended mode for AES in modern applications

Note: Legacy CBC-encrypted data retains the original vulnerability until re-encrypted. See "Residual Security Risk" section above.

### Performance Considerations

- **New data**: Single GCM operation (fast)
- **Legacy data**: GCM attempt + CBC fallback (two operations)
- **Impact**: Negligible for typical Openfire workloads; encryption operations are infrequent

## References

- **OF-3077**: Potential padding oracle CBC-mode encryption
- **OF-1533**: Use a random IV for each new encrypted property (2018)
- **OF-3074**: Prevent hardcoded IV when encrypting parameters
- **NIST SP 800-38D**: Recommendation for Block Cipher Modes of Operation: Galois/Counter Mode (GCM) - https://csrc.nist.gov/pubs/sp/800/38/d/final