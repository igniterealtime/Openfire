# ADR-008: Login Limits for User Accounts

## Status

Accepted

## Context

Openfire limits failed admin console logins through `org.jivesoftware.admin.LoginLimitManager`, but nothing limits failed XMPP logins, so a client can guess a user's password for as long as it likes. OF-2279 asks for the admin console's protection for ordinary users.

The admin console's limits count failures per address, per username and, since OF-3262, per (username, address) pair. OF-3262 added the pair because counting per address alone let one client lock out everyone behind a shared address. Each count is held in memory on one cluster node, and a timer clears all counts every 15 minutes.

Openfire can already lock an account through `LockOutManager`: an administrator sets a lock, optionally with start and end times, it is stored by the lockout provider (by default in the database), shown on the user's lockout page, and checked during authentication. Nothing sets a lock automatically.

Forces:

- **Who can be locked out.** A limit that locks an account from every address can be triggered deliberately by anyone who knows the username. The admin console accepts this for a handful of administrators; for every user on a server it is a denial-of-service vector.
- **Shared addresses.** Many genuine users can share one address (NAT, a reverse proxy), so a limit per address can affect people who did nothing wrong.
- **Distributed guessing.** A limit per address or per pair does nothing against guesses spread across many addresses.
- **Clustering.** Counts held per node let an attacker multiply their guesses by the number of nodes.
- **Visibility.** Administrators need to see and lift blocks that affect their users.
- **Answers that leak.** With a lock checked only after the credentials are verified, a locked account answers SCRAM and DIGEST-MD5 with `account-disabled` for a correct password and `not-authorized` for a wrong one, which tells an attacker when they have guessed right.
- **Volume.** XMPP clients log in far more often than administrators, and clients with a stale saved password retry repeatedly.

## Options Considered

### Option 1: Reuse the admin console's `LoginLimitManager`

Generalise the existing class with a separate set of counters and settings for XMPP logins.

**Consequences**:
- ✅ One implementation of the counting logic
- ✅ The same behaviour for the admin console and XMPP
- ❌ Counts stay per node, so a cluster multiplies an attacker's guesses
- ❌ The per-username block applies from every address, at a low limit, so anyone can lock any user out
- ❌ Blocks are invisible to administrators and cannot be lifted
- ❌ Changing it to fix these would change admin console behaviour, which was reworked in OF-3262

### Option 2: Account locks only

Count failures per username and, at a limit, lock the account through `LockOutManager`.

**Consequences**:
- ✅ Uses the existing lock: stored, shown on the user's lockout page, liftable by an administrator
- ✅ Simplest implementation
- ✅ Stops distributed guessing
- ❌ Anyone who knows a username can lock that user out from anywhere
- ❌ Does nothing about one address guessing across many accounts

### Option 3: Blocks scoped to their key only

Count per (username, address) pair, per address and per username in clustered caches; each limit blocks only its own key for a set time. No account locks.

**Consequences**:
- ✅ A pair block cannot be used to lock out a genuine user at another address
- ✅ Counts shared across the cluster
- ✅ No storage changes
- ❌ The per-username block still applies from every address, as in option 2, but is invisible to administrators and cannot be lifted
- ❌ Does not use the existing lock mechanism

### Option 4: Scoped blocks and an account lock, in three tiers (chosen)

Count per (username, address) pair and per address in clustered caches; each of these blocks only its own key for a set time. Separately count per username, at a higher limit and over a longer window; reaching it locks the account through `LockOutManager` for a set duration.

**Consequences**:
- ✅ The common case (one address guessing) is stopped by blocks that cannot lock out a genuine user elsewhere
- ✅ Distributed guessing is stopped by an account lock that administrators can see and lift on the existing page
- ✅ Counts shared across the cluster
- ✅ No storage changes: the blocks use caches, and the account lock uses the lockout provider
- ❌ The per-username lock can still be triggered deliberately once an administrator turns it on; it expires, and it can be turned off on its own
- ❌ The per-address block can affect genuine users behind a shared address once an administrator turns it on
- ❌ Pair and address blocks are lost when the whole cluster restarts (an attacker gets a fresh window)
- ❌ Two kinds of block to explain to administrators

## Decision

We will implement option 4 in a new `UserLoginLimitManager`, separate from the admin console's `LoginLimitManager`:

