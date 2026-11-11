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
  session_8_upstream_review_changes: completed (2026-11-13)
  session_9_blocks_apart_from_counts: completed (2026-11-23)
  session_10_second_review_other_fixes: completed (2026-11-20)
  session_11_blocks_by_user_and_address: completed (2026-11-23)
  session_12_user_lockout_page: completed (2026-11-23)
  session_13_login_limits_by_address_and_user_list: completed (2026-11-23)
  session_14_unblock_user: completed (2026-11-23)
  session_15_unlock_clears_limits: completed (2026-11-23)
  session_16_reenable_user_command: completed (2026-11-24)
  session_17_third_review: completed (2026-11-25)
  session_18_unknown_username_counts: completed (2026-11-25)
  session_19_lock_cache_name: completed (2026-11-25)
---

# Login limits for user accounts

## Summary

Add brute-force protection for XMPP user logins, equivalent to the protection the admin console has through `LoginLimitManager`. Openfire counts failed password attempts in three ways and refuses further attempts when a limit is reached:

| Tier | Counts failures for | On reaching the limit | Default limit |
|---|---|---|---|
| 1 | one username from one address | that address may not attempt that username for the block duration | 10 in 15 minutes, blocked for 15 minutes |
| 2 | one address, any username | that address may not attempt any username for the block duration | off; 15-minute window and block once a limit is set |
| 3 | one username, from any address | the account is locked through `LockOutManager` for a set duration | off; 1-hour window and 30-minute lock once a limit is set |

The feature is on by default with only tier 1 active. While a block or lock applies, every attempt is refused before the password is evaluated: with `account-disabled` for a locked account or a username blocked by tier 3, and with `not-authorized` for a tier 1 or tier 2 block. An admin console page sets the limits and lists the active blocks.

The decision and the options considered are recorded in `doc/adr/014-login-limits-for-user-accounts.md`.

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
- Tier 3 is the only tier an attacker can use to lock out a genuine user; the lock expires, and its limit is 0 (off) by default.
- Tier 2 can affect genuine users who share an address (NAT, a reverse proxy, a connection manager); its limit is 0 (off) by default.
- Tier 1 refuses only the username that reached it, from that address, so it is on by default and stops one machine guessing as fast as it can on every installation. Where clients share an address (a reverse proxy, a connection manager, NAT), it refuses that username to everyone behind the address; the guide and the ADR say to turn it off there unless Openfire sees each client's address.

### 3. Settings
Registered `SystemProperty` values, all dynamic, defined in `UserLoginLimitManager`:

| Property | Type | Default |
|---|---|---|
| `xmpp.auth.loginlimit.enabled` | Boolean | true |
| `xmpp.auth.loginlimit.maxAttemptsPerUsernameAddressPair` | Long, 0 turns tier 1 off | 10 |
| `xmpp.auth.loginlimit.perUsernameAddressPairInterval` | Duration | 15 minutes |
| `xmpp.auth.loginlimit.perUsernameAddressPairBlockDuration` | Duration, at least 1 second | 15 minutes |
| `xmpp.auth.loginlimit.maxAttemptsPerAddress` | Long, 0 turns tier 2 off | 0 |
| `xmpp.auth.loginlimit.perAddressInterval` | Duration | 15 minutes |
| `xmpp.auth.loginlimit.perAddressBlockDuration` | Duration, at least 1 second | 15 minutes |
| `xmpp.auth.loginlimit.maxAttemptsPerUsername` | Long, 0 turns tier 3 off | 0 |
| `xmpp.auth.loginlimit.perUsernameInterval` | Duration | 1 hour |
| `xmpp.auth.loginlimit.lockoutDuration` | Duration | 30 minutes |

Each has a `system_property.*` description in `openfire_i18n.properties`. With `enabled` false, the manager records nothing and blocks nothing.

