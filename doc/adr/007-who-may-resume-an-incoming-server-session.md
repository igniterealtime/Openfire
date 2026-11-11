# ADR-007: Who May Resume an Incoming Server Session

## Status

Accepted

## Context

OF-2425 adds Stream Management (XEP-0198) to server-to-server connections, including resumption. When a remote server's connection to Openfire breaks, Openfire keeps the incoming server session for a while, so that the remote server can reconnect and resume the stream with `<resume previd='…'/>`. On success, the kept session takes over the new connection, with everything that was authenticated on it: the remote server can then send stanzas from every domain that was validated on the kept session, without authenticating them again on the new connection (see ADR-008).

Openfire therefore has to make sure that the new connection belongs to the same remote server as the session that it resumes. XEP-0198 (version 1.6.3) only requires that resumption happens after authentication. § 10 (Security Considerations) says:

> a server MUST NOT allow a client to resume a stream management session until after the client has authenticated (for some value of "authentication"); this helps to prevent session hijacking.

It does not say how a server checks that the entity that resumes is the one that owned the session. For clients, Openfire looks up the session to resume by the authenticated user of the new connection and the resource in the SM-ID, so a client can only resume a session of its own user. For server sessions, the SM-ID is the Base64 encoding of the stream ID of the kept session, which does not name the remote server.

Several facts about server sessions shape the options:

- A remote server authenticates with a certificate (`SASL_EXTERNAL`) or with Server Dialback (`DIALBACK`). Dialback relies on DNS, and can be passed by someone who can spoof DNS. A certificate is the stronger proof. A SASL mechanism other than EXTERNAL is recorded as `OTHER`.
- The domain that a remote server claims in its stream header ('from') is not authenticated. The domain that it authenticates can differ from it.
- With dialback, several domains can be authenticated on one connection. Each dialback for an extra domain sets the authentication method of the session to `DIALBACK`, also on a session that was authenticated with a certificate. The authentication method of the session is also cleared when its connection closes. Neither is therefore a reliable record of how the session was first authenticated.
- The answer to a resume request should not reveal to a remote server that may not resume a session whether that session exists.

This ADR applies to incoming server sessions, where Openfire answers the resume request. On connections that Openfire opens, the remote server decides whether Openfire may resume, and Openfire authenticates the new connection with the same TLS and authentication policy as any new connection.

## Options Considered

### Option 1: Any Authenticated Session May Resume

**Description**: Accept a resume request from any session on which at least one domain is authenticated, as for a client that has authenticated.

**Consequences**:

- ✅ Simplest implementation, no record of how a session was authenticated
- ✅ Meets the requirement of XEP-0198 § 10
- ❌ Any remote server that can authenticate any domain can take over the session of another remote server, if it obtains its SM-ID, and send stanzas from that server's domains

### Option 2: Same Domain, Compared With the Domain in the Stream Header

**Description**: Require that the domain that the new connection claims in its stream header is the same as the one that the kept session claimed.

**Consequences**:

- ✅ Simple: the stream header is always available
- ❌ The domain in the stream header is not authenticated, so the check proves nothing about who connected

### Option 3: Same Authenticated Domain, Any Authentication Method

**Description**: Require that the first domain authenticated on the new connection is the same as the first domain authenticated on the kept session, whatever method was used on either.

**Consequences**:

- ✅ Only the same remote server can resume
- ✅ A remote server can always resume, whatever method it can use on the new connection
- ❌ Allows a downgrade: someone who can pass dialback for a domain, for example by spoofing DNS, can take over a session that the real server authenticated with a certificate

### Option 4: Same Authenticated Domain, Exactly the Same Method

**Description**: As option 3, but the authentication method must also be the same.

**Consequences**:

- ✅ Prevents the downgrade of option 3
- ❌ Also refuses a remote server that authenticated with dialback before and now has a certificate that validates, without any security gain
- ❌ `SASL_EXTERNAL` and `DIALBACK` are compared as unrelated values, while one is stronger than the other

### Option 5: Same Authenticated Domain, Same or Stronger Method (CHOSEN)

**Description**: As option 3, but the method with which the first domain was authenticated on the new connection must be the same as, or stronger than, the method with which the first domain was authenticated on the kept session. A certificate (`SASL_EXTERNAL`) is stronger than `DIALBACK`.

**Consequences**:

- ✅ Prevents the downgrade of option 3
- ✅ Allows a remote server to move from dialback to a certificate
- ✅ Uses what was authenticated, not what was claimed
- ❌ Requires a record of the first authenticated domain and its method, kept separately from the authentication method of the session
- ❌ A remote server whose certificate stopped validating, and that falls back to dialback, cannot resume, and starts a new stream instead

## Decision

We will implement **Option 5: Same authenticated domain, same or stronger method**.

Specifically:

- **What is compared**: each incoming server session records the first domain that is validated for it, and the method with which that domain was authenticated (`LocalIncomingServerSession#getFirstAuthentication()`). The record is set once per session. It does not change when that domain is removed, when other domains are validated, possibly with another method, or when the session is resumed: a resumed session keeps its own record, so a later resumption of it is compared with the method of its original connection.
- **Domain**: the two domains are compared after the normalisation that `JID` applies to a domain (`JID.domainprep`), so that their case does not matter. A domain that is not a valid domain name matches no domain. The domain that the remote server claims in its stream header is not used.
- **Method**: a method is accepted when it is the same as the method of the kept session, or when it is `SASL_EXTERNAL` and that of the kept session is `DIALBACK`. `OTHER` is only the same as itself, and is not ranked against the other methods. An unknown method (none recorded) on either session is never accepted.
- **Refusal**: a resume request that fails the check is answered with `<failed/>` with `<item-not-found/>`, without an 'h' attribute. That is the same answer as for a 'previd' that cannot be decoded, that names no incoming server session on this cluster node, or that names the requesting session itself. The check is made while the session is looked up (`ServerResumption#locate`, which calls `ServerResumption#mayTakeOver`), before the checks that answer with `<unexpected-request/>`, such as for a session that is closed or not resumable. The answer thereby does not reveal whether a session exists for a 'previd' that the remote server may not resume.
- **Access policy**: the check does not consult the access policy for remote servers. Blocking a remote domain, or changing the policy so that it is no longer allowed, makes the sessions of that domain non-resumable and closes them, and ends those that are kept, so they cannot be resumed.

## Rationale

Resumption hands over more than a counter: with ADR-008, the domains that were validated on the kept session stay valid on the new connection without being authenticated there again. The check must therefore be at least as strong as the authentication that the kept session relied on.

Comparing the first authenticated domain, rather than the stream header, makes the check rest on what the remote server proved. Recording it once, apart from the authentication method of the session, keeps it correct when dialback for an extra domain overwrites that method, and when the method is cleared as the connection closes.

Allowing a stronger method, and not only the same one, avoids refusing a remote server for improving its authentication, without opening a path for a weaker one. `OTHER` and an unknown method have no defined place in that order, so they are only accepted where nothing has to be ranked, or not at all.

Answering a refusal exactly as for an unknown 'previd' follows XEP-0198 § 5, which says that a server that "does not recognize the 'previd' as an earlier session (e.g., because the former session has timed out), it MUST return a `<failed/>` element, which SHOULD include an error condition of `<item-not-found/>`". To a remote server that may not resume it, the session is not one that it can refer to.

## Consequences

### What Becomes Easier

- ADR-008 can keep all validated domains of a resumed incoming session, as the new connection is known to belong to the same remote server
- A remote server can switch from dialback to a certificate and still resume
- The check is in one place (`ServerResumption#mayTakeOver`), so it can be tightened or relaxed there

### What Becomes Harder

- A remote server whose certificate stopped validating starts a new stream instead of resuming. For stanzas that it sends to Openfire, this is the remote server's concern: it holds the unacknowledged ones
- Diagnosing a refused resumption needs Openfire's debug log, as the remote server receives the same answer as for an unknown 'previd'

### Constraints

- A resumed session keeps the record of its original connection. A session that was authenticated with dialback, and was then resumed on a connection that used a certificate, can later be resumed again with dialback
- If this decision is relaxed, ADR-008 must be revisited, as it relies on it

## References

- **OF-2425**: Stream Management for server-to-server connections (engineering plan: `doc/plan/2026-10-23-stream-management-s2s.md`, decision 1)
- **ADR-008**: Extra Domains After Resuming a Server Connection
- **XEP-0198**: Stream Management, version 1.6.3, § 5 (Resumption) and § 10 (Security Considerations)
- **XEP-0220**: Server Dialback
