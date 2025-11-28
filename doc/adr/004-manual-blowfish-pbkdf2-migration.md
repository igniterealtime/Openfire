# ADR-004: Manual Migration Tool for Blowfish PBKDF2 Upgrade

## Status
Accepted

## Context

As part of OF-3075, we're replacing Blowfish's weak SHA1 key derivation with PBKDF2-HMAC-SHA512. This improves security but requires re-encrypting all existing Blowfish-encrypted properties with keys derived using the new KDF.

We need to decide: should migration happen automatically on server startup, or should it require manual admin action via a console tool?

**Key architectural constraint**: Blowfish-encrypted properties are stored in a **shared database** (ofProperty table), but the KDF configuration is stored in **local files** per node (security.xml). This creates a fundamental coordination challenge for automatic migration.

## Decision

We will implement a **manual migration tool** in the admin console rather than automatic migration on startup.

Admins must explicitly trigger migration via:
- Link in warning banner on Server Information page (if migration needed)
- Requires confirmation checkboxes (database backup, security.xml backup, openfire.xml backup)
- Displays migration progress and success/failure status

## Options Considered

### Option 1: Manual Migration Tool (CHOSEN)

**Implementation**:
- Admin console page shows migration status
- Displays current KDF type and count of properties to migrate
- Requires checkbox confirmation of backups before proceeding
- Provides clear progress indication and error messages
- Admin must stop all cluster nodes before migration

**Consequences**:

✅ **Admin control**: Admin chooses when to migrate (during planned maintenance window)

✅ **Backup enforcement**: Checkboxes force admin to backup database and security.xml before proceeding

✅ **Cluster coordination**: Clear requirement to stop all nodes prevents race conditions and configuration drift

✅ **Visibility**: Progress bar, success/failure messages, clear audit trail

✅ **Safety**: Can test in staging environment first before production

✅ **Rollback capability**: Admin can restore from backup if migration fails

✅ **No surprise downtime**: Migration happens during planned maintenance, not unexpectedly at startup

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
2. **All-or-nothing**: No per-property KDF tracking (would require database schema change to add `kdf_version` column)
3. **Cluster safety**: Requires all nodes stopped to prevent race conditions
4. **Data safety**: Irreversible migration requires backup opportunity

**Comparison with XMLProperties auto-migration** (which does work):

| Aspect | XMLProperties | Blowfish Properties |
|--------|--------------|---------------------|
| **Storage** | Local file (openfire.xml) | Shared database (ofProperty) |
| **Configuration** | Local file (same file) | Local file (security.xml) |
| **Migration scope** | Per-property (gradual) | All-or-nothing (global KDF) |
| **Format mixing** | Can mix old/new formats | Cannot mix KDFs |
| **Rollback** | Easy (restore file) | Requires database backup |
| **Cluster coordination** | None needed (per-node file) | **IMPOSSIBLE** (shared DB + local config) |

XMLProperties auto-migration works because storage and configuration are both local. Blowfish has storage (database) that's shared while configuration (security.xml) is local - creating an unsolvable coordination problem for automatic migration.

## Consequences

Choosing manual migration means:

✅ Admins control timing and can prepare backups during planned maintenance

✅ Cluster coordination is explicit (stop all nodes requirement)

✅ Clear visibility into migration progress and any failures

❌ Existing installations remain on SHA1 until manually migrated

❌ Requires admin awareness of the migration tool after upgrade

See Option 1 for full consequence analysis.

## Implementation Notes

**Manual migration tool provides**:
- Pre-flight checks (display property count, current KDF)
- Backup confirmation checkboxes (database + security.xml)
- Cluster shutdown requirement warning
- Progress indication during migration
- Success/failure messaging
- Detailed logging for audit trail

**Clustering detection implementation**:

The migration tool uses multiple `ClusterManager` methods to detect unsafe cluster scenarios:

| Method | Purpose |
|--------|---------|
| `isClusteringAvailable()` | Checks if Hazelcast plugin is installed and loadable |
| `isClusteringEnabled()` | Checks if clustering is configured in XML properties |
| `isClusteringStarted()` | Checks if JVM is actually participating in a cluster |
| `getNodesInfo().size()` | Counts active cluster nodes |

**Migration blocked when**:
1. `clusteringEnabled && !clusteringStarted` — clustering configured but not yet running (race condition risk: other nodes might be starting simultaneously)
2. `clusteringStarted && nodeCount > 1` — multiple nodes active in cluster

**Migration allowed when**:
1. `!clusteringAvailable` — Hazelcast plugin not installed, clustering impossible
2. `!clusteringEnabled` — clustering not configured
3. `clusteringStarted && nodeCount == 1` — single node in active cluster (safe)

**Admin documentation must include**:
- Migration procedure for single-node deployments
- Migration procedure for clustered deployments (stop all nodes, run from one node, update all security.xml and openfire.xml files, restart)
- Backup and rollback procedures
- Troubleshooting common migration failures

**Future consideration**: If we later add `kdf_version` column to ofProperty table, gradual automatic migration becomes possible (similar to XMLProperties). However, this requires database schema change and doesn't address the security.xml synchronisation problem in clusters.

## References

- [OF-3075](https://igniterealtime.atlassian.net/browse/OF-3075): Weak SHA1 hash used as key for Blowfish
- ADR-001: Separate Obfuscation from Encryption (architectural split)
- ADR-005: PBKDF2 Parameters for Blowfish (cryptographic parameters used by this migration)
