# ADR-004: Manual Migration Tool for Blowfish PBKDF2 Upgrade

## Status
Accepted

## Context

As part of OF-3075, we're replacing Blowfish's weak SHA1 key derivation with PBKDF2-HMAC-SHA512. This improves security but requires re-encrypting all existing Blowfish-encrypted properties with keys derived using the new KDF.

We need to decide: should migration happen automatically on server startup, or should it require manual admin action via a console tool?

**Key architectural constraint**: Blowfish-encrypted properties are stored in a **shared database** (ofProperty table), but the KDF configuration is stored in **local files** per node (security.xml). This creates a fundamental coordination challenge for automatic migration.

**Data integrity concern**: If configuration state becomes inconsistent with encrypted data (e.g., security.xml is lost or corrupted), the system must detect this rather than silently corrupting data by using the wrong KDF. This requires encrypted values to be self-describing - carrying their own KDF version indicator.

## Decision

We will implement a **manual migration tool** in the admin console rather than automatic migration on startup. Migrated values will use a **version prefix** (`$v2$`) to enable per-property KDF identification and prevent silent data corruption.

Admins must explicitly trigger migration via:
- Link in warning banner on Server Information page (if migration needed)
- Requires confirmation checkboxes (database backup, security.xml backup, openfire.xml backup)
- Displays migration progress and success/failure status

Migrated encrypted values are prefixed with `$v2$` to indicate PBKDF2 key derivation, making each value self-describing regardless of configuration state.

## Options Considered

### Option 1: Manual Migration Tool (CHOSEN)

**Implementation**:
- Admin console page shows migration status
- Displays current KDF type and count of properties to migrate
- Requires checkbox confirmation of backups before proceeding
- Provides clear progress indication and error messages
- Admin must stop all cluster nodes before migration
- Migrated values prefixed with `$v2$` to indicate PBKDF2 key derivation

**Consequences**:

✅ **Admin control**: Admin chooses when to migrate (during planned maintenance window)

✅ **Backup enforcement**: Checkboxes force admin to backup database and security.xml before proceeding

✅ **Cluster coordination**: Clear requirement to stop all nodes prevents race conditions and configuration drift

✅ **Visibility**: Progress bar, success/failure messages, clear audit trail

✅ **Safety**: Can test in staging environment first before production

✅ **Rollback capability**: Admin can restore from backup if migration fails

✅ **No surprise downtime**: Migration happens during planned maintenance, not unexpectedly at startup

✅ **Self-describing values**: Version prefix prevents silent corruption if configuration is lost or inconsistent

❌ **Manual intervention required**: Admin must remember to run migration tool after upgrade

❌ **More implementation work**: Requires building admin console page, servlet, i18n strings

❌ **Existing installations remain on SHA1**: Until admin explicitly migrates (but backward compatibility maintained)

### Option 2: Automatic Migration on Startup

**Implementation**:
- Server detects `<blowfish.kdf>sha1</blowfish.kdf>` in security.xml at startup
- Automatically migrates all properties in database to PBKDF2
- Updates local security.xml to `<blowfish.kdf>pbkdf2</blowfish.kdf>`

**Consequences**:

✅ **No admin action required**: Migration happens transparently on first startup after upgrade

✅ **Simpler implementation**: No admin console page needed

❌ **Cluster coordination impossible**: Cannot update security.xml on other nodes (different servers)

**Critical failure scenario**:
```
Node A starts up:
  1. Reads security.xml (KDF=sha1)
  2. Migrates ALL database properties to PBKDF2
  3. Updates local security.xml (KDF=pbkdf2)

Node B starts up 30 seconds later:
  1. Reads security.xml (still KDF=sha1) ← OLD FILE!
  2. Tries to decrypt database properties
  3. FAILS - properties were re-encrypted with PBKDF2 key
  4. ❌ Node B cannot start
```

❌ **Shared database + local config = architectural mismatch**: Database is shared across cluster, but security.xml is local per node. Cannot coordinate automatic updates.

❌ **No backup opportunity**: Admin has no chance to backup database before irreversible migration

❌ **All-or-nothing migration**: Unlike XMLProperties (which can mix old/new formats per-property), Blowfish KDF is global. Must migrate ALL properties at once or none.

❌ **Rolling upgrade broken**: New nodes auto-migrate database, breaking old nodes that don't understand PBKDF2

❌ **Race conditions in cluster**: Multiple nodes starting simultaneously might both attempt migration

❌ **Unexpected startup delay**: Re-encrypting 100s/1000s of properties with PBKDF2 (intentionally slow) could delay startup by minutes

❌ **No visibility**: Admin doesn't know migration is happening or why startup is slow

❌ **Irreversible without backup**: PBKDF2-encrypted properties cannot be decrypted with SHA1-derived key

### Option 3: Automatic Migration with System Property

**Note**: This option was considered but rejected early due to fundamental architectural issues that cannot be resolved.

**Implementation**:
```bash
# Admin must explicitly enable on first startup
./openfire.sh -Dblowfish.auto.migrate=true
```

**Consequences**:

❌ **Still cannot update security.xml on other nodes**: Even with system property, cannot reach other servers' filesystems

