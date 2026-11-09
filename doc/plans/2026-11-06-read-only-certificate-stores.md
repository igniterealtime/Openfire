---
generated_by: Claude Opus 5.5
generation_date: 2026-11-09
model_version: claude-opus-5-5
purpose: implementation_plan
status: draft
human_reviewer: matthewvivian
implementation_tracking: in_progress
tags: [certificates, keystore, truststore, admin-console, security]
jira_ticket: OF-1878
branch: OF-1878
session_status:
  session_1_move_issuer_update: completed (2026-11-06)
  session_2_setting_and_refusal: completed (2026-11-09)
  session_3_issuer_update_and_setup: completed (2026-11-09)
  session_4_admin_console: not-started
  session_5_documentation_and_coverage: not-started
---

# Read-only certificate stores: stop Openfire modifying the stores it uses

## Summary

Add one setting, `cert.stores.readonly`, that stops Openfire from modifying any of the certificate stores it uses. When it is on, every operation that would change an identity store or trust store (in the admin console, in the setup wizard, or from a plugin) is refused before anything is changed, and the admin console shows the stores as read-only instead of offering actions that would fail. Openfire still reloads a store when its file changes on disk, so updates made by other software are picked up.

## Background

Openfire ships with its own certificate stores, but can be configured to use others (each connection type can use its own files, and OF-1854 allows trust and identity stores to be of different types). When a store file is also used by other software on the system, changes Openfire makes to it affect that software too.

OF-1878 asks for "a feature that prevents Openfire from making modifications to the certificate stores that it uses", for stores that "are also used by other software components on the system".

Read-only is a safety guard, not an access control. An administrator can turn it off, as they can point Openfire at a different file; it prevents unintended modification, and does not protect a store from someone with admin console access.

## Design Decisions

### 1. One setting for all stores: `cert.stores.readonly`
- A registered `SystemProperty<Boolean>`, default false and dynamic, defined in `CertificateStoreManager`, with a `system_property.cert.stores.readonly` description. The name follows the existing global certificate settings (`cert.storewatcher.enabled`, `cert.validity-days`).
- When it is on, Openfire modifies none of the identity stores and trust stores it uses, for any connection type.
- Rationale: the ticket asks for Openfire not to modify the stores it uses, and does not ask for finer control; one setting is the simplest to configure, explain and test.
- Alternatives considered: a setting per store, resolved per file across the connection types that share it (much more complex, in the code and for administrators, and not asked for); one setting for identity stores and one for trust stores (a clean separation that matches the store classes and properties, and can be added later with this setting keeping its meaning of "all stores").

### 2. Stores read the setting when they are written
- Each store-changing method reads the property at the moment it is called. A change takes effect at once, with no state to recompute, and stores created outside `CertificateStoreManager` (for example by a plugin) are covered as well.
- The setting is not part of `CertificateStoreConfiguration`, and `CertificateStoreManager.replaceIdentityStore` and `replaceTrustStore` are unchanged.

### 3. Refuse in the store classes, before anything changes
- Every method that changes a store checks the setting first and throws `CertificateStoreReadOnlyException`, a new subclass of `CertificateStoreConfigException`, so existing `catch` blocks in core and in plugins handle it unchanged.
- The check comes before the in-memory `KeyStore` is changed, not only in `persist()`: otherwise Openfire would serve a modified store from memory while the file is unchanged. `persist()` also checks, as a backstop for changes made directly through `getStore()`, and reloads the store from disk when it refuses.
- The constructor does not create a missing store file when the setting is on.
- Rationale: the admin console is not the only writer. Plugins call these methods, and the setup wizard calls `ensureDomainCertificate`.

### 4. The admin console shows the setting; the stores enforce it
- The Certificate Stores page gets a section of its own after the introductory text and above the store sections, boxed like the store sections, with one checkbox and its own Save button. Saving sets the property to what the checkbox shows. It is separate from the store forms, so it behaves the same in both views of the page and its result is not mixed with location and password errors.
- Pages that change a store's contents show a notice and hide the actions that would fail. A request that reaches a write action anyway (an old link, a direct URL) gets the refusal from the store class, shown as an error.
- Changing a store's location or password stays allowed: that changes Openfire's configuration, not the store.