### 4. Counting
- Attempts are counted when they are admitted, not after they fail: as soon as a password mechanism knows the username, `UserLoginLimitManager.tryAcquireAttempt` atomically checks every tier and, if none is at its limit, counts the attempt in all three. A successful login gives the attempt back to each tier whose window has not changed since the attempt was counted (and clears the tier 1 count for that pair); the reservation, held on the session, records those window starts. Checking and counting in one step stops concurrent connections from each slipping a guess past a limit. Attempts that end without the credentials being evaluated (a client disconnecting mid-exchange, a protocol error) stay counted.
- Each counter is a fixed window that starts at the first counted attempt for its key and ends after the tier's interval; the next attempt after that starts a new window. A limit of N admits N attempts in a window; the next is refused. (The admin manager compares with `>` and so allows N+1.) Refusal compares with the current limit, so lowering a limit takes effect at once.
- Counters live in clustered caches created through `CacheFactory` (registered in `CacheFactory.cacheNames` and `cacheProps`), so all nodes share them. Windows expire by their own timestamps; the caches keep entries for a fixed day from their last change, and the intervals, block durations and lock duration are at most one day. Each cached value includes its key in its reported size, so the cache size bound holds. Usernames are keyed by a SHA-256 digest of the normalised username (with a display form of at most 64 characters kept in the value), so long junk usernames take no more room than short ones. The caches are bounded, so a flood of attempts that reach no limit can make a counts cache evict its oldest entries; blocks of every tier are therefore kept in caches of their own ("User Login Blocks per Username and IP Address", "... per IP Address", "... per Username"), apart from the counts. Only an attempt that reaches a limit creates a block, so a flood cannot remove one; evicting blocks would cost the limit's number of attempts for each block created. The tier 1 and tier 3 counts of usernames that have no account are kept in caches of their own ("User Login Failures per Unknown Username and IP Address", 5 MB, and "User Login Failures per Unknown Username", 20 MB), chosen by one `UserManager` lookup when the attempt is reserved, so a flood of made-up usernames evicts only their counts; to evict a real user's count, an attacker would have to fail at as many real usernames as the cache holds. A lookup that fails for any reason other than an invalid username counts the attempt as an account's. The per-key locks (`Cache.getLock`) are taken in a fixed order: pair, address, username; a key's count lock also guards its block.
- Usernames are normalised before counting: a trailing `@` plus this server's XMPP domain is removed (as `DefaultAuthProvider` does), then `Stringprep.nodeprep` is applied (failing that, the name is lower-cased), so `Alice`, `alice` and `alice@<domain>` share a counter. A normalised username longer than 1023 bytes in UTF-8 (the RFC 7622 limit for a localpart) cannot name an account: it is counted for its address only and never used as a cache key.
- Addresses come from `LocalSession.getHostAddress()`. IPv6 addresses are counted by their /64 prefix, the usual allocation to one subscriber, so one client cannot multiply its budget by rotating addresses within its own network; IPv4 addresses are counted individually.
- Attempts for usernames with no account are counted, limited and blocked the same way (in their own counts caches), so the answers do not reveal which accounts exist.