❌ **Other nodes might start before migration completes**: Risk of decryption failures

❌ **Configuration drift**: Some nodes might have `-Dblowfish.auto.migrate=false` and fail to start

❌ **No backup enforcement**: System property doesn't force admin to backup first

❌ **Complex failure modes**: Database locked during migration, nodes timing out, etc.

This option shares all the fundamental problems of Option 2 (shared database + local config) while adding operational complexity.

### Option 4: Move KDF Configuration to Database

**Implementation**:
- Move `encrypt.blowfish.kdf` and `encrypt.blowfish.salt` from security.xml to database (ofProperty table)
- All nodes read KDF from shared database instead of local files
- This would solve the cluster coordination problem (all nodes see same KDF value)

**Consequences**:

✅ **Cluster coordination solved**: All nodes read KDF from shared database, see same value

✅ **Enables automatic migration**: Could auto-migrate on startup since KDF update is atomic

✅ **Better logical coherence**: KDF metadata travels with encrypted data

❌ **FATAL: Bootstrap circular dependency**

**The Chicken-and-Egg Problem**:

```
Startup sequence requires:
  1. Read security.xml → get master encryption key
  2. Read openfire.xml → get database connection config
  3. Decrypt database password (might be encrypted!)
  4. Connect to database
  5. Read encrypted properties from ofProperty table

But if KDF is in the database:
  1. Read security.xml → get master key only (no KDF)
  2. Read openfire.xml → database password is encrypted
  3. Need to decrypt password... but need KDF to derive decryption key
  4. Need database connection to read KDF
  5. ❌ Can't connect without decrypting password
  6. ❌ Can't decrypt password without KDF
  7. ❌ Can't read KDF without database connection

  → DEADLOCK
```

**Concrete example** (`openfire.xml`):
```xml
<database>
  <serverURL>jdbc:postgresql://db.example.com/openfire</serverURL>
  <username>openfire_user</username>
  <password encrypted="true">Rw8fEzVz...</password> ← ENCRYPTED!
</database>
```

To decrypt the database password, you need:
- Master encryption key ✅ (from security.xml)
- KDF version ❌ (would be in database)
- Salt ❌ (would be in database)

But to read KDF from database, you need a database connection, which requires decrypting the password!

❌ **Breaks bootstrap architecture**: `security.xml` exists specifically to contain everything needed to initialize encryption and connect to the database

❌ **Makes encrypted database passwords impossible**: Cannot support encrypted DB passwords if KDF is in database

❌ **Not backward compatible**: Old Openfire versions expect KDF in security.xml

**Architectural Principle Violated**:

The bootstrap configuration (`security.xml`) **cannot depend on the database** because the database connection itself might require decryption. This is why `security.xml` must contain:
- Master encryption key (needed to decrypt anything)
- KDF version (needed to derive keys before database access)
- Salt (needed to derive keys before database access)
- Encryption algorithm choice (needed before database access)

**Conclusion**: This option is architecturally impossible due to circular dependency in bootstrap sequence.

## Rationale

The architectural mismatch between **shared database storage** and **local file configuration** makes automatic migration fundamentally unsafe for production environments:

1. **Coordination impossible**: Cannot update security.xml on multiple servers automatically
2. **Cluster safety**: Requires all nodes stopped to prevent race conditions
3. **Data safety**: Irreversible migration requires backup opportunity

**Comparison with XMLProperties auto-migration** (which does work):

| Aspect | XMLProperties | Blowfish Properties |
|--------|--------------|---------------------|
| **Storage** | Local file (openfire.xml) | Shared database (ofProperty) |
| **Configuration** | Local file (same file) | Local file (security.xml) |
| **Migration scope** | Per-property (gradual) | Per-property (with version prefix) |
| **Format mixing** | Can mix old/new formats | Can mix KDFs (via `$v2$` prefix) |
| **Rollback** | Easy (restore file) | Requires database backup |
| **Cluster coordination** | None needed (per-node file) | **IMPOSSIBLE** (shared DB + local config) |

XMLProperties auto-migration works because storage and configuration are both local. Blowfish has storage (database) that's shared while configuration (security.xml) is local - creating an unsolvable coordination problem for automatic migration.

## Implementation Notes

**Manual migration tool provides**:
- Pre-flight checks (display property count, current KDF)
- Backup confirmation checkboxes (database + security.xml)
- Cluster shutdown requirement warning
- Progress indication during migration
- Success/failure messaging
- Detailed logging for audit trail

**Admin documentation must include**:
- Migration procedure for single-node deployments
- Migration procedure for clustered deployments (stop all nodes, run from one node, update all security.xml files, restart)
- Backup and rollback procedures
- Troubleshooting common migration failures

**Critical backup requirement**: After migration to PBKDF2, the salt stored in security.xml becomes essential for decryption. Unlike SHA1 (which derived keys solely from the master password), PBKDF2 requires both the master password AND the salt. If security.xml is lost or the `<blowfish>` element is deleted, PBKDF2-encrypted data becomes unrecoverable. Administrators must:
- Include security.xml in all backup procedures
- Never delete or modify the `<blowfish><salt>` element after migration
- Ensure security.xml is synchronised across all cluster nodes