### 5. Signing-request issuer update moves into `IdentityStore`
- `security-keystore-signing-request.jsp` changes the store through `getStore()` (re-signing self-signed certificates with new issuer details) and then calls `persist()`. That bypasses any check in the store's own methods. The logic moves into an `IdentityStore` method that checks the setting first, which also makes it testable.
- `getStore()` stays public (TLS setup needs the `KeyStore`). A plugin that changes the `KeyStore` directly and never calls `persist()` is out of reach; the `persist()` check covers the file.

### 6. No self-signed certificate during setup when the setting is on
- `XMPPServer.finalSetupSteps` calls `IdentityStore.ensureDomainCertificate`, which generates a self-signed certificate when the store has none for the domain. With the setting on, it logs a warning naming the store and the domain and carries on; it does not fail setup.
- This only happens in the setup wizard: `ensureDomainCertificate` is not called on a normal start.

---

## Impact map

### Write paths in core

Measured by searching the `xmppserver` sources for callers of each store-changing method and for direct `KeyStore` changes (`setKeyEntry`, `setCertificateEntry`, `deleteEntry`). No Java class outside `org.jivesoftware.openfire.keystore` changes a store, apart from `XMPPServer.finalSetupSteps`.

| Method | Called from | With the setting on |
|---|---|---|
| `CertificateStore` constructor with `createIfAbsent` | `CertificateStoreManager.replaceIdentityStore`/`replaceTrustStore` (the admin console passes false) | Refuse to create the file |
| `CertificateStore.persist` | the methods below; `security-keystore.jsp` | Refuse and reload from disk |
| `CertificateStore.delete` | `security-keystore.jsp`, `security-truststore.jsp` | Refuse |
| `IdentityStore.installCertificate` (both overloads) | `import-keystore-certificate.jsp` | Refuse |
| `IdentityStore.installCSRReply` | `security-keystore.jsp` | Refuse |
| `IdentityStore.replaceCertificate` | no core caller (public, so plugins) | Refuse |
| `IdentityStore.addSelfSignedDomainCertificate` | `security-keystore.jsp`, `ensureDomainCertificate` | Refuse |
| `IdentityStore.ensureDomainCertificate` | `XMPPServer.finalSetupSteps` (setup wizard) | Warn and skip |
| `TrustStore.installCertificate` | `import-truststore-certificate.jsp` | Refuse |
| `IdentityStore.updateIssuerOfSelfSignedCertificates` (moved from a direct `KeyStore` change in the page, decision 5) | `security-keystore-signing-request.jsp` | Refuse |

Unaffected, because they only read the store or write elsewhere: `CertificateStore.reload` and `CertificateStoreWatcher` (reloading on file change keeps working), `CertificateStoreManager.backup` and `security-certificate-store-backup.jsp` (copies the file to the archive directory), TLS setup in `EncryptionArtifactFactory`, `CertificateExpiryChecker`, SASL EXTERNAL, and the domain-certificate warnings in `index.jsp`, `AdminConsolePlugin` and `HttpBindManager`.

Plugins in other repositories (for example ones that install certificates automatically) were not inspected. They call the methods above, so they get `CertificateStoreReadOnlyException` and report it however they report a failed install today.

### Admin console screens

| Screen | Today | With the setting on |
|---|---|---|
| `security-certificate-store-management.jsp` | Store sections with file, password and "Manage Store Contents"; one view for the default layout, one per connection type | A new section above the store sections, boxed like the store sections, with the read-only checkbox and its own Save button; the store sections are unchanged |
| `security-keystore.jsp` (identity store contents) | Generate self-signed, import, delete, import a signed-certificate reply, link to the signing-request page; a "no certificate for this domain" message offering generate or import | Notice that the stores are read-only; those actions hidden; the missing-certificate message says the store must be updated outside Openfire. Listing, details and creating a signing request to copy stay |
| `security-truststore.jsp` (trust store contents) | Import link, delete per certificate | Notice; import and delete hidden |
| `import-keystore-certificate.jsp`, `import-truststore-certificate.jsp` | Import form | Notice instead of the form; a POST is refused by the store |
| `security-keystore-signing-request.jsp` | Issuer details form that re-signs self-signed certificates | Notice instead of the form; a POST is refused by the store |
| `security-certificate-details.jsp`, `security-certificate-store-backup.jsp` | Read-only view; backs up store files | Unchanged |
| Server → System Properties | Lists registered properties | `cert.stores.readonly`, listed whether set or not, with its description |
| Setup wizard | Generates a self-signed certificate at the end | Skipped, with a warning in the log |

