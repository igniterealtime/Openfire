---
generated_by: Claude Opus 5.5
generation_date: 2026-11-10
model_version: claude-opus-5-5
purpose: implementation_plan
status: draft
human_reviewer: matthewvivian
implementation_tracking: completed
tags: [authentication, sasl, lockout, brute-force, security]
jira_ticket: OF-2279
branch: OF-2279
session_status:
  session_1_limit_manager: completed (2026-11-10)
  session_2_sasl_integration: completed (2026-11-10)
  session_3_jmx_startup_and_docs: completed (2026-11-10)
  session_4_admin_console_page: completed (2026-11-10)
  session_5_running_server_and_coverage: completed (2026-11-10)
  session_6_block_duration: completed (2026-11-11)
  session_7_final_review_fixes: completed (2026-11-11)
---

# Login limits for user accounts

## Summary

Add brute-force protection for XMPP user logins, equivalent to the protection the admin console has through `LoginLimitManager`. Openfire counts failed password attempts in three ways and refuses further attempts when a limit is reached:

| Tier | Counts failures for | On reaching the limit | Default limit |
|---|---|---|---|
| 1 | one username from one address | that address may not attempt that username for the block duration | 10 in 15 minutes, blocked for 15 minutes |
| 2 | one address, any username | that address may not attempt any username for the block duration | 40 in 15 minutes, blocked for 15 minutes |
| 3 | one username, from any address | the account is locked through `LockOutManager` for a set duration | 50 in 1 hour, locked for 30 minutes |

The feature is off by default. While a block or lock applies, every attempt is refused before the password is evaluated: with `account-disabled` for a locked account or a username blocked by tier 3, and with `not-authorized` for a tier 1 or tier 2 block. An admin console page sets the limits and lists the active blocks.

The decision and the options considered are recorded in `doc/adr/008-login-limits-for-user-accounts.md`.

## Background

OF-2279: "Presently, Openfire has a LoginLimitManager to lock out admin account login attempts. It would be nice to have the same for mortal users."

`org.jivesoftware.admin.LoginLimitManager` limits admin console logins per address, per username and (since OF-3262) per (username, address) pair. OF-3262 added the pair key because per-address counting let one client lock out everyone behind a shared address. Nothing limits XMPP logins today. `LockOutManager` lets an administrator lock an account (with optional start and end times), and the SASL code refuses logins for a locked account, but nothing locks an account automatically.

## Design Decisions

### 1. A new manager for user logins: `UserLoginLimitManager`
- A new singleton in `org.jivesoftware.openfire.auth`, separate from the admin console's `LoginLimitManager`.
- Rationale: the admin manager keeps its counts per cluster node and resets them all on a timer; user logins need counts shared across the cluster and a lock through `LockOutManager`. Changing the admin manager to suit both would change admin console behaviour, which OF-2279 does not ask for.
- Alternative considered: generalising `LoginLimitManager` with a key prefix per use. It would share the counting code, at the cost of changing a class that was reworked in OF-3262 (May 2026).

### 2. Three tiers, and only tier 3 locks the account
- A `LockOutManager` lock applies to the account from every address. If it were triggered by the pair count (tier 1), anyone at any address could lock any user out by guessing on purpose. Tiers 1 and 2 therefore block only their own key, and only tier 3 (failures for one username from anywhere, at a higher limit) locks the account.
- Tier 3 is the only tier an attacker can use to lock out a genuine user; its limit is high, the lock expires, and setting its limit to 0 turns it off on its own.
- Tier 2 can affect genuine users who share an address (NAT, a reverse proxy). Its limit matches the admin console default; setting it to 0 turns it off.

### 3. Settings
Registered `SystemProperty` values, all dynamic, defined in `UserLoginLimitManager`:

| Property | Type | Default |
|---|---|---|
| `xmpp.auth.loginlimit.enabled` | Boolean | false |
| `xmpp.auth.loginlimit.maxAttemptsPerUsernameAddressPair` | Long, 0 turns tier 1 off | 10 |
| `xmpp.auth.loginlimit.perUsernameAddressPairInterval` | Duration | 15 minutes |
| `xmpp.auth.loginlimit.perUsernameAddressPairBlockDuration` | Duration, at least 1 second | 15 minutes |
| `xmpp.auth.loginlimit.maxAttemptsPerAddress` | Long, 0 turns tier 2 off | 40 |
| `xmpp.auth.loginlimit.perAddressInterval` | Duration | 15 minutes |
| `xmpp.auth.loginlimit.perAddressBlockDuration` | Duration, at least 1 second | 15 minutes |
| `xmpp.auth.loginlimit.maxAttemptsPerUsername` | Long, 0 turns tier 3 off | 50 |
| `xmpp.auth.loginlimit.perUsernameInterval` | Duration | 1 hour |
| `xmpp.auth.loginlimit.lockoutDuration` | Duration | 30 minutes |

Each has a `system_property.*` description in `openfire_i18n.properties`. With `enabled` false, the manager records nothing and blocks nothing.

### 4. Counting
- Attempts are counted when they are admitted, not after they fail: as soon as a password mechanism knows the username, `UserLoginLimitManager.tryAcquireAttempt` atomically checks every tier and, if none is at its limit, counts the attempt in all three. A successful login gives the attempt back to each tier whose window has not changed since the attempt was counted (and clears the tier 1 count for that pair); the reservation, held on the session, records those window starts. Checking and counting in one step stops concurrent connections from each slipping a guess past a limit. Attempts that end without the credentials being evaluated (a client disconnecting mid-exchange, a protocol error) stay counted.
- Each counter is a fixed window that starts at the first counted attempt for its key and ends after the tier's interval; the next attempt after that starts a new window. A limit of N admits N attempts in a window; the next is refused. (The admin manager compares with `>` and so allows N+1.) Refusal compares with the current limit, so lowering a limit takes effect at once.
- Counters live in clustered caches created through `CacheFactory` (registered in `CacheFactory.cacheNames` and `cacheProps`), so all nodes share them. Windows expire by their own timestamps; the caches keep entries for a fixed day from their last change, and the intervals, block durations and lock duration are at most one day. Each cached value includes its key in its reported size, so the cache size bound holds. The per-username caches are keyed by a SHA-256 digest of the normalised username (with a display form of at most 64 characters kept in the value) and are larger (20 MB), so spraying many long junk usernames cannot cheaply evict a victim's count. The per-key locks (`Cache.getLock`) are taken in a fixed order: pair, address, username.
- Usernames are normalised before counting: a trailing `@` plus this server's XMPP domain is removed (as `DefaultAuthProvider` does), then `Stringprep.nodeprep` is applied (failing that, the name is lower-cased), so `Alice`, `alice` and `alice@<domain>` share a counter. A normalised username longer than 1023 bytes in UTF-8 (the RFC 7622 limit for a localpart) cannot name an account: it is counted for its address only and never used as a cache key.
- Addresses come from `LocalSession.getHostAddress()`. IPv6 addresses are counted by their /64 prefix, the usual allocation to one subscriber, so one client cannot multiply its budget by rotating addresses within its own network; IPv4 addresses are counted individually.
- Attempts for usernames with no account are counted the same way, so the answers do not reveal which accounts exist.