### 5. Reaching a limit
- Tiers 1 and 2: the attempt that reaches the limit starts a block, stored in the tier's blocks cache with when it started and the failures that led to it, and removes the key's count; the key is refused for the tier's block duration from then, whether or not the window has ended; counting for the key then starts again from zero. The block's end is worked out from the current setting, so a change applies to blocks in force. A block lasts its duration whatever the limit is changed to, unless the tier is turned off (limit 0), the limits are disabled or the block is cleared. A counter already at or over a limit that was lowered after its last attempt is refused until its window ends, and the next attempt starts its block. If the attempt that reached the limit succeeds, giving it back lifts the block. One security audit entry (`SecurityAuditManager.logEvent`) is written when the limit is reached. The tier 2 entry is filed under the address, with only the last username tried in its details, so an address block cannot appear under a real user's name. Audit subjects are escaped and truncated to 64 characters (the width of the audit table's column).
- Tier 3: the attempt after the Nth, from any address, blocks the username in the cache for the lockout duration, restarts the count, and is refused. Setting the lock on the next attempt, rather than during the Nth, means every admitted attempt is evaluated against the state it was admitted under. Then, if the account exists, has no lockout flag, and the lockout provider is writable and supports end times, `LockOutManager.disableAccount(username, now, now + lockoutDuration)` locks it as well, and an audit entry is written. When the user does not exist, the provider is read-only or ignores end times (a lock without an end would let an attacker lock an account indefinitely), or the account already has a flag (for example an administrator's lock, indefinite or scheduled to start later, which is left alone), the cache block alone applies, so the answer is the same. An administrator's account (`AdminManager.isUserAdmin(username, true)`, as the admin console decides it) is never locked either, and nor is one whose administrator status cannot be determined: the cache block refuses its XMPP and JMX logins, while the admin console, which honours only `LockOutManager` locks, stays open, so a deliberate lock-out cannot keep an administrator out of it.
- Restarting the count means that after a lock ends another N attempts lock the account again; otherwise a count already at the limit would stay there until the window ended.
- If the user lookup, the lockout check or the lock itself throws, the cache block still applies and a warning is logged: it fails closed.
- A successful login never lifts a `LockOutManager` lock.
- When an administrator unlocks an account (`LockOutEventListener.accountUnlocked`), the tier 3 counter and cache block for that username are cleared, so the next failure does not lock it again at once. When the lock was set by the login limits and is lifted from the user's Lock Out page or with the Re-Enable a User command, the user's tier 1 blocks and counts are cleared too, as Clear login limits clears them (`UserLoginLimitManager.unlockAccount`), so that one more wrong password does not block the user again from an address that had failures counted. The unlock event cannot do this itself, as the lock is gone by the time it fires and so cannot be told apart from one set by hand; other ways of unlocking clear only the tier 3 count.
- The user's lockout page says when the login limits set a lock: the username has a tier 3 block in force and the lock ends when the block does (`UserLoginLimitManager.isLockSetByLoginLimits`), which a lock set by hand does not. For a username that tier 3 blocks without a lock (an administrator's), the page says the account is not locked but the login limits refuse it, and until when.
- Usernames and addresses are escaped before they reach logs or audit entries (`UserLoginLimitManager.forLog`), so control characters cannot forge log lines.