### Other permutations

- **Setting changed at runtime:** takes effect at the next write; no restart.
- **Cluster:** the property is cluster-wide and each node applies it to its own store files.
- **Store file missing:** unchanged (the store is not loaded, with a warning); the setting only stops Openfire creating it.
- **File not writable at the operating-system level, with the setting off:** unchanged (writes fail with the existing error). With the setting on, Openfire no longer attempts them.

---

## Implementation Sessions

Tests are written before the code in each session. Use `-pl i18n,xmppserver` once `openfire_i18n.properties` changes, or messages render as `???key???`.

### Session 1: Move the signing-request issuer update into `IdentityStore`

**Goal:** The only store change made outside the store classes moves into `IdentityStore`, with no change in behaviour, so that session 3 can guard it like every other write (decision 5).

**Files:**
- `xmppserver/src/main/java/org/jivesoftware/openfire/keystore/IdentityStore.java`
- `xmppserver/src/main/webapp/security-keystore-signing-request.jsp`
- `xmppserver/src/test/java/org/jivesoftware/openfire/keystore/IdentityStoreIssuerUpdateTest.java` (new)

**Tasks:**
1. Write tests for a new `IdentityStore` method that takes the issuer details and re-signs the self-signed certificates with them, as the page does today: self-signed entries get the new issuer and keep their keys, other entries are untouched, the change is persisted, and on failure the store is reloaded from disk.
2. Move the logic out of `security-keystore-signing-request.jsp` into that method; the page keeps its form handling, validation and redirect, and calls the method.
3. On a running server, submit the signing-request form for a store with a self-signed certificate and compare the result with `main`.

**Exit:** Tests pass; the page behaves as on `main` on a running server; the commit is a refactoring only.

---

### Session 2: The setting and the refusal in the store classes

**Goal:** With `cert.stores.readonly` on, every change in `CertificateStore`, `IdentityStore` and `TrustStore` is refused before the in-memory store is touched.

**Files:**
- `xmppserver/src/main/java/org/jivesoftware/openfire/keystore/CertificateStoreReadOnlyException.java` (new)
- `xmppserver/src/main/java/org/jivesoftware/openfire/keystore/CertificateStore.java`
- `xmppserver/src/main/java/org/jivesoftware/openfire/keystore/IdentityStore.java`
- `xmppserver/src/main/java/org/jivesoftware/openfire/keystore/TrustStore.java`
- `xmppserver/src/main/java/org/jivesoftware/openfire/keystore/CertificateStoreManager.java`
- `xmppserver/src/test/java/org/jivesoftware/openfire/keystore/CertificateStoreReadOnlyTest.java` (new)
- `i18n/src/main/resources/openfire_i18n.properties`

**Tasks:**
1. Write tests: for each method in the write-paths table (apart from the issuer update and `ensureDomainCertificate`, which session 3 covers), with the setting on the method throws `CertificateStoreReadOnlyException`, the file's bytes are unchanged, and the in-memory store still matches the file; with the setting off, each method still works. Include the constructor with `createIfAbsent` on a missing file, `persist()` after a change made through `getStore()` (refused and reloaded), the default (off), and a change of the setting taking effect without recreating the store.
2. Add the exception, the `SystemProperty<Boolean>` in `CertificateStoreManager` (decision 1), and a check in `CertificateStore` that every write method calls first; add the check and reload in `persist`.
3. Add the `system_property.cert.stores.readonly` description, using the OF-1878 wording.

**Exit:** New tests pass; `./mvnw test -pl i18n,xmppserver` passes.

---

### Session 3: Issuer update and setup wizard

**Goal:** The issuer update moved in session 1 and the setup wizard's self-signed certificate respect the setting.

**Files:**
- `xmppserver/src/main/java/org/jivesoftware/openfire/keystore/IdentityStore.java`
- tests in `xmppserver/src/test/java/org/jivesoftware/openfire/keystore/`