### 5. Reaching a limit
- Tiers 1 and 2: the attempt that reaches the limit records when it did so in the key's counter, and the key is refused for the tier's block duration from then, whether or not the window has ended; counting for the key then starts again from zero. The block's end is worked out from the current setting, so a change applies to blocks in force. A block lasts its duration whatever the limit is changed to, unless the tier is turned off (limit 0), the limits are disabled or the block is cleared. A counter already at or over a limit that was lowered after its last attempt is refused until its window ends, and the next attempt starts its block. If the attempt that reached the limit succeeds, giving it back lifts the block. One security audit entry (`SecurityAuditManager.logEvent`) is written when the limit is reached. The tier 2 entry is filed under the address, with only the last username tried in its details, so an address block cannot appear under a real user's name. Audit subjects are escaped and truncated to 64 characters (the width of the audit table's column).
- Tier 3: the attempt after the Nth, from any address, blocks the username in the cache for the lockout duration, restarts the count, and is refused. Setting the lock on the next attempt, rather than during the Nth, means every admitted attempt is evaluated against the state it was admitted under. Then, if the account exists, has no lockout flag, and the lockout provider is writable and supports end times, `LockOutManager.disableAccount(username, now, now + lockoutDuration)` locks it as well, and an audit entry is written. When the user does not exist, the provider is read-only or ignores end times (a lock without an end would let an attacker lock an account indefinitely), or the account already has a flag (for example an administrator's lock, indefinite or scheduled to start later, which is left alone), the cache block alone applies, so the answer is the same. An administrator's account (`AdminManager.isUserAdmin(username, true)`, as the admin console decides it) is never locked either, and nor is one whose administrator status cannot be determined: the cache block refuses its XMPP and JMX logins, while the admin console, which honours only `LockOutManager` locks, stays open, so a deliberate lock-out cannot keep an administrator out of it.
- Restarting the count means that after a lock ends another N attempts lock the account again; otherwise a count already at the limit would stay there until the window ended.
- If the user lookup, the lockout check or the lock itself throws, the cache block still applies and a warning is logged: it fails closed.
- A successful login never lifts a `LockOutManager` lock.
- When an administrator unlocks an account (`LockOutEventListener.accountUnlocked`), the tier 3 counter and cache block for that username are cleared, so the next failure does not lock it again at once.
- The user's lockout page says when the login limits set a lock: the username has a tier 3 block in force and the lock ends when the block does (`UserLoginLimitManager.isLockSetByLoginLimits`), which a lock set by hand does not. For a username that tier 3 blocks without a lock (an administrator's), the page says the account is not locked but the login limits refuse it, and until when.
- Usernames and addresses are escaped before they reach logs or audit entries (`UserLoginLimitManager.forLog`), so control characters cannot forge log lines.

### 6. Refuse before the password is evaluated, with an answer that does not depend on it
- When a password mechanism knows the username, it first checks whether the account is locked and then reserves the attempt (decision 4). If the account is locked or the reservation is refused, the attempt fails without evaluating the credentials: with `account-disabled` when the username is locked out from every address (its account is locked, or tier 3 has blocked it, whether or not a lock could be set), and otherwise with `not-authorized`, the answer to a wrong password (`SASLAuthentication.refusalFailure`, through `UserLoginLimitManager.isLockedOut`).
- This also closes a password-guessing hole that exists today for accounts locked by an administrator: for SCRAM (and DIGEST-MD5) the lockout is only checked after the credentials are verified, so a locked account answers `account-disabled` to a correct password and `not-authorized` to a wrong one. With the check at the username, a locked account answers `account-disabled` whatever the password, for every mechanism (PLAIN and FAST tokens answered `not-authorized` before). Anyone who knows a username can learn that its account is locked; a genuine user who is locked out is told so, rather than seeing what looks like a wrong password. This applies whether or not the login limits are enabled. The lock check after a successful exchange in `SASLAuthentication` stays as a backstop, and also answers `account-disabled`; when the early check in the same attempt found the account not locked (recorded on the reservation), the backstop relies on it rather than querying again, because `LockOutManager` does not cache that an account is not locked and the second lookup would cost a database query on every login. A lock set during the exchange is then seen on the next login. Mechanisms without an early check (EXTERNAL), a failed early check, and an authorization identity other than the username are still checked after the exchange.
- Over SCRAM, a refused attempt continues the exchange with the salt and iteration count an admitted attempt would get (the account's own, or a generated salt for an unknown user), looks up no stored keys, and fails at the client's final message, so neither the refusal nor the account's existence is visible at an earlier step.
- If the early lock check itself throws (for example for a username `LockOutManager` cannot turn into a JID), the attempt is not refused on that alone: the backstop still stops a locked account, and refusing would turn a lookup bug into a login outage.
- Refused attempts are not counted, so a block does not extend itself.

### 7. Which attempts are counted
- XMPP logins, for SASL and SASL2 and every connection type (TCP, BOSH, WebSocket). Only password mechanisms reserve attempts: PLAIN and DIGEST-MD5 through `XMPPCallbackHandler` on its `NameCallback`, before the password callbacks; SCRAM-* in `ScramSaslServer` after the client-first message is parsed, before the stored credentials are looked up. The reservation is kept as session data for the success path and cleared at the start of every attempt, on every failure and on success. A caller with no session (for example a plugin using `XMPPCallbackHandler` directly) is checked against locks and blocks but not counted, because there is nowhere to keep the reservation for a refund.
- EXTERNAL and ANONYMOUS have no username to guess for. HT-*/FAST token attempts are not counted (tokens cannot practically be guessed), but they are refused for a locked account, and through the read-only check `isLoginAllowed` whenever a password attempt would be: for a blocked address, a blocked (username, address) pair, or a username blocked or at its limit in tier 3.
- JMX: an attempt counts towards tier 3 only, because the JMX authenticator is not given the client's address.
- Not counted: the admin console (it has `LoginLimitManager`) and the admin ad-hoc command "Authenticate a user" (an administrator checking a password). `AuthFactory.authenticate` therefore does not count attempts itself.

---

## Impact map

| Code | Change |
|---|---|
| `auth/UserLoginLimitManager` (new) | Settings, counters, blocks, tier 3 lock, unlock listener, audit entries |
| `net/XMPPCallbackHandler` | Takes the session; on `NameCallback` reserves the attempt, refusing a blocked or locked username before the password callbacks |
| `net/SASLAuthentication` | Passes the session to the callback handler; keeps the reservation on the session; gives it back on success; answers a refusal `account-disabled` or `not-authorized`; the post-success lock check answers `account-disabled` |
| `sasl/ScramSaslServer` | After parsing client-first: reserves the attempt; a refused attempt gets the same salt and iteration count as an admitted one and fails at client-final |
| `sasl/AbstractHtSaslServer` | Refuses an attempt the limits block alongside the existing lock check; a locked account is answered `account-disabled` |
| `JMXManager` | Reserves JMX attempts (tier 3) and refuses blocked or locked usernames |
| `user-lockout.jsp` | Says when the login limits set a lock, or refuse a username that is not locked |
| `XMPPServer` | Creates `UserLoginLimitManager` at start-up and after setup, so its unlock listener is registered on every node |
| `util/cache/CacheFactory` | Registers the new caches |
| `admin/servlet/UserLoginLimitsServlet` (new), `user-login-limits-page.jsp` (new), `admin-sidebar.xml` | The Login Limits page under Server Manager |
| `i18n/openfire_i18n.properties` | Property descriptions and page text |
| `documentation/login-limits-guide.html` (new), `documentation/index.html` | Admin guide |

Behaviour change with the feature off: a locked account answers `account-disabled` whatever the password, for every mechanism (decision 6). Before, SCRAM and DIGEST-MD5 answered `account-disabled` to the correct password only, and PLAIN and FAST tokens answered `not-authorized`.

---

## Implementation Sessions

Tests are written before the code in each session. Inside a worktree, pass `-Dmaven.gitcommitid.skip=true`; once `openfire_i18n.properties` changes, test with `-pl i18n,xmppserver`.

### Session 1: `UserLoginLimitManager`

**Goal:** The manager exists, is fully unit-tested, and is not yet called from anywhere.

**Files:**
- `xmppserver/src/main/java/org/jivesoftware/openfire/auth/UserLoginLimitManager.java` (new)
- `xmppserver/src/test/java/org/jivesoftware/openfire/auth/UserLoginLimitManagerTest.java` (new)
- `xmppserver/src/main/java/org/jivesoftware/util/cache/CacheFactory.java`
- `i18n/src/main/resources/openfire_i18n.properties`

**Tasks:**
1. Write tests (with `LockOutManager`, `SecurityAuditManager`, user lookup and a clock injected through a `@VisibleForTesting` constructor, and plain caches standing in for clustered ones) for: disabled means nothing recorded or blocked; each tier blocks after N failures and not before; each tier's block is scoped to its key (tier 1 does not block the same user from another address, nor another user from the same address); windows end and counting restarts; a limit of 0 turns its tier off; success clears only the pair counter; tier 3 locks an existing, unlocked account for the duration through `LockOutManager`; tier 3 leaves an existing lock alone; tier 3 uses a cache block for an unknown user or a read-only provider; unlock clears the tier 3 counter and block; usernames are normalised; one audit entry per limit crossed.
2. Implement decisions 1 to 5.
3. Add the property descriptions.

**Exit:** New tests pass; `./mvnw test -pl i18n,xmppserver` passes.

### Session 2: SASL integration and the uniform refusal

**Goal:** XMPP password logins are counted and refused as designed, and a locked account gives the same answer whatever the password.

**Files:**
- `xmppserver/src/main/java/org/jivesoftware/openfire/net/XMPPCallbackHandler.java`
- `xmppserver/src/main/java/org/jivesoftware/openfire/net/SASLAuthentication.java`
- `xmppserver/src/main/java/org/jivesoftware/openfire/sasl/ScramSaslServer.java`
- `xmppserver/src/main/java/org/jivesoftware/openfire/sasl/AbstractHtSaslServer.java`
- tests alongside the existing SASL tests (`SASLIntegrationTest`, `ScramSha1SaslServerTest` and similar)

**Tasks:**
1. Write tests: for PLAIN, SCRAM-SHA-1 and DIGEST-MD5 (where the existing test setup supports it), a locked account answers `not-authorized` with the correct password and with a wrong one, and the credentials are not checked; with the limits enabled, failures are recorded against the attempted username and the session's address, a blocked pair is refused before credentials are checked, refused attempts are not counted, success is recorded; EXTERNAL and ANONYMOUS failures are not counted; HT-* refuses a blocked username; with the limits disabled, behaviour is unchanged apart from decision 6.
2. Implement decisions 6 and 7 for SASL and SASL2.

**Exit:** New and existing SASL tests pass; full `xmppserver` suite passes.

### Session 3: JMX, unlock and documentation

**Goal:** JMX logins are covered, the manager is created at start-up on every node, and the feature is documented.

**Files:**
- `xmppserver/src/main/java/org/jivesoftware/openfire/JMXManager.java`
- `xmppserver/src/main/java/org/jivesoftware/openfire/XMPPServer.java` (or wherever comparable managers are initialised)
- the relevant page under `documentation/` (the admin guide's security section, or a new short page linked from it)

**Tasks:**
1. Write tests for the JMX authenticator: an attempt is reserved against tier 3 only (no address) before the password is checked, a refused reservation or a locked account is refused without checking the password, and a success gives the attempt back. Extract the authenticator into a testable method or class if needed.
2. Create `UserLoginLimitManager` eagerly at server start-up. Its unlock listener is registered when the singleton is created, so without this an administrator's unlock on a node that has not yet used the manager would not clear the tier 3 count.
3. Document: what the feature does, the three tiers, the settings, that it is off by default, that tier 3 locks appear on the user's lockout page and can be lifted there, that other blocks appear on the Login Limits page, the shared-address caution for tier 2, and that a reverse proxy hides client addresses.

**Exit:** Tests pass; documentation written.

### Session 4: Login Limits admin console page

**Goal:** An administrator can set the limits and see and clear the active blocks on one page.

**Files:**
- `UserLoginLimitsServlet` (new, mapped to `user-login-limits.jsp`) and `xmppserver/src/main/webapp/user-login-limits-page.jsp` (new, the view), so the logic is in testable Java; named for user logins so that they are not confused with the admin console's `LoginLimitManager`
- `xmppserver/src/main/resources/admin-sidebar.xml`
- `i18n/src/main/resources/openfire_i18n.properties`

**Tasks:**
1. Add a Login Limits item in the Server Manager sidebar, after the Security Audit Viewer.
2. Settings form: the on/off switch and the nine limits, windows and durations, with validation (non-negative limits, positive durations), CSRF protection as other settings pages have, an admin event logged for each change, and the page's success and error messages.
3. Active blocks: a table from `UserLoginLimitManager.getActiveBlocks(100)` (tier, username, address, failure count, when it ends), showing each tier's total and at most its 100 latest-ending blocks, so that an attack with many thousands of blocks does not make the page do a lockout lookup and a form for each; each row has a Clear button (CSRF-protected) calling `clearActiveBlock`, which reads only that block's key, under its lock, clears the block only if it is still in force, and records an admin event (itself the security audit entry, under the administrator's name). Tier 3 rows also list usernames whose account is locked through `LockOutManager`; clearing the row does not lift that lock, so for those rows the page says so and links to the user's lockout page.
4. Escape every value shown (usernames and addresses come from clients).
5. On a running server: save each setting, see a block appear, clear it.

**Exit:** The page works on a running server; the test suite passes.

### Session 5: Running server and coverage

**Goal:** The feature is shown working on a running server, and coverage of changed code meets 80%.

**Tasks:**
1. Build the server and run it (a local distribution or an image built from a `git archive` export; never overwrite `openfire:latest`). With the limits enabled and low limits set, show each tier with a scripted XMPP client: tier 1 refusal and that another address still works, tier 2, tier 3 lock visible on the user's lockout page and lifted there, the window ending, and a locked account answering `not-authorized` to the correct password over SCRAM. With the limits disabled, show nothing is blocked.
2. Measure coverage of changed and added lines with JaCoCo (`-Pcoverage`); add tests where it is short of 80%.

**Exit:** Every tier shown on a running server; coverage recorded.

### Session 6: Block duration for tiers 1 and 2

**Goal:** A tier 1 or 2 block lasts a set time from the attempt that reached the limit, rather than until the window ends (which made a block anything from almost the whole window to a moment, depending on how fast the limit was reached).

**Files:**
- `UserLoginLimitManager` and its test: `perUsernameAddressPairBlockDuration` and `perAddressBlockDuration` (default 15 minutes, 1 second to 1 day); `FailureWindow` records when it reached its limit (serialised for the cluster)
- `UserLoginLimitsServlet`, `user-login-limits-page.jsp` and their test: a block duration field for tiers 1 and 2
- `openfire_i18n.properties`, `documentation/login-limits-guide.html`, ADR-008

**Tasks:**
1. Tests first: a fast and a slow guesser both blocked for the full duration, a block outliving its window, a fresh count after a block, changing the duration mid-block, a lowered limit, a refund after a block ended, serialisation.
2. Record the block start in the counter; refuse while the block lasts; start afresh when it ends.
3. The page, the guide, the ADR and the property descriptions.
4. On a running server: a block outlasting its window and ending after its duration.
5. A PLAIN login as user@<this domain> with the right password fails on every release since 4.7.0 (OF-2289), because AuthFactory.authenticate checks the lockout under the name as given and LockOutManager cannot make a JID of it. With the login limits on, each such login would also count as a failure against the user. The fix (check the lockout and issue the token under the bare username, and ask the provider about the name as given) is in its own ticket and pull request, OF-3466 (#3568). Once that is merged, this branch adds a test that a successful PLAIN login as user@<this domain> leaves no count against the user.
6. SCRAM looks credentials up under the name as sent, and the SCRAM username is the localpart (RFC 6120 section 6.3.7), so a SCRAM attempt as user@domain cannot succeed (upstream behaviour, unchanged). Before this step it was counted against the user and never given back, so a client set up that way blocked or locked the account with the right password. A SCRAM attempt for a name containing '@' is now refused without being counted, through the existing refused path. DIGEST-MD5 as user@domain succeeds (the provider reads the password for that name) and gives its attempt back, so it is unchanged. Tests in SASLAuthenticationUserAtDomainTest.

**Exit:** The tests pass and the demo shows a block ending after its duration.

### Session 7: Final review fixes

**Goal:** Address the findings of the final review of the change.

**Tasks:**
1. With the limits off, or tier 1 off, a reservation carries no pair key, so a successful login takes no tier 1 lock.
2. The tier 1 and 2 audit entry and log line, written when the Nth attempt is admitted, say the block applies unless that attempt succeeds.
3. Key tier 1 by the address and the username's digest, keeping the username (shortened to 64 characters) in the counter for display, so long junk usernames cannot push a victim's tier 1 count out of its cache.
4. Release every cache lock even when releasing another fails; log a tier 3 username that is not a valid account name at debug without a stack trace.
5. The Login Limits page: remove the row for a listing with no sections at all, which could not be shown as the page always receives a section for each kind of block (each section keeps its own row for when it has no blocks), and an unused attribute, and take the longest duration from `UserLoginLimitManager.MAX_DURATION`.
6. Correct the guide, the ADR and the property descriptions: a change to the lock duration applies only to later locks; a refused SCRAM attempt is answered sooner than a wrong password; the salt of an account with no stored SCRAM salt; blocks returning when the limits are turned off and on again.
7. Re-measure coverage of changed and added code.
8. On everything an administrator reads, name the limits by what they count instead of numbering them as tiers, and say IP address rather than address (an XMPP address is a JID); IPv6 addresses are counted by /64 network. The page follows reg-settings.jsp: one settings form across a box for turning the limits on and off and a box for each kind of limit, saved by one button below them; then a box for each kind of block, titled with its count and holding a table of only the columns that apply: Usernames blocked from one IP address (Username, IP address, Failed attempts, Ends), IP addresses blocked for all usernames (IP address, Failed attempts, Ends) and Usernames blocked from all IP addresses (Username, Failed attempts, Ends, Account, the last linking a locked account to its lockout page).
9. Give the Blocks in Force section headings their own message keys (they had replaced the column headings by sharing keys), and add a test that fails when a message bundle defines a key twice.

**Exit:** The tests pass, coverage is recorded, and the demo passes.

---

## Critical Files

| File | Purpose |
|---|---|
| `xmppserver/src/main/java/org/jivesoftware/admin/LoginLimitManager.java` | The admin console's limits, the model for this feature |
| `xmppserver/src/main/java/org/jivesoftware/openfire/lockout/LockOutManager.java` | Account locks, used by tier 3 |
| `xmppserver/src/main/java/org/jivesoftware/openfire/net/SASLAuthentication.java` | SASL and SASL2 negotiation, success and failure handling |
| `xmppserver/src/main/java/org/jivesoftware/openfire/net/XMPPCallbackHandler.java` | Username and password callbacks for PLAIN and DIGEST-MD5 |
| `xmppserver/src/main/java/org/jivesoftware/openfire/sasl/ScramSaslServer.java` | SCRAM; parses the username from client-first |
| `xmppserver/src/main/java/org/jivesoftware/openfire/JMXManager.java` | JMX authenticator |

---

## Verification Checklist

- [x] Disabled by default; with it disabled, nothing is counted or blocked
- [x] Each tier blocks after its limit, scoped to its key; a tier 1 or 2 block lasts its block duration from the attempt that reached the limit
- [x] Tier 3 locks through `LockOutManager`, leaves existing locks alone, and an administrator's unlock resets it
- [x] A refused attempt gets an answer that does not depend on the password, for PLAIN, SCRAM, DIGEST-MD5 and FAST tokens: `account-disabled` for a locked account or a username blocked by tier 3, `not-authorized` for a tier 1 or tier 2 block; over SCRAM a refused attempt gets the same salt and iteration count as an admitted one
- [ ] Counts are shared across cluster nodes (clustered caches): by construction and unit tests; not run on a cluster
- [x] JMX failures counted for tier 3; admin console and the "Authenticate a user" command not counted
- [x] Full `xmppserver` test suite passes
- [x] Coverage of changed and added code at least 80%: JaCoCo (`-Pcoverage`, a clean build running the full `i18n` and `xmppserver` suites, 3179 tests) covers 781 of 816 changed or added lines (95.7%) and 416 of 457 branches (91.0%) against upstream/main, measured on the final code (2026-11-12)
- [x] Login Limits page sets the limits and lists and clears blocks
- [x] Shown on a running server: every tier, the uniform answer over PLAIN and SCRAM (including an unchanged SCRAM salt across a block), the page, a block ending, and an administrator's unlock

---

## Open Questions

- Should the feature be on or off by default? It is off in this change, because it can refuse logins that succeed today; to be settled in review.