## Version Prefix Format (`$v2$`)

To address the data integrity concern identified in Context, migrated encrypted values include a version prefix that identifies the KDF used.

### Format Design

```
Legacy (v1/SHA1):   a7f3e9b2c1d4...      (no prefix)
New (v2/PBKDF2):    $v2$a7f3e9b2c1d4...  (prefix added)
```

The `$v2$` format is inspired by the PHC (Password Hashing Competition) String Format, a de facto standard used by modern password hashing algorithms:
- bcrypt: `$2b$12$...`
- argon2: `$argon2id$v=19$...`
- scrypt: `$scrypt$...`

Design choices for `$v2$`:
- **`$` delimiter**: Not a valid hex character (0-9, a-f), so no ambiguity with legacy ciphertext
- **Simple version number**: `v2` is human-readable and easy to parse
- **Short (4 chars)**: Minimises storage overhead
- **Extensible**: Future versions can use `$v3$`, `$v4$`, etc.
- **Industry familiarity**: Developers familiar with password hashes will recognise the pattern

Alternative formats considered:
- `{v2}` - Less standard, braces might conflict with other formats
- `bf2:` - Blowfish-specific, less extensible if algorithm changes
- Single byte marker - Not human-readable, harder to debug
- Full PHC format (`$blowfish$v=2$...`) - Overkill for this use case

### Problem Solved: Silent Data Corruption

Without version prefixes, configuration loss can cause silent data corruption:

**Scenario without version prefix**:
1. Server operating with PBKDF2 (kdf=pbkdf2, salt in security.xml)
2. Admin deletes `<blowfish>` element from security.xml (accidentally or during troubleshooting)
3. System defaults to SHA1 (no config = SHA1)
4. Migration tool shows "needs migration"
5. Migration decrypts PBKDF2 data with SHA1 key = **garbage**
6. Garbage re-encrypted and saved = **silent corruption**

**Same scenario with `$v2$` prefix**:
1. Server operating with PBKDF2 (kdf=pbkdf2, salt in security.xml)
2. Admin deletes `<blowfish>` element from security.xml (accidentally or during troubleshooting)
3. System detects `$v2$` prefix and auto-configures PBKDF2
4. Migration tool shows "already migrated"
5. No migration attempted
6. Data remains unchanged (but may be unreadable if salt was also lost - see below)

The version prefix makes each encrypted value self-describing, so the system can detect KDF mismatches rather than silently corrupting data.

### Remaining Risk: Salt Loss After Migration

While the `$v2$` prefix prevents silent corruption, there is a remaining risk when configuration is lost **after** migration to PBKDF2.

**Scenario**:
1. Server successfully migrated to PBKDF2 (properties have `$v2$` prefix)
2. Admin deletes or loses `<blowfish>` element from security.xml (including the salt)
3. Server restarts, detects `$v2$` prefix, auto-configures PBKDF2
4. Server generates a **new random salt** (because the original was lost)
5. Decryption attempts fail - wrong salt produces wrong key
6. Data is preserved but **unreadable**

**Key insight**: The salt stored in security.xml is critical for PBKDF2 decryption. Unlike the KDF version (which can be auto-detected from the `$v2$` prefix), the salt cannot be recovered from the encrypted data itself.

**Behaviour comparison**:

| Scenario | Without `$v2$` prefix | With `$v2$` prefix |
|----------|----------------------|-------------------|
| Config lost before migration | Silent corruption (SHA1 key used on PBKDF2 data) | Auto-detects PBKDF2, generates new salt, data unreadable |
| Config lost after migration | Silent corruption | Data preserved but unreadable |

The `$v2$` prefix is a **fail-safe** mechanism: it prevents silent corruption by ensuring the system knows PBKDF2 was used, but it cannot recover the original salt. This is the correct trade-off - explicit failure is better than silent data corruption.

**Mitigation**: Backup security.xml as part of regular backup procedures. The salt is as important as the encryption key for PBKDF2-encrypted data.

### Downgrade Behaviour

Once migrated to PBKDF2, downgrading to older Openfire versions is not supported - old versions lack PBKDF2 key derivation code. The version prefix improves this scenario by making the incompatibility obvious:

| Scenario | Without prefix | With `$v2$` prefix |
|----------|---------------|-------------------|
| Old Openfire decrypts | Hex parsing succeeds, uses SHA1 key (wrong) | Hex parsing fails (`$` invalid) |
| Result | **Silent garbage** - appears to work | **Explicit failure** - returns null |

The prefix makes incompatibility fail fast and obviously rather than silently corrupting data.

### Benefits and Trade-offs

**Benefits**:
- Self-describing values - each carries its own KDF version
- Prevents silent corruption - config/data mismatch detectable
- Cluster-safe - works even with inconsistent node configs
- No database schema change required
- Works for both database and XML properties

**Trade-offs**:
- Cannot downgrade after migration (acceptable - security improvement is one-way)

## Related

- OF-3075: Weak SHA1 hash used as key for Blowfish
- ADR-001: Separate Obfuscation from Encryption (architectural split)