**Tasks:**
1. Write a test that the issuer-update method from session 1 refuses with the setting on, before changing anything, file and in-memory store unchanged; add the check.
2. Write a test that `ensureDomainCertificate`, with the setting on and no domain certificate in the store, logs a warning naming the store and domain, and leaves the store unchanged; implement it.

**Exit:** `./mvnw test -pl i18n,xmppserver` passes.

---

### Session 4: Admin console

**Goal:** An administrator can see and change the setting on the Certificate Stores page, and the pages that change a store's contents show the stores as read-only and offer no actions that would fail.

**Files:**
- `xmppserver/src/main/webapp/security-certificate-store-management.jsp`
- `xmppserver/src/main/webapp/security-keystore.jsp`, `security-truststore.jsp`, `import-keystore-certificate.jsp`, `import-truststore-certificate.jsp`, `security-keystore-signing-request.jsp`
- a shared notice tag under `xmppserver/src/main/webapp/WEB-INF/classes/META-INF/tags/admin/security/`, registered in `admin.tld`
- `i18n/src/main/resources/openfire_i18n.properties`

**Tasks:**
1. On the Certificate Stores page, add a section after the introductory text and above the store sections, boxed like the store sections, with the read-only checkbox (showing the property's value), a short explanation and its own Save button with CSRF protection, as the store form has. Saving sets the property to what the checkbox shows, logs an admin event when the value changes, and shows the page's success or error message.
2. Add a shared notice, and on each content page in the screens table show it and hide the actions listed there; replace the missing-domain-certificate message on `security-keystore.jsp` with the setting on.
3. On a running server: the checkbox in both views of the Certificate Stores page, turning the setting on and off, without a restart; for each content page, with the setting on, the actions are hidden, a direct POST is refused with a readable error and the store file's checksum is unchanged; with it off, each action still works.

**Exit:** Every row of the screens table checked on a running server, with the setting on and off; the test suite passes.

---

### Session 5: Documentation and coverage

**Goal:** The feature is documented and its coverage measured.

**Files:**
- `documentation/ssl-guide.html`

**Tasks:**
1. Add a section on read-only stores to `ssl-guide.html`: what the setting does, where to set it (the Certificate Stores page or the `cert.stores.readonly` System Property), that it applies to every store, that it is a safety guard and not an access control, that changes made by other software are reloaded while the certificate store watcher is on, and that setup does not generate a self-signed certificate with it on.
2. Measure coverage of the changed and added code with JaCoCo; add tests where it is short.

**Exit:** Documentation reviewed; coverage recorded.

---

## Critical Files

| Repository | File | Purpose |
|------------|------|---------|
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/keystore/CertificateStore.java` | Base store: `persist`, `delete`, constructor creating a file; the read-only check |
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/keystore/IdentityStore.java` | Certificate install, CSR reply, self-signed generation, issuer update |
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/keystore/TrustStore.java` | Trusted certificate install |
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/keystore/CertificateStoreManager.java` | Defines `cert.stores.readonly` |
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/XMPPServer.java` | `finalSetupSteps` calls `ensureDomainCertificate` |
| Openfire | `xmppserver/src/main/webapp/security-certificate-store-management.jsp` | Certificate Stores page, with the read-only section |
| Openfire | `xmppserver/src/main/webapp/security-keystore.jsp`, `security-truststore.jsp`, the two import pages, `security-keystore-signing-request.jsp` | Pages that change store contents |

---

## Verification Checklist

### Core (Sessions 1-3)
- [ ] Every row of the write-paths table refuses with the setting on, file and in-memory store unchanged
- [ ] Changes to the setting apply without a restart
- [ ] Setup wizard skips self-signed generation with the setting on, with a warning
- [ ] Full `xmppserver` test suite passes

### Admin console (Session 4)
- [ ] The read-only section on the Certificate Stores page, in both views
- [ ] Every row of the screens table checked with the setting on and off
- [ ] Direct POSTs to write actions refused with a readable error

### Delivery (Session 5)
- [ ] `ssl-guide.html` updated
- [ ] Coverage of changed and added code measured

---

## Open Questions

None.