### 6. Refuse before the password is evaluated, with an answer that does not depend on it
- When a password mechanism knows the username, it first checks whether the account is locked and then reserves the attempt (decision 4). If the account is locked or the reservation is refused, the attempt fails without evaluating the credentials: with `account-disabled` when the username is locked out from every address (its account is locked, or tier 3 has blocked it, whether or not a lock could be set), and otherwise with `not-authorized`, the answer to a wrong password (`SASLAuthentication.refusalFailure`, through `UserLoginLimitManager.isLockedOut`).
- This also closes a password-guessing hole that exists today for accounts locked by an administrator: for SCRAM (and DIGEST-MD5) the lockout is only checked after the credentials are verified, so a locked account answers `account-disabled` to a correct password and `not-authorized` to a wrong one. With the check at the username, a locked account answers `account-disabled` whatever the password, for every mechanism (PLAIN and FAST tokens answered `not-authorized` before). Anyone who knows a username can learn that its account is locked; a genuine user who is locked out is told so, rather than seeing what looks like a wrong password. This applies whether or not the login limits are enabled. The lock check after a successful exchange in `SASLAuthentication` stays as a backstop: it answers `account-disabled` where no password was checked (EXTERNAL) and `not-authorized` after a password exchange, where the password was right by then and `account-disabled` would confirm it. It looks the lock up again rather than relying on the early check, so that a lock set during the exchange (whose length the client controls) refuses that exchange: an administrator locking an account whose password was stolen must stop logins already in progress. `LockOutManager` caches that an account is not locked, as it already cached that an account is locked, so the second lookup costs no query, and a login queries the lockout provider less often than on main, where every login of an account that is not locked queried it.
- `LockOutManager` keeps each account's state as one entry in its clustered cache, "Account Lock States", whose value is now a `CacheableOptional<LockOutFlag>` (as `GroupManager` caches groups): the account's lock, or that it has none. It does so only when the provider allows caching. Setting or lifting a lock is one put. A lookup that has to query the provider, and setting or lifting a lock, hold the account's cluster lock in that cache around the provider access and the cache update, so that a node caching "not locked" cannot overwrite or outlive a lock set at the same time on another node; an answer from the cache, either way, takes no lock. With caching on, a lock set directly in the provider's store, outside Openfire, is seen once the cached entry expires (up to 15 minutes), as an unlock set there already was.
- Over SCRAM, a refused attempt continues the exchange with the salt and iteration count an admitted attempt would get (the account's own, or a generated salt for an unknown user), looks up no stored keys, and fails at the client's final message, so neither the refusal nor the account's existence is visible at an earlier step.
- If the early lock check itself throws (for example for a username `LockOutManager` cannot turn into a JID), the attempt is not refused on that alone: the backstop still stops a locked account, and refusing would turn a lookup bug into a login outage.
- Refused attempts are not counted, so a block does not extend itself.

### 7. Which attempts are counted
- XMPP logins, for SASL and SASL2 and every connection type (TCP, BOSH, WebSocket). Only password mechanisms reserve attempts: PLAIN and DIGEST-MD5 through `XMPPCallbackHandler` on its `NameCallback`, before the password callbacks; SCRAM-* in `ScramSaslServer` after the client-first message is parsed, before the stored credentials are looked up. The reservation is kept as session data for the success path and cleared at the start of every attempt, on every failure and on success. A caller with no session (for example a plugin using `XMPPCallbackHandler` directly) is checked against locks and blocks but not counted, because there is nowhere to keep the reservation for a refund.
- EXTERNAL and ANONYMOUS have no username to guess for. HT-*/FAST token attempts are not counted (tokens cannot practically be guessed), but they are refused for a locked account, and through the read-only check `isLoginAllowed` whenever a password attempt would be: for a blocked address, a blocked (username, address) pair, or a username blocked or at its limit in tier 3.
- JMX: an attempt counts towards tier 3 only, because the JMX authenticator is not given the client's address.
- Not counted: the admin console (it has `LoginLimitManager`) and the admin ad-hoc command "Authenticate a user" (an administrator checking a password). `AuthFactory.authenticate` therefore does not count attempts itself.

### 8. The admin pages answer why a user cannot log in
The user's Lock Out page lists every block that refuses that user (the block from any IP address, and each block from one address) next to the account's lock, each with a Clear button; clearing lifts everything the login limits did, including a lock they set, but never a lock set by hand. An address block affects everyone at that address, so it is not cleared from a user's page; the page says it refused that username and links to the Login Limits page, which lists blocks by IP address (for all usernames, or for which ones) with a Clear button per address. The user list marks a user the login limits refuse without a locked account. Blocks for usernames with no account appear on no page: nobody can log in with those names, and the blocks end by themselves.

Amended in session 14: the Lock Out page clears the login limits for the user in one action (Clear login limits) in place of a Clear button per block. A cleared tier 1 block left the tier 3 count behind, so one more wrong password could block the user again at once; the action now resets the user's tier 1 counts from every address and tier 3 count with the blocks. Address blocks are still left alone, and the page says when one still refuses the user. The account lock buttons are named Lock account and Unlock account, so that each label names what it acts on. Unlock account on a lock the login limits set clears the login limits as Clear login limits does, amended in session 15: unlocking only the lock left the user's counts from each address behind.

---

## Impact map

| Code | Change |
|---|---|
| `auth/UserLoginLimitManager` (new) | Settings, counters, blocks, tier 3 lock, unlock listener, audit entries |
| `net/XMPPCallbackHandler` | Takes the session; on `NameCallback` reserves the attempt, refusing a blocked or locked username before the password callbacks |
| `net/SASLAuthentication` | Passes the session to the callback handler; keeps the reservation on the session; gives it back on success; answers a refusal `account-disabled` or `not-authorized`; the post-success lock check answers `account-disabled` without a password exchange and `not-authorized` after one |
| `sasl/ScramSaslServer` | After parsing client-first: reserves the attempt; a refused attempt gets the same salt and iteration count as an admitted one and fails at client-final |
| `sasl/AbstractHtSaslServer` | Refuses an attempt the limits block alongside the existing lock check; a locked account is answered `account-disabled` |
| `JMXManager` | Reserves JMX attempts (tier 3) and refuses blocked or locked usernames |
| `UserLockoutServlet`, `user-lockout-page.jsp` | The user's Lock Out page: whether the user can log in, the account's lock, and the login limits' blocks for the user |
| `XMPPServer` | Creates `UserLoginLimitManager` at start-up and after setup, so its unlock listener is registered on every node |
| `lockout/LockOutManager` | Caches that an account is not locked; guards loading and changing an account's entry with the cache's cluster lock |
| `util/cache/CacheFactory` | Registers the new caches |
| `admin/servlet/UserLoginLimitsServlet` (new), `user-login-limits-page.jsp` (new), `admin-sidebar.xml` | The Login Limits page under Server Settings, after Registration & Login: the settings, and the IP addresses from which logins are refused, each with a Clear button |
| `user-summary.jsp` | Marks a user the login limits refuse from every IP address without a locked account |
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
- `openfire_i18n.properties`, `documentation/login-limits-guide.html`, ADR-014

**Tasks:**
1. Tests first: a fast and a slow guesser both blocked for the full duration, a block outliving its window, a fresh count after a block, changing the duration mid-block, a lowered limit, a refund after a block ended, serialisation.
2. Record the block start in the counter; refuse while the block lasts; start afresh when it ends.
3. The page, the guide, the ADR and the property descriptions.
4. On a running server: a block outlasting its window and ending after its duration.
5. A PLAIN login as user@<this domain> with the right password fails on every release since 4.7.0 (OF-2289), because AuthFactory.authenticate checks the lockout under the name as given and LockOutManager cannot make a JID of it. With the login limits on, each such login would also count as a failure against the user. The fix (check the lockout and issue the token under the bare username, and ask the provider about the name as given) is in its own ticket and pull request, OF-3466 (#3568). Once that is merged, this branch adds a test that a successful PLAIN login as user@<this domain> leaves no count against the user.
6. Openfire looks SCRAM credentials up under the name as sent, and no account has an '@' in its name, so a SCRAM attempt as user@domain cannot succeed (upstream behaviour, unchanged). Before this step it was counted against the user and never given back, so a client set up that way blocked or locked the account with the right password. A SCRAM attempt for a name containing '@' is now refused without being counted, through the existing refused path. DIGEST-MD5 as user@domain succeeds (the provider reads the password for that name) and gives its attempt back, so it is unchanged. Tests in SASLAuthenticationUserAtDomainTest.

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

### Session 8: Upstream review changes

**Goal:** Address the review of the pull request (2026-11-12), one commit per point.

**Tasks:**
1. Answer a refused attempt for a username locked out from every address (its account is locked, or tier 3 has blocked it) `account-disabled`, still without checking the password, for every mechanism (PLAIN and FAST tokens included) and for the lock check after a successful exchange; tier 1 and tier 2 blocks stay `not-authorized` (decision 6).
2. Say on the user's lockout page when the login limits set a lock, and when they refuse a username that is not locked.
3. Move the Login Limits page to Server Settings, after Registration & Login.
4. Look a refused SCRAM attempt's salt up as for any attempt (the separate lookup that was meant not to create a missing salt did create one with `DefaultAuthProvider`), and stop citing RFC 6120 for the SCRAM username form: Openfire looks the credentials up under the name as sent.
5. Replace the test of a new attempt clearing an earlier reservation with one that ends in a successful EXTERNAL login, which fails when the clear is removed.
6. Turn the limits on by default with only tier 1 active; tiers 2 and 3 have a limit of 0.

**Exit:** The full `i18n` and `xmppserver` suites pass and the demo passes.

### Session 9: Keep blocks apart from the counts

**Goal:** Stop a flood of attempts from evicting a tier 1 or tier 2 block (review, 2026-11-20). With tier 2 off, one address could try about 30,000 junk usernames, fill the 5 MB tier 1 counts cache, and make it evict the entry holding the victim's count or block, getting a fresh set of guesses each time.

**Tasks:**
1. Tests first: a tier 1 block, a tier 2 block and a block started over a lowered limit still refuse once their count has gone from the counts cache; a tier 1 block survives a flood that overflows a counts cache of the production size.
2. Keep tier 1 and tier 2 blocks in caches of their own ("User Login Blocks per Username and IP Address", "User Login Blocks per IP Address"), one per tier as for the counts, registered in `CacheFactory` with the counts caches' size and lifetime. A block value (`UserLoginLimitManager.AddressBlock`) holds its tier, username for display, address, failures, when it started and the start of the window that led to it, so that a success can still give back an attempt counted in that window. Reaching a limit removes the key's count. A key's count lock guards its block; the lock order is unchanged.
3. Correct the ADR, the guide and this plan: a flood cannot remove a block; it can reset counts, gaining fewer than the limit of guesses per flood of roughly the cache's capacity.

**Exit:** The `xmppserver` suite passes.

---

### Session 10: Second upstream review, other fixes

**Goal:** Address the other inline comments of the second review (2026-11-20), one commit per point.

**Tasks:**
1. Leave a duration field empty when its setting is not a whole number of minutes, so that entering a value always saves it and saving the page for another setting keeps the finer value.
2. Declare the Login Limits page's variables for the IDE.
3. Cache in `LockOutManager` that an account is not locked, and look the lock up again after a successful exchange, so that a lock set during a SCRAM or DIGEST-MD5 exchange refuses it.

**Exit:** The full `xmppserver` suite passes.

---

### Session 11: Blocks by user and by address

**Goal:** Give the redesigned pages what they need from `UserLoginLimitManager`: one user's blocks, blocks grouped by IP address, and the usernames an address block refused.

**Tasks:**
1. `getBlocksForUsername(username, max)`: every block in force for one username, for the user's lockout page: the tier 3 block, the tier 1 blocks (one per address, the latest to end first, at most `max`, with the total) and the tier 2 blocks that recorded refusing it. The username is normalised as an attempt's is. Finding tier 1 blocks reads every key of the tier 1 blocks and counts caches, as tier 1 keys start with the address.
2. `getBlocksByAddress(maxAddresses, maxUsernamesPerAddress)`: blocks grouped by IP address, the latest end first, with totals: for each address its tier 2 block (with the usernames it refused) and its tier 1 blocks, both when it has both, as a tier 1 block can outlast the tier 2 block. For the Login Limits page. As in `getActiveBlocks`, counts at a lowered limit are included and usernames are shown shortened and marked.
3. Record on a tier 2 block the most recent usernames it refused (`MAX_REFUSED_USERNAMES`, 10), newest first and each once, in the form shown (shortened, marked), with the username's key for matching; written under the address lock when the block refuses an attempt, and not written when the username is already the newest, so that retrying one username adds no cluster write. Only attempts that reserve (`tryAcquireAttempt`) are recorded; the read-only check for FAST tokens records nothing.
4. `clearBlocksForUsername(username)`: lifts the username's tier 1 blocks from every address and its tier 3 block, and the account's lock when `isLockSetByLoginLimits` says the limits set it, never one set by hand; if that lock cannot be checked or lifted, the tier 3 block is kept, so the lock is still recognised, and the result says so. `clearBlocksForAddress(address)`: lifts the address's tier 2 block and its tier 1 blocks, given the address as listed or any address in its /64. Both return what was lifted, for the caller's admin event.

**Exit:** `UserLoginLimitManagerTest` covers each method, including the bounds and the lock-by-hand case.

---

### Session 12: The user's Lock Out page

**Goal:** Answer "why can't this user log in?" on one page (decision 8).

**Tasks:**
1. Move the page's logic into `UserLockoutServlet`, mapped to `user-lockout.jsp` so that existing links keep working, with the view `user-lockout-page.jsp`, following the Login Limits page, and declare its variables for the IDE. The lock and unlock forms keep their choices and behaviour (closing the user's sessions, signing out an administrator who locks their own account) and now post.
2. Show the account's lock as before ("The account is not locked." when it is not), and a Login limits section listing the user's blocks (from any IP address, and from each address: From, Failed attempts, Ends), each with a Clear button, and saying how many are not shown when the bounded list (20) is truncated. A row is cleared through a new `UserLoginLimitManager.clearBlockForUsername(username, tier, key)`, which clears only that username's tier 1 or tier 3 block, and with the tier 3 block the lock the login limits set (never one set by hand), keeping the block if that lock cannot be lifted, as `clearBlocksForUsername` does. Notes under the table say that blocks end by themselves, whether clearing the block from any IP address unlocks the account, and, for an administrator, that the limits never lock an administrator's account. The section is left out when the limits refuse the user nothing.
3. Say when an address block refused this username, with a link to the Login Limits page, where that block can be cleared; a token for an address block is refused on the user's page.
4. Apply a read-only lockout provider's error only to the lock and unlock forms (disabled in the page, and refused by the servlet), so that the Clear buttons keep working.
5. Log each clear as an admin event and protect every post with the CSRF token, as on the Login Limits page.

**Exit:** Servlet tests cover each case of the mockups (a lock set by the limits, an administrator refused without a lock, a block from one address, an address block that refused the user, a read-only provider).

---

### Session 13: Login Limits page by IP address, and the user list

**Goal:** Make the Login Limits page an overview by IP address, and mark refused users in the user list.

**Tasks:**
1. Replace the three tables of blocks with one table of IP addresses from `getBlocksByAddress(100, 6)`, the latest to end first: "All usernames (N failed attempts)" for an address block, without the usernames it refused (those are on each user's page); "N usernames: a, b and K more" for tier 1 blocks, each username that names an account linking to its Lock Out page (URL-encoded) and others, or a username shortened for display, shown as plain text; both on one row, on two lines, when an address has both; when the last block ends; and a Clear button (CSRF-protected) that lifts every block for the address through `clearBlocksForAddress` and logs an admin event with the address and usernames escaped. Each username is looked up at most once per page. The table says how many addresses are not shown. The intro points to the user's Lock Out page.
2. Remove the table of usernames blocked from every IP address; those blocks are now on each user's page, and blocks for usernames with no account end by themselves. With it go the clearing of one block by token on this page, the rows for locked accounts without a Clear button, `UserLoginLimitManager.getActiveBlocks` and `TierBlocks`, and their strings and tests; `clearActiveBlock` is no longer public. The block rows and tokens of the Lock Out page move into `UserLockoutServlet`.
3. Mark in the user list a user that tier 3 refuses without a locked account (for example an administrator) with the padlock image (`lock.gif`) and its own title, distinct from the icons for a locked account and a lock set to start later; one cache read per row (`hasUsernameBlock`). Tier 1 blocks are not marked, as finding them would read a whole cache per row.
4. On the user's Lock Out page, when the login limits refuse the user nothing, say so in one line in place of the Login limits box.
5. Update the guide, ADR-014's description of the pages, and the i18n strings; remove the strings no longer used.

**Exit:** The full `i18n` and `xmppserver` suites pass; the pages render on a running server with each kind of block.

---

### Session 14: Clear a user's login limits in one action

**Goal:** Give a user a fresh start from the Lock Out page (decision 8, as amended).

**Tasks:**
1. `clearBlocksForUsername` also resets the username's tier 1 counts from every address and its tier 3 count, whether or not they have reached a limit, each under its key's lock, and reports whether it reset any (`ClearedBlocks.countsReset`). If the lock the login limits set cannot be lifted, the tier 3 block and count are kept, as before. Tier 2 blocks and counts are left alone.
2. `getBlocksForUsername` also says whether failures are counted for the username in a window that has not ended (`BlocksForUsername.counted`), from the tier 1 keys it already reads and one read of the tier 3 count.
3. Replace the Clear button per block on the Lock Out page with one Clear login limits button below the list, shown when there is a block or a count to clear; the rows are information only. It posts with the CSRF token and logs one admin event naming the username and each block, escaped. The success message says the login limits were cleared, that the account was unlocked if a lock the limits set was lifted, and that an address block still refuses the user if one does. Each address block listed says that clearing the login limits does not lift it.
4. Remove `clearBlockForUsername`, the block tokens and their parsing, their tests and strings. Rename the lock buttons Lock account and Unlock account.
5. Update the guide, ADR-014 and the i18n strings.

**Exit:** The full `i18n` and `xmppserver` suites pass.

---

### Session 15: Unlocking a lock the login limits set clears them

**Goal:** Unlock account on a lock the login limits set gives the user the same fresh start as Clear login limits (decision 8, as amended); unlocking it alone left the user's tier 1 counts, so one wrong password re-blocked the user from that address.

**Tasks:**
1. Add `UserLoginLimitManager.unlockAccount`: for a lock the login limits set it calls `clearBlocksForUsername`, which lifts the lock; any other lock is lifted through `LockOutManager` alone, as before. The unlock event listener keeps clearing the tier 3 count on every unlock; it cannot tell the two kinds of lock apart, as the lock is gone when it fires.
2. The Lock Out page unlocks through it, logs what was cleared with the unlock, says on the user's page that the login limits were cleared too, and reports a lock that could not be lifted. Tier 2 blocks are never touched.
3. Update the note on a lock set by the login limits, the guide, ADR-014 and the i18n strings.

**Exit:** The full `i18n` and `xmppserver` suites pass.

---

### Session 16: Re-Enable a User clears the login limits for a lock they set

**Goal:** The XEP-0133 Re-Enable a User command gives a user whose lock the login limits set the same fresh start as Unlock account on the Lock Out page; it lifted the lock alone, so the user's counts from each IP address survived it. Kept in its own commit so that it can be reverted on its own.

**Tasks:**
1. `ReEnableUser` unlocks each account through `UserLoginLimitManager.unlockAccount`; a lock set by hand is lifted alone, as before. A lock the login limits set that could not be lifted is reported as finished with errors.
2. Add `ReEnableUserTest`, the command's first tests.
3. Update ADR-014, the guide and the note on unlocking.

**Exit:** The full `xmppserver` suite passes.

---

### Session 17: Third review

**Goal:** Address the third upstream review, apart from the eviction of tier 1 counts by a flood (session 18).

**Tasks:**
1. Redesign the Lock Out page around the question "can this user log in?": one sentence at the top, in a warning box when the user cannot log in and an info box when they can, then two matching boxes, Account lock and Login limits, each with its status, its own button and a short hint under the button. The lock form loses the "Are you sure" question and the Cancel button, and its radio buttons become two dropdowns, Starts and Lasts, submitting the same values. Each end time is shown with how long that is from now (`StringUtils.getElapsedTime`).
2. Format the lock's start and end on the Lock Out page, and the end times in the admin events for cleared blocks and new locks, with `JiveGlobals.formatDateTime`.
3. Keep an account's lock state in one `LockOutManager` cache entry, a `CacheableOptional<LockOutFlag>` in a cache named "Account Lock States", in place of a second cache of accounts not locked.
4. Write the tier 3 block before removing its count, as tiers 1 and 2 do, so that `isLoginAllowed` never sees neither.
5. Rename `testPairCountSurvivesSprayOfJunkUsernames` for what it shows.

**Exit:** The full `i18n` and `xmppserver` suites pass.

---

### Session 18: Count usernames without an account apart

**Goal:** Stop a flood of made-up usernames from evicting a real user's count (third review). An attacker could make 9 guesses for a username from one address, try about 31,000 made-up usernames from the same address so that the tier 1 counts cache dropped the victim's count, and repeat: guesses limited only by how fast attempts can be sent, no block ever created, and every other user's tier 1 counts wiped by each flood.

**Tasks:**
1. Tests first: five rounds of 9 guesses and 40,000 made-up usernames admit only the limit of 10 guesses (45 before the change); the tier 3 equivalent with a flood larger than its cache; usernames without an account are counted apart and blocked as accounts are; a success, Clear login limits, clearing by address and the admin pages' queries work for both kinds; a lookup failure counts as an account's unless the username is invalid.
2. Add "User Login Failures per Unknown Username and IP Address" (5 MB) and "User Login Failures per Unknown Username" (20 MB) to `CacheFactory`, sized as their counterparts. `tryAcquireAttempt` looks the account up once (`UserManager`, before taking any lock) and counts tiers 1 and 3 in the matching caches; the reservation records the choice for the refund; reads, the admin pages and clearing look in both. Locks stay those of the accounts' counts caches. Tier 2 is unchanged.
3. Update ADR-014, the guide, the class Javadoc and this plan.

**Exit:** The full `xmppserver` suite passes.

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

- [x] On by default with only tier 1 active; with it disabled, nothing is counted or blocked
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

None. Review settled the default: on, with only tier 1 active.

---

### Session 19: Name of the lock cache

**Goal:** Let nodes of two versions share a cluster during a rolling upgrade.

**Tasks:**
1. Name the lock cache "Account Lock States" (same 10 MB size and 15-minute lifetime) and stop using "Locked Out Accounts", whose values in earlier versions are `LockOutFlag`s rather than `CacheableOptional<LockOutFlag>`s, so that old and new nodes never read each other's entries (`ClassCastException`). During an upgrade the two versions keep separate lock caches, so a lock or unlock on a node of one version can take up to 15 minutes to reach users on nodes of the other.
2. Update ADR-014.

**Exit:** The full `xmppserver` suite passes.