1. Tier 1 counts failures per (username, address) pair, default 10 in 15 minutes, and blocks that pair. Tier 2 counts per address, over 15 minutes by default, and blocks that address; it is off by default. Each block lasts its tier's block duration (default 15 minutes) from the attempt that reached the limit, whether or not the window has ended by then; counting for the key then starts again. Both are held in clustered caches.
2. Tier 3 counts per username, over an hour by default; it is off by default. The attempt after the limit blocks the username in the cache for 30 minutes and locks the account through `LockOutManager` for the same time. It leaves an existing lock alone. For a username with no account, an administrator's account, a lockout provider that is read-only or ignores end times, an account that already has a lock, or an error while locking, the cache block alone applies, so the answer is the same. An administrator's account is never locked, so that a deliberate lock-out cannot keep an administrator out of the admin console, which honours only `LockOutManager` locks; the cache block still refuses its XMPP and JMX logins. Reaching the limit restarts the count, and an administrator's unlock clears it.
3. An attempt is counted when it is admitted: one atomic step checks every tier and counts the attempt, so concurrent connections cannot each slip a guess past a limit. A successful login gives the attempt back to the window it was counted in, clears the tier 1 count for that pair, and never lifts a lock. Each window runs from the first counted attempt for its key.
4. A mechanism checks locks and reserves the attempt as soon as it knows the username, and refuses before evaluating the credentials: with `account-disabled` when the account is locked or tier 3 has blocked the username, and with `not-authorized`, the answer to a wrong password, for a tier 1 or tier 2 block. This applies to locks set by administrators too, whether or not the limits are enabled, and to every mechanism, FAST tokens included. The lock check after a successful exchange answers `account-disabled` where no password was checked (EXTERNAL); after a password exchange it answers `not-authorized`, as the password was right by then (the early check could not be completed, the lock was set during the exchange, or the authorization identity is another account). Over SCRAM a refused attempt runs the exchange with the salt and iteration count an admitted attempt would get, and fails at the final step. Refused attempts are not counted.
5. Attempts are counted for password mechanisms (PLAIN, SCRAM-*, DIGEST-MD5) and for JMX (tier 3 only, as JMX does not give the client's address). Not counted: EXTERNAL, ANONYMOUS, FAST tokens, the admin console (it has its own limits) and the administrative "Authenticate a user" command.
6. The security audit log gets one entry when a limit is crossed, not one per attempt.
7. The feature is on by default with only tier 1 active; tiers 2 and 3 have a limit of 0 (off) until an administrator sets one. Every limit, window, block duration and the lock duration is a dynamic system property, and a change to a tier 1 or tier 2 block duration applies to blocks already in force (a lock keeps the end it was set with); a limit of 0 turns its tier off.
8. A Login Limits page in the admin console sets the limits and lists the active tier 1, tier 2 and cache blocks, with a way to clear each.

## Rationale

Option 4 is the only option that stops both guessing from one address and guessing spread across many, without giving every attacker an easy way to lock out any user. Keeping the low, fast limits scoped to their key makes deliberate lock-outs hard; reserving the account lock for a higher limit keeps it for the attack it is needed against, and puts it where administrators already look for locks.

Tier 1 and 2 blocks last a set time from the attempt that reached the limit, so that every block lasts as long as the setting says. A block that lasted until its window ended would last almost the whole window for a fast guesser but only moments for one who paced attempts to reach the limit near the window's end, after which a fresh window allowed the full limit again.

Pair and address blocks live in caches because the lockout provider stores locks per account only; storing address blocks would need a new table and upgrade scripts for every supported database, for state that lasts minutes and is cheap to lose.

A separate class leaves the admin console's behaviour unchanged. Its limits could adopt this design later in a change of its own.

Refusing before evaluating the credentials, with an answer that does not depend on the password, removes the signal an attacker would otherwise get from a locked account. Answering a locked account `account-disabled` gives a genuine user who is locked out a clear reason, where `not-authorized` would look like a wrong password; the cost, that anyone who knows a username can learn that its account is locked, is accepted. Logging only when a limit is crossed keeps the audit log usable on a busy server.

On by default with only tier 1 active, because a tier 1 block refuses no one but the pair that reached it, so it hardly ever affects anyone else, and it stops the cheapest attack, one machine guessing as fast as it can, on every installation. Tier 2 can shut out everyone behind a NAT, a reverse proxy or a connection manager (a few clients retrying an old password after a password change are enough), and tier 3 lets anyone who knows a username keep that account locked from a handful of addresses; those are better turned on by administrators who know their network.

## Consequences

- Every installation gets tier 1 protection for XMPP logins without any setting, including on upgrade: a client that fails 10 times in 15 minutes for one username from one address is refused that username from that address for 15 minutes. Administrators can turn on tiers 2 and 3, tune each tier, or turn the limits off.
- JMX logins count towards tier 3 only, so they are not limited until an administrator turns tier 3 on.
- Tier 3 locks appear on the user's lockout page, which says that the login limits set them, and can be lifted there; tier 1, tier 2 and cache blocks appear on the Login Limits page. A username that tier 3 blocks without a lock (an administrator's) is shown on its lockout page as not locked but refused by the login limits, with when that ends.
- An administrator's account that reaches tier 3 is blocked in the cache rather than locked, so a deliberate lock-out cannot keep an administrator out of the admin console; their XMPP and JMX logins are still refused while the block lasts.
- A locked account now answers `account-disabled` whatever the password, for every mechanism, whether or not the limits are enabled. Before, SCRAM and DIGEST-MD5 answered `account-disabled` to the correct password only, and `not-authorized` to a wrong one; PLAIN and FAST tokens answered `not-authorized`. Anyone who knows a username can learn, without its password, whether its account is locked.
- A username blocked by tier 3 is answered `account-disabled` whether or not its account could be locked, so the answer does not show whether the account exists while the block lasts. The lock of an existing account can outlast the block, which lives in a cache (lost when the whole cluster restarts, evicted under a large attack, or ignored once tier 3 or the limits are turned off); for the rest of the lock, an existing account answers `account-disabled` and an unknown username does not. An administrator whose username tier 3 has blocked is answered `account-disabled` over XMPP, while their Lock Out page shows no lock and the admin console stays open to them.
- Over SCRAM a refused attempt sends the same salt and iteration count as an admitted one (the account's own for an existing account, a generated salt for an unknown user) and fails at the final step, so a client cannot see a tier 1 or tier 2 block before that step, or learn from a refusal whether the account exists. It looks the salt up as an admitted attempt does, so where an account has no stored SCRAM salt but a password Openfire can read, the salt is created, as any SCRAM attempt on main does. It never looks up stored keys or verifies a proof, so it is answered sooner than a wrong password: a client can tell from the timing that it is blocked, though not whether the account exists.
- A client that disconnects mid-exchange keeps its attempt counted, so a genuine client on an unreliable network uses up attempts.
- With the limits enabled, each password attempt takes up to three cluster locks when it is admitted: one for its (username, address) pair and one for its address, each only while that tier is on, and one for its username whenever the username can name an account, even with tier 3 off, as that lock also guards the username's block. A successful attempt takes up to three more to give its attempt back: the pair, and the address and username counts it was counted in.
- With the limits off, an XMPP login makes the same number of lockout lookups as before: the check after a successful exchange reuses the early check from the same attempt, so a lock set while an exchange is in progress applies from the next login. A JMX login makes one lookup more than before.
- Behind a reverse proxy, every client appears to come from the proxy's address; tier 2 should be raised or turned off there. BOSH can take the client's address from forwarding headers (`httpbind.forwarded.enabled`), but unless `httpbind.forwarded.trusted.proxies` is set, any client can send those headers and choose the address that tiers 1 and 2 count. WebSocket sessions always report the address of the connection to Openfire.
- IPv6 clients are counted by /64 prefix for tiers 1 and 2, so clients sharing a /64 share those counts.
- The per-username caches are keyed by a digest and sized so that spraying junk usernames cannot cheaply evict a victim's count. The caches remain bounded, so a large enough distributed attack can still evict counts; tier 3 locks already set through `LockOutManager` are stored by the lockout provider and are not affected.
- The admin console's `LoginLimitManager` keeps its current behaviour (per node, timer-cleared, per-attempt audit entries). Aligning it with this design is possible follow-up work.
- Turning the limits off lifts their blocks (not account locks), but does not delete them: turning the limits on again before a block would have ended brings it back.
- A SCRAM attempt for a name containing '@' (such as `alice@domain`) is refused without being counted: Openfire looks SCRAM credentials up under the name as sent, and no account has an '@' in its name, so it cannot succeed, and counting it against `alice` would let a misconfigured client block or lock the account with the right password.
- Plugins that authenticate users through `AuthFactory.authenticate` (for example REST APIs) are not counted; they could call `UserLoginLimitManager` in a later change.
