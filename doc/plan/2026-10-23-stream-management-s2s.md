---
generated_by: Claude Opus 5.5
generation_date: 2026-10-23
model_version: claude-opus-5-5
purpose: implementation_plan
status: draft
human_reviewer: guusdk
session_status:
  session_1_characterise_acks: completed (2026-10-30)
  session_2_characterise_resumption: completed (2026-11-04)
  session_3_rename_inbound_outbound: completed (2026-11-04)
  session_4_extract_unacked_queue: completed (2026-11-04)
  session_5_isolate_client_resumption: completed (2026-11-04)
  session_5b_separate_negotiation: completed (2026-11-06)
  session_5c_reorganise_tests: completed (2026-11-06)
  session_6_server_settings: replay after session 5b (first completed 2026-11-06)
  session_7_advertise_incoming: rework after session 5b (first completed 2026-11-06)
  session_8_incoming_ack_tests: replay after session 5b (first completed 2026-11-06)
  session_9_outgoing_negotiation: rework after session 5b (first completed 2026-11-06)
  session_10_outgoing_gate: not-started
  session_11_outgoing_tracking: not-started
  session_12_classify_unacked: not-started
  session_13_retry_limit: not-started
  session_14_detach_timeout_per_type: not-started
  session_15_detach_incoming_server: not-started
  session_16_offer_resume_incoming: not-started
  session_17_validate_resumer: not-started
  session_18_extra_domains_incoming: not-started
  session_19_cluster_hint: not-started
  session_20_keep_outgoing_route: not-started
  session_21_single_outgoing_connection: not-started
  session_22_resume_outgoing: not-started
  session_23_extra_domains_outgoing: not-started
  session_24_give_up_resumption: not-started
  session_25_admin_console: not-started
  session_26_documentation: not-started
  session_27_adrs: not-started
  session_28_cleanup: not-started
tags: [stream-management, xep-0198, s2s, federation]
tickets: [OF-2425]
---

# OF-2425: Stream Management for server-to-server connections

## Summary

### What changes

When the connection between Openfire and another chat server breaks, messages that were on their way can currently disappear without anyone noticing. After this work, both servers keep track of what has arrived. After a break, they pick up where they left off. Where that is not possible, Openfire sends the missing messages again, and reports requests that cannot safely be repeated back to the sender as failed.

Administrators get separate settings for this, and the admin console shows for each server connection whether it is active.

### How it works

On a connection between two servers, the server that opened the connection sends the stanzas, and the server that accepted it regularly reports how many it has handled. The sending server keeps a copy of everything that has not been confirmed yet.

When the connection breaks, the sending server opens a new connection and asks to continue the old one. The other server reports how far it got, and only the stanzas after that point are sent again. If continuing is not possible, the sending server decides per stanza whether it is safe to send it again or whether the sender should be told that delivery failed.

Openfire already does this for clients, but there it only ever plays the accepting role. For connections it opens to other servers, Openfire takes the role that clients have today, which is new code.

### For developers

This adds XEP-0198 to incoming and outgoing server sessions, building on the existing client implementation in `StreamManager`. The existing implementation assumes it serves a client (for example in how it identifies a session to resume), uses client/server naming that will be wrong once Openfire also takes the client's role, and has little test coverage. Phase 1 adds tests and refactors it without changing behaviour.

Incoming server sessions (`LocalIncomingServerSession`) reuse the responding role. Outgoing server sessions (`LocalOutgoingServerSession`, negotiated through `RespondingServerStanzaHandler`) get new code to enable Stream Management, request acknowledgements and resume. Resumption reuses the existing detach/reattach mechanism in `LocalSession` and `SessionManager`.

All new settings are disabled by default until phase 7 sets their final defaults, so that a release taken between phases does not change behaviour.

Out of scope: bidirectional server connections (XEP-0288). The code must not assume that the counters for the unused direction are always zero, but otherwise does not prepare for them.

Prerequisites: OF-3433, OF-3434, OF-3442 and OF-3443 fix bugs in the existing implementation (see Blocking Issues), and were required before phases 2, 3, 4 and 5 respectively. They are merged. OF-3420 fixes thread-safety problems in the existing implementation and must still be merged before phase 5.

## Background

Openfire currently supports Stream Management for client connections only. In that setup, the client always starts Stream Management: it enables it and requests resumption, while Openfire only responds. Openfire has never taken the client's role, and the existing implementation contains no code for it.

On incoming server connections, where a remote server sends stanzas to Openfire, Openfire would have the same responding role as with clients. The existing implementation already handles most of the protocol there (`StanzaHandler` passes Stream Management elements to `StreamManager` for every session type), but Openfire does not advertise support for it. Outgoing server connections have no support at all. As a result, when an outgoing connection breaks today, Openfire loses the stanzas that the remote server had not yet received.

For clients, a broken session is detached by the client session close listener in `SessionManager` and cleaned up after `xmpp.session.detach.timeout` (default 10 minutes). When a client session closes without being resumed, `StreamManager#onClose` routes unacknowledged messages again, answers unacknowledged IQ requests with a service-unavailable error, and discards all other unacknowledged stanzas (OF-3442).

Openfire currently sends stanzas over a server connection in one direction only: from the server that opened the connection to the server that accepted it. Other traffic on that connection, such as dialback results, already goes both ways. Openfire does not support bidirectional server connections (XEP-0288), where stanzas flow both ways.

Resumption serves a different purpose on server connections than on client connections. XEP-0198 motivates it as a way to avoid "the tedious process of stream establishment, roster retrieval, and presence broadcast". A client that does not resume has to bind a resource and retrieve its roster again, and its contacts see it go offline and come back. None of this applies to a server connection: after authentication there is nothing to restore, and closing the session only removes its routes. Resumption does not make reconnecting faster either. It happens after authentication, so TLS and authentication are repeated regardless, and `<resume/>` takes the same single round trip as `<enable/>`. The only exception is that a remote server does not need to repeat dialback for extra domains on an incoming connection (decision 2); on outgoing connections, Openfire does repeat it. The reason to resume a server connection is accuracy: the remote server reports exactly how far it got. Stanzas it received are then not duplicated or reported as failed, and IQ requests it did not receive can be sent instead of being returned as errors (decision 4).

## Design Decisions

Decisions 1, 2 and 4 settle questions that XEP-0198 leaves open. They are recorded as ADRs in session 27.

### 1. Who may resume a session

Resumption hands an existing session, including its unacknowledged stanzas and authenticated domains, to a new connection. Openfire has to make sure that the new connection belongs to the same remote server. XEP-0198 only requires that resumption happens after authentication.

**Decision:** A session can only be resumed by the same authenticated domain, using the same or a stronger authentication method (a certificate, `SASL_EXTERNAL`, is stronger than `DIALBACK`). The domain compared is the first one authenticated on the original connection, not the one the remote server claims in its stream header.

**Rationale:** This prevents someone who can pass dialback, for example by spoofing DNS, from taking over a session that was authenticated with a certificate. The cost is that a remote server whose certificate stopped validating starts a new stream instead of resuming.

**Rejected:**
- Same domain, any authentication method: allows the downgrade described above.
- Same domain, exactly the same method: also refuses a switch from dialback to certificate, without any security gain.

### 2. Extra domains on a resumed connection

With dialback, several domains can be authenticated on one connection. After resuming, only the main domain is authenticated again on the new connection. XEP-0198 does not say what happens to the others.

**Decision:** Accept liberally on incoming connections, send conservatively on outgoing connections.

- Incoming: all domains authenticated on the original connection remain valid after resumption. If the remote server authenticates one of them again, Openfire accepts that.
- Outgoing: after resuming, Openfire authenticates each extra domain again with dialback before it sends stanzas for that domain. Stanzas for an extra domain, including unacknowledged ones that need to be sent again, wait until that domain is verified. If verification fails, the domain's unacknowledged stanzas are handled as in decision 4, and its other waiting stanzas are sent over a new connection.

**Rationale:** On incoming connections, the extra domains were authenticated for this same remote server, and decision 1 makes sure that the new connection belongs to it. This depends on decision 1: if decision 1 is relaxed, this decision must be revisited. On outgoing connections, Openfire cannot know whether the remote server keeps extra domains after resumption, and a stanza from a domain the remote server no longer accepts would typically close the connection. The cost is an extra dialback round trip per extra domain after an outgoing resumption.

**Rejected:**
- Refuse resumption if the session has extra domains: remote servers that host several domains would never benefit from resumption.
- Resume, but keep only the main domain (incoming): the remote server's next stanza from another domain would cause an error that closes the connection.
- Assume the remote server keeps all domains (outgoing): if it does not, the first stanza for such a domain closes the resumed connection. Its stanzas then go through decision 4 a second time, and their one retry is spent on a connection that was going to fail.

### 3. When a new outgoing connection may be used

Openfire starts sending stanzas on a new outgoing connection as soon as authentication completes. Stream Management is negotiated after that, and stanzas sent before it is enabled are not covered.

**Decision:** A new outgoing connection is not used until negotiation has finished: Stream Management is enabled, refused, not offered, or the negotiation timed out.

**Rationale:** Every stanza is then covered. The cost is one extra round trip, which is small compared to setting up TLS and authentication. Remote servers that don't offer Stream Management are not delayed.

**Rejected:**
- Negotiate in the background: the first stanzas on every connection would not be covered.

### 4. Unacknowledged stanzas when a stream is not resumed

When a connection that uses Stream Management breaks and the stream is not resumed (resumption is not enabled, the attempt fails, or the time to resume runs out), Openfire holds stanzas that the remote server has not acknowledged, but cannot know whether they were received. Sending them again can create duplicates. Returning an error can report a failure for a stanza that did arrive. XEP-0198 recommends treating them like stanzas for a recipient that is unavailable. Connections without Stream Management are not affected: Openfire does not track their stanzas, so it has nothing to act on.

**Decision:** Messages, presences and IQ responses (result/error) are sent again. IQ requests (get/set) are returned to the sender as an error. Each stanza is sent again at most once, whether on a resumed stream or a new connection. If it is still unacknowledged when that connection breaks too, it is returned to the sender as an error.

**Rationale:** This avoids losing messages without repeating side effects. Duplicates only occur where they are harmless, or can be recognised by their ID. The limit prevents a stanza that makes the remote server close the connection (for example because of a policy violation) from being sent again on every new connection, breaking each one in turn. The cost is that two breaks in quick succession turn a stanza that was probably delivered into an error.

**Rejected:**
- Send everything again: repeated IQ requests can repeat side effects, such as publishing an item twice.
- Return everything as an error: a brief network interruption would produce many errors, mostly for stanzas that were probably delivered.
- No limit on sending again: a stanza that breaks the connection does so forever.

### 5. How long a broken session is kept

While a broken session is kept, its unacknowledged stanzas wait. Today a single setting (`xmpp.session.detach.timeout`, 10 minutes) applies to all broken sessions, a length that suits clients, which may take a while to reconnect.

**Decision:** Server sessions get their own, shorter timeout, defaulting to 1 or 2 minutes. The cleanup code uses a timeout per session type.

**Rationale:** Servers reconnect within seconds, and a shorter timeout makes Openfire fall back sooner. The cost is a second setting to document.

**Rejected:**
- Reuse the existing setting: stanzas would wait up to 10 minutes before being resent or returned, and server and client sessions couldn't be tuned separately.

### 6. Resumption in a cluster

A session lives on one cluster node. For outgoing connections this is not a problem, because Openfire reconnects from that node. A remote server reconnecting to resume an incoming connection, however, may reach a node that does not have the session.

**Decision:** Resumption only works on the node that holds the session. Openfire tells the remote server which node to reconnect to, as it already does for clients (the `location` attribute, controlled by `stream.management.location.enabled`). If that node can't be reached, the remote server falls back to a normal connection.

**Rationale:** This takes little effort and is consistent with clients. Whether remote servers follow the hint needs to be tested.

**Rejected:**
- Same node only, without a hint: resumption would fail whenever the remote server reaches another node.
- Move session state between nodes: a large effort, which Openfire does not make for clients either.

### 7. Routing while a broken outgoing session waits to resume

When an outgoing connection breaks and resumption is enabled, new stanzas for that remote domain keep arriving while Openfire reconnects.

**Decision:** The broken session stays the route for its domains. New stanzas are queued on it, behind the unacknowledged ones, and other cluster nodes keep routing to the node that holds it. Openfire does not open a second connection to the same remote domain while it tries to resume. When resumption succeeds, Openfire first sends the stanzas the remote server reports as not received, then the queued ones, in order. When Openfire gives up, the unacknowledged stanzas are handled as in decision 4, and the queued stanzas, which were never sent, go out over a new connection. The queue has a maximum size. When it is full, Openfire gives up on resumption immediately and falls back as above.

**Rationale:** One route per domain keeps stanzas in order, and nothing has to be reconciled after a successful resumption. The cost is that new stanzas wait for the duration of the resumption attempt.

**Rejected:**
- Open a new connection straight away and send new stanzas there: two routes for the same domain would break stanza order, and a successful resumption would then have to be reconciled with the other connection.
- Return new stanzas to the sender while waiting: a brief interruption would produce errors for stanzas that could simply have waited.
- Drop or return new stanzas when the queue is full: loses or rejects stanzas that a new connection could deliver.

### 8. Compatibility of public methods for plugins

Plugins, including third-party ones, can call the public methods of `StreamManager`, `LocalSession`, `SessionManager` and the session classes. Several sessions rename, move or change such methods: session 3 renames, session 4 moves the queue of unacknowledged stanzas (including the public class `StreamManager.UnackedPacket`), and session 5 reorganises resumption.

**Decision:** A public or protected member that is renamed, moved or given another signature keeps its old signature as a delegate to the new one, marked `@Deprecated(forRemoval = true, since = "<release>")` with a `// Remove in or after Openfire <release>` comment, as elsewhere in Openfire. `since` is the release that ships the change, and removal is in or after the next minor release. Phases can be released separately, so these versions follow the release of each phase, not the order of the sessions. The Javadoc of the delegate names its replacement. A member that is only meant to be called as part of Openfire's own processing gets a delegate as well, and its Javadoc describes that. Where the old signature cannot keep its meaning, for example because the behaviour of the method changes, there is no delegate: the change is listed as an API change in the pull request and in the changelog.

**Rationale:** A plugin that calls a renamed or moved method keeps working, binary-compatible, until the removal release, and its developers get a compiler warning in the meantime. Delegates are cheap to add and to remove. A delegate that behaves differently from the old method would hide a change that its callers need to know about, which is why there is none in that case.

**Rejected:**
- Change public methods without delegates: breaks plugins without warning, while their use cannot be checked for third-party plugins.
- Keep the old names permanently: client and server naming would remain in the API, which is what session 3 removes.


### 9. One stream manager class for each end of a stream

Openfire answers requests for Stream Management on most sessions, and asks for it on connections that it opens to other servers (session 9). The two ends process different elements: the end that answers handles `<enable/>` and `<resume/>`, the end that asks handles `<enabled/>`, `<failed/>` and `<resumed/>`. They also handle unacknowledged stanzas differently when a session closes without being resumed: as for clients at the end that answers (OF-3442), and as decision 4 describes at the end that asks. Acknowledgements, the counters and the queue of unacknowledged stanzas are the same at both ends. Which end Openfire is on is fixed when a session is created.

**Decision:** `StreamManager` is an abstract base class for what both ends share, with a subclass for each end: `AnsweringStreamManager` (session 5b) and `RequestingStreamManager` (session 9). `StreamManager.forSession(LocalSession)` creates the subclass that matches the type of a session, and session classes that are tied to one end override `getStreamManager()` to return that subclass. What a stream manager does when its session is resumed (`onResume`) and when its session closes without being resumed (`onClose`) are abstract methods of the base class, which each end implements. On resumption, the end that answers sends `<resumed/>` and then the unacknowledged stanzas, and the end that asks only sends the unacknowledged stanzas (session 22). On close, the end that answers handles unacknowledged stanzas as for clients, and the end that asks as decision 4 describes (session 12). The state that both ends share is private to the base class, and subclasses change it through package-private methods. The public constructor of `StreamManager` becomes protected (decision 8: an abstract class cannot keep it), which is listed as an API change.

**Rationale:** This separates the concerns of the code: the base class holds what both ends share, and each subclass the protocol of one end. Code for one end can be read, changed and tested without the other, and what one end refuses follows from its class, rather than from checks spread through shared code. This keeps the code maintainable as the end that asks grows in phases 3, 4 and 6, while the end that answers keeps serving clients. In addition, methods for one end exist only on its subclass, so the compiler checks callers that hold the type of the session or of the subclass, instead of a check at runtime; a caller that holds only `LocalSession` still needs a cast. The moved code keeps locking on its own instance, and `LocalSession` can complete a resumption without knowing which end it is on. The cost is the API change of the constructor, which only affects plugins that create a stream manager themselves, as Openfire creates them only in `LocalSession`. Until session 22, the asking end cannot be resumed, so its `onResume` refuses. Until session 12, its `onClose` only disables Stream Management, as no unacknowledged stanzas were handled on these connections before.

**Rejected:**
- Classes for the negotiation, held by `StreamManager` and selected per session (the first version of session 5b): keeps the public constructor, but methods for one end stay on `StreamManager` and refuse at runtime, and the negotiation classes need write access to the state of `StreamManager`.
- A concrete `StreamManager` that answers, with a subclass for the end that asks: keeps the public constructor, but the asking subclass inherits answering methods that it has to override to refuse, and the base class is not neutral.
- `onResume` and `onClose` kept in the base class until sessions 22 and 12 need another implementation: smallest change now, but the base class keeps code of one end, and those sessions have to move it. Moving them to `AnsweringStreamManager` instead would need casts where `LocalSession` and `SessionManager` call them on the base type.
---

## Blocking Issues

Session 1, and the work on the fixes for what it found, exposed bugs in the existing implementation. Stream Management on server connections builds on this code, so these bugs must be fixed before the phases below can start. The issues change the behaviour of client connections, so they are separate pull requests and not part of phase 1, which does not change behaviour.

All four issues are merged, and this branch was rebased on them on 2026-11-06.

### OF-3433: Handling of 'h'. Must be merged before phase 2

Phase 2 lets remote servers send Stream Management elements to Openfire, and phase 3 and later phases rely on the numbering, on acknowledgements and on `<resumed/>`. This issue covers:

- Stanza numbers do not roll over at 2^32 (XEP-0198 § 4). As a result, an `<a/>` that has rolled over also removes stanzas that it does not cover, which are then not sent again. The peer's next valid `<a/>` can close the session, and an `<a/>` for a stanza that was never sent goes undetected at that point (§ 6).
- The `h` value in `<resumed/>` does not roll over (§ 4 and § 5).
- A stream error for a value of `h` that is too high does not include `<handled-count-too-high/>` with its `h` and `send-count` attributes (§ 6).
- An `<a/>` with a value of `h` above 2^32-1, which is not a legal xs:unsignedInt (§ 13), throws `IllegalArgumentException` instead of closing the stream with a stream error.
- An `<a/>` with a value of `h` lower than an earlier one closes the session when no stanzas are unacknowledged. XEP-0198 does not define this as an error: an `<a/>` only indicates that new stanzas were processed if `h` has been incremented (§ 4), and § 6 only calls for closing the stream when `h` is too high. It must not be treated as an error, and must not affect the numbers of later stanzas.

### OF-3434: Exceeding the maximum number of unacknowledged stanzas. Must be merged before phase 3

Session 11 starts tracking stanzas on outgoing connections, which makes this reachable on server connections, and decision 4 assumes that Openfire still holds unacknowledged stanzas when a connection breaks. Exceeding `stream.management.max-unacked` disables Stream Management without telling the peer, and discards all unacknowledged stanzas. From then on, `<r/>` from the peer goes unanswered, while `StreamManager#getResume` keeps returning `true`.

XEP-0198 defines no such limit, and no way to disable Stream Management on an open stream. It does state that an unacknowledged stanza remains the responsibility of the sender, that a server should treat it as a stanza for an unavailable resource when the stream ends (§ 4), and that the response to `<r/>` must not be withheld for any condition other than a timeout (§ 4). What Openfire does when the limit is reached is therefore a decision for this issue, within those requirements.

### OF-3442: Unacknowledged stanzas when a session closes. Must be merged before phase 4

Phase 4 implements decision 4 for outgoing connections, next to the code that handles the unacknowledged stanzas of a client session that closes without being resumed. That code, `StreamManager#onClose`, routes unacknowledged messages again and discards all other unacknowledged stanzas. An unacknowledged IQ request is then never answered, while RFC 6120 § 8.2.3 requires a response to every IQ of type get or set, so its sender waits until its own timeout expires. The messages are routed while the closing session is still registered, so they are routed back to that session, and from there always go to offline storage, even when the user has another resource online. They also send carbons again and run the outgoing interceptors again.

XEP-0198 § 4 states that a server should treat unacknowledged stanzas as stanzas for an unavailable resource. For an IQ request, RFC 6121 § 8.5.3.2.3 defines that as returning a service-unavailable error to the sender, which is also what decision 4 does for IQ requests.

### OF-3443: Closing a stream with a stream error. Must be merged before phase 5a

Phase 5a keeps broken incoming server sessions that are resumable, in the same way as the close listener for client sessions does. When `StreamManager` closes a session because of a Stream Management error, such as an `<a/>` with an invalid value of `h` or a malformed `<resume/>`, it delivers the stream error and closes the session in a way that leaves resumption enabled. The close listener then detaches the session instead of ending it, and the peer can resume a stream that was closed because of an error, which OF-2751 is meant to prevent. RFC 6120 § 4.9.1.1 defines stream errors as unrecoverable.

### Tests

Each issue was fixed on its own branch, which held the fix and only the tests for that issue. The branches for OF-3433, OF-3442 and OF-3443 started from `main`, and the branch for OF-3434 built on the one for OF-3433.

Until the fixes were merged, the tests on this branch that failed because of these issues were annotated with `@Disabled` and the number of the issue, so that the build kept passing. When this branch was rebased on the merged fixes, the tests from `main` replaced the copies of the same tests in `StreamManagerTest`, and the other `@Disabled` annotations were removed. Three tests for exceeding the maximum number of unacknowledged stanzas were dropped, as OF-3434 closes the stream instead, and has its own tests for that.

Code in `StreamManager` that closes its own stream with a stream error makes that stream non-resumable (OF-3443). Most of it uses `Session#close(StreamError)`, which does this itself. `StreamManager#closeForExceedingMaximum` calls `formalClose()` and then `Connection#close(StreamError)` (OF-3434). After the rebase, `StreamManager` has no place left that delivers a stream error with `deliverRawText()` and then calls `close()`.

These issues touch the same code as sessions 3 and 4, which were adapted to the fixes when this branch was rebased: session 3 renames this code and session 4 moves it.

---

## Implementation Sessions

Sessions are grouped into phases. Each phase is one pull request that leaves Openfire in a working state; each session is one commit within it. Phases 5 and 6 are split into sub-phases (5a-5c, 6a-6c), each its own pull request.

Paths below use `main/` for `xmppserver/src/main/java/org/jivesoftware/openfire/` and `test/` for `xmppserver/src/test/java/org/jivesoftware/openfire/`. Class names marked "proposed" are suggestions; use a better name if the code suggests one.

Every session that renames, moves or changes a public or protected member keeps its old signature as a deprecated delegate, as described in decision 8.

Tests are unit tests that use Mockito, `Fixtures` and mocked connections, following `SessionManagerCloseListenerRouteOwnershipTest` and `StanzaHandlerClaimedIdentityTest`. Do not use or extend the remote server dummies (`AbstractRemoteServerDummy` and its subclasses): they are fragile. Behaviour between real servers is covered by the verification checklist.

Run the tests with `./mvnw -pl xmppserver -am test` from the repository root.

### Phase 1: Refactor the existing implementation without changing its behaviour

### Session 1: Record current acknowledgement behaviour

**Goal:** Add tests that record what the existing implementation does for enabling Stream Management and for acknowledgements on client connections.

**Files:**
- `test/streammanagement/StreamManagerTest.java`

**Tasks:**
1. Add tests for enabling Stream Management (with and without resumption, both namespaces).
2. Add tests for `<r/>` and `<a/>`: counts of handled stanzas, removal of acknowledged stanzas, acknowledgement of stanzas that were never sent, invalid `h` values.
3. Add tests for the counter roll-over at 2^32 and for exceeding the maximum number of unacknowledged stanzas.
4. Do not change production code. If a test exposes a bug, test the behaviour that the specification requires, annotate the test with `@Disabled` and the issue number, and add the bug to Blocking Issues (see Tests).

**Exit:** New tests pass against unchanged production code, apart from the tests that are disabled for an issue, and cover the behaviour listed above.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 1.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md

Goal: Add tests that record what the existing implementation does for enabling Stream Management and for acknowledgements on client connections.

Working directory: Openfire repository root

Tasks:
1. Add tests for enabling Stream Management (with and without resumption, both namespaces).
2. Add tests for <r/> and <a/>: counts of handled stanzas, removal of acknowledged stanzas, acknowledgement of stanzas that were never sent, invalid 'h' values.
3. Add tests for the counter roll-over at 2^32 and for exceeding the maximum number of unacknowledged stanzas.
4. Do not change production code. If a test exposes a bug, test the behaviour that the specification requires, annotate the test with @Disabled and the issue number, and add the bug to Blocking Issues (see Tests).

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: New tests pass against unchanged production code, apart from the tests that are disabled for an issue, and cover the behaviour listed above.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Session 2: Record current resumption behaviour

**Goal:** Add tests that record what the existing implementation does for resumption, failed resumption, detaching and closing on client connections.

**Files:**
- `test/streammanagement/StreamManagerTest.java`
- `test/SessionManagerCloseListenerRouteOwnershipTest.java` (or a new test class next to it)

**Tasks:**
1. Add tests for a successful resumption: `<resumed/>`, the `h` value reported, and the unacknowledged stanzas sent again.
2. Add tests for failed resumption: unknown `previd`, session on another cluster node, anonymous session, already authenticated session.
3. Add tests that a resumable session is detached when its connection closes, and cleaned up after `xmpp.session.detach.timeout`.
4. Add tests that `StreamManager#onClose` handles unacknowledged stanzas as stanzas for an unavailable resource (XEP-0198 § 4): messages are routed again, IQ requests get a service-unavailable error, and other stanzas are discarded. Annotate the tests that fail until OF-3442 is merged with `@Disabled` and the issue number (see Blocking Issues).
5. Do not change production code.

**Exit:** New tests pass against unchanged production code, and cover the behaviour listed above.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 2.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md

Goal: Add tests that record what the existing implementation does for resumption, failed resumption, detaching and closing on client connections.

Working directory: Openfire repository root

Tasks:
1. Add tests for a successful resumption: <resumed/>, the 'h' value reported, and the unacknowledged stanzas sent again.
2. Add tests for failed resumption: unknown previd, session on another cluster node, anonymous session, already authenticated session.
3. Add tests that a resumable session is detached when its connection closes, and cleaned up after xmpp.session.detach.timeout.
4. Add tests that StreamManager#onClose handles unacknowledged stanzas as stanzas for an unavailable resource (XEP-0198 § 4): messages are routed again, IQ requests get a service-unavailable error, and other stanzas are discarded. Annotate the tests that fail until OF-3442 is merged with @Disabled and the issue number (see Blocking Issues).
5. Do not change production code.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: New tests pass against unchanged production code, and cover the behaviour listed above.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Session 3: Rename client/server to inbound/outbound

**Goal:** Rename the internals of `StreamManager` from client/server to inbound/outbound, so that the names stay correct when Openfire takes the client's role.

**Files:**
- `main/streammanagement/StreamManager.java`
- `main/session/LocalSession.java`
- Any caller of the renamed public methods

**Tasks:**
1. Rename fields such as `serverProcessedStanzas`, `clientProcessedStanzas` and `unacknowledgedServerStanzas` to names based on direction (inbound: stanzas Openfire receives; outbound: stanzas Openfire sends).
2. Rename methods such as `sendServerAcknowledgement`, `sendServerRequest`, `processClientAcknowledgement`, `validateClientAcknowledgement` and `incrementServerProcessedStanzas` in the same way, and update their callers. Keep the old public methods as deprecated delegates (decision 8).
3. Update log messages and Javadoc that refer to "client" or "server" in this sense. Leave text in stream errors sent to clients unchanged.
4. Do not change behaviour.

**Exit:** No client/server naming remains for direction in `StreamManager`; all tests from sessions 1 and 2 pass unchanged.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 3.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md

Goal: Rename the internals of StreamManager from client/server to inbound/outbound, so that the names stay correct when Openfire takes the client's role.

Working directory: Openfire repository root

Tasks:
1. Rename fields such as serverProcessedStanzas, clientProcessedStanzas and unacknowledgedServerStanzas to names based on direction (inbound: stanzas Openfire receives; outbound: stanzas Openfire sends).
2. Rename methods such as sendServerAcknowledgement, sendServerRequest, processClientAcknowledgement, validateClientAcknowledgement and incrementServerProcessedStanzas in the same way, and update their callers. Keep the old public methods as deprecated delegates (decision 8).
3. Update log messages and Javadoc that refer to "client" or "server" in this sense. Leave text in stream errors sent to clients unchanged.
4. Do not change behaviour.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: No client/server naming remains for direction in StreamManager; all tests from sessions 1 and 2 pass unchanged.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Session 4: Move the queue of unacknowledged stanzas into its own class

**Goal:** Move tracking of unacknowledged outbound stanzas out of `StreamManager` into a class that outgoing server sessions can reuse.

**Files:**
- `main/streammanagement/StreamManager.java`
- `main/streammanagement/UnacknowledgedStanzaQueue.java` (new, proposed name)
- `test/streammanagement/UnacknowledgedStanzaQueueTest.java` (new)

**Tasks:**
1. Move the deque of `UnackedPacket`, numbering of sent stanzas, processing of acknowledgements (including roll-over) and the maximum size check into the new class.
2. Give each entry room for a "sent again" marker (needed in session 13), without using it yet.
3. Make `StreamManager` delegate to the new class.
4. Add unit tests for the new class.

**Exit:** `StreamManager` no longer manipulates the deque directly; new and existing tests pass.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 4.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md

Goal: Move tracking of unacknowledged outbound stanzas out of StreamManager into a class that outgoing server sessions can reuse.

Working directory: Openfire repository root

Tasks:
1. Move the deque of UnackedPacket, numbering of sent stanzas, processing of acknowledgements (including roll-over) and the maximum size check into a new class (proposed name: UnacknowledgedStanzaQueue, in the streammanagement package).
2. Give each entry room for a "sent again" marker (needed in session 13), without using it yet.
3. Make StreamManager delegate to the new class.
4. Add unit tests for the new class.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: StreamManager no longer manipulates the deque directly; new and existing tests pass.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Session 5: Isolate the client-specific parts of resumption

**Goal:** Separate the parts of resumption that assume a client session, so that server sessions can provide their own.

**Files:**
- `main/streammanagement/StreamManager.java`
- `main/streammanagement/ResumeRequest.java`
- `main/streammanagement/ResumeRequestValidationResult.java`
- `main/streammanagement/ClientResumption.java` (new, proposed name)

**Tasks:**
1. Identify the client-specific parts: `allowResume()` (checks `ClientSession` and `AuthToken`), the `previd` format (resource and stream ID), and the lookup of the session to resume in `validateResumeRequest` (`getClientRoute`).
2. Move them behind an interface (proposed: `Resumption`) with a client implementation, selected by session type.
3. Leave the inline SASL2 resume flow working through the client implementation.
4. Do not change behaviour.

**Exit:** `StreamManager` contains no references to `ClientSession`, `LocalClientSession` or `AuthToken`; all tests pass, including the SASL2 resume tests.

**Outcome:** `StreamManager#processSasl2Resume` still refers to `LocalClientSession`. Inline resumption is defined for SASL2, which only clients use, and `Sasl2ResumeResult#getResumedSession()` returns a `LocalClientSession`.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 5.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md

Goal: Separate the parts of resumption that assume a client session, so that server sessions can provide their own.

Working directory: Openfire repository root

Tasks:
1. Identify the client-specific parts: allowResume() (checks ClientSession and AuthToken), the previd format (resource and stream ID), and the lookup of the session to resume in validateResumeRequest (getClientRoute).
2. Move them behind an interface (proposed name: Resumption) with a client implementation (proposed: ClientResumption), selected by session type.
3. Leave the inline SASL2 resume flow working through the client implementation.
4. Do not change behaviour.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: StreamManager contains no references to ClientSession, LocalClientSession or AuthToken; all tests pass, including the SASL2 resume tests.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Session 5b: One subclass of StreamManager for each end of a stream

A first version of this session moved the negotiation into separate classes held by `StreamManager`, which decision 9 rejects. This version replaced it.

This session was added after a first version of session 9, which put the code for Openfire asking a remote server to enable Stream Management in `StreamManager`, next to the code that answers such requests from the peer. Nothing ties a `StreamManager` to one end of the stream, so a remote server can also enable Stream Management on a connection that Openfire opened, whatever the settings. Sessions 3 to 5 separated `StreamManager` by direction, by the queue of unacknowledged stanzas and by type of session, but not by which end of the stream asks for Stream Management.

**Goal:** Make `StreamManager` a base class for what both ends of a stream share, with a subclass for the end that answers requests of the peer, so that session 9 can add a subclass for the end that asks (decision 9).

**Files:**
- `main/streammanagement/StreamManager.java`
- `main/streammanagement/AnsweringStreamManager.java` (new, proposed name)
- `main/session/LocalSession.java`
- `main/session/LocalClientSession.java`, `main/session/LocalIncomingServerSession.java` (covariant getter)
- Tests that construct a `StreamManager` or call the methods that move

**Design:** As decision 9 describes. In this session:
- `StreamManager` keeps the counters, the queue of unacknowledged stanzas, `<r/>` and `<a/>`, the maximum of unacknowledged stanzas, `redeliverUnackedStanzas`, `formalClose`, `getResume`, `isEnabled` and the termination delegates. `process` handles `<r/>` and `<a/>`, and passes other elements to an abstract method; an element that the subclass does not handle gets the unexpected-request answer, as today. `onResume` and `onClose` become abstract.
- `AnsweringStreamManager` gets the processing of `<enable/>` and `<resume/>`, `enableAndBuildElement`, `processSasl2Resume`, the validation of resume requests, `detachIfNeeded`, `buildResumedElement`, the implementations of `onResume` and `onClose`, and the `Resumption` from session 5. The moved code keeps `synchronized (this)`, and changes only where it uses the package-private methods for the shared state.
- `StreamManager.forSession(LocalSession)` always returns an `AnsweringStreamManager`. `LocalSession` uses it in its constructor; the check of the session type works there, as `this` already has the type of the subclass.
- `LocalClientSession` and `LocalIncomingServerSession` override `getStreamManager()` to return `AnsweringStreamManager`.

**Compatibility (decisions 8 and 9):**
- `enableAndBuildElement` and `processSasl2Resume` move to `AnsweringStreamManager`. `StreamManager` keeps them as deprecated delegates (`forRemoval`), for callers that hold the base type. Called on a stream manager of another subclass, a delegate refuses as for an unexpected request; that cannot happen until session 9.
- `onResume` and `onClose` keep their signatures as abstract methods, so they need no delegates.
- The public constructor becomes protected, which is listed as an API change in the pull request and the changelog.

**Consequences for later sessions:** Session 7 puts its checks for incoming servers in `AnsweringStreamManager#enableAndBuildElement`. Session 9 adds `RequestingStreamManager`, selected for `LocalOutgoingServerSession`, with `requestEnable` and the status of the request (new, so without delegates); it refuses `<enable/>` and `<resume/>` from the remote server, and every other element that it does not process, without changing the state of Stream Management. Its `onResume` refuses until session 22 implements it, and its `onClose` only disables Stream Management until session 12 implements decision 4.

**Replay:** A first version of sessions 6 to 9 was built on top of the first version of this session, on a branch that this one replaces. After this session, they are replayed on top of it: sessions 6 and 8 as they are, and sessions 7 and 9 reworked as their sections describe.

**Tasks:**
1. Make `StreamManager` abstract, and move the answering parts into `AnsweringStreamManager` as described above, including the implementations of `onResume` and `onClose`, which become abstract.
2. Add `StreamManager.forSession(LocalSession)`, use it in `LocalSession`, and add the covariant getters.
3. Keep deprecated delegates for the moved public methods, and make the constructor protected.
4. Update tests where they construct a `StreamManager`, call moved methods, stub `getStreamManager()` of a client or incoming server session to return a `StreamManager`, or look up private fields by name. Only these references change; the expected behaviour does not.
5. Add a test that checks which subclass `StreamManager.forSession` returns for each type of session, so that the casts in the covariant getters cannot fail unnoticed.
6. Do not change behaviour.

**Exit:** `StreamManager` is abstract and does not process `<enable/>` or `<resume/>` itself; every session gets an `AnsweringStreamManager`, which a test checks per type of session; all tests pass with only the references above changed.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 5b.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md (decisions 8 and 9)

Goal: Make StreamManager a base class for what both ends of a stream share, with a subclass for the end that answers requests of the peer, so that session 9 can add a subclass for the end that asks.

Working directory: Openfire repository root

Tasks:
1. Make StreamManager abstract, and move the answering parts into AnsweringStreamManager (proposed name) as described in the session's Design, including the implementations of onResume and onClose, which become abstract.
2. Add StreamManager.forSession(LocalSession), use it in LocalSession, and add covariant getStreamManager() overrides to LocalClientSession and LocalIncomingServerSession.
3. Keep deprecated delegates for the moved public methods, and make the constructor protected (an API change for the pull request and the changelog).
4. Update tests where they construct a StreamManager, call moved methods, stub getStreamManager() of a client or incoming server session to return a StreamManager, or look up private fields by name. Only these references change; the expected behaviour does not.
5. Add a test that checks which subclass StreamManager.forSession returns for each type of session, so that the casts in the covariant getters cannot fail unnoticed.
6. Do not change behaviour.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: StreamManager is abstract and does not process <enable/> or <resume/> itself; every session gets an AnsweringStreamManager, which a test checks per type of session; all tests pass with only the references above changed.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Session 5c: Arrange the tests by the class they test

Session 5b moves the answering end of Stream Management into its own class, but leaves the tests where they were, so that a test diff with only changed references shows that the move kept behaviour. After it, `StreamManagerTest` mixes tests of what both ends share with tests of the answering end, and all of `StreamManagerResumptionTest` tests the answering end, including its tests of `onClose`, which is implemented per end (decision 9). Someone who changes `AnsweringStreamManager` does not find its tests by name. The tests of the validation of acknowledgements also still carry the client/server naming that session 3 removed from the code, as the tests had to stay unchanged then.

**Goal:** Arrange the tests of Stream Management by the class they test, and name them after direction, without changing what they test (decision 9).

**Files:**
- `test/streammanagement/StreamManagerTest.java`
- `test/streammanagement/StreamManagerResumptionTest.java`, renamed to `AnsweringStreamManagerResumptionTest.java` (proposed name)
- `test/streammanagement/AnsweringStreamManagerTest.java` (new, proposed name)
- `test/streammanagement/StreamManagerDetachedSessionTest.java` (new, proposed name)

**Tasks:**
1. Rename `StreamManagerResumptionTest` to `AnsweringStreamManagerResumptionTest`, as a rename that git recognises.
2. Move the tests whose subject moved to `AnsweringStreamManager` in session 5b from `StreamManagerTest` to `AnsweringStreamManagerTest`: enabling Stream Management, `detachIfNeeded`, building `<resumed/>`, the validation of resume requests, and the handling of unacknowledged stanzas when a session closes (`onClose`).
3. Split the remaining tests, of what both ends share, in two. Tests that need a resumption, or a detached session that is ended, move to `StreamManagerDetachedSessionTest`; the other tests stay in `StreamManagerTest`. A test that only reads, from a mocked session, whether that session is detached stays in `StreamManagerTest`. Session 11 runs `StreamManagerTest` against both ends; `StreamManagerDetachedSessionTest` runs against the end that answers until session 22 makes the end that asks resumable.
4. Rename the tests named `testValidateClientAcknowledgement…` after direction (proposed: `testValidateOutboundAcknowledgement…`).
5. Do not change the setup, the steps or the assertions of any test, other than what the move requires (such as the class under test in a helper). Every test that exists before this session exists after it.

**Exit:** No test of `StreamManagerTest` or `StreamManagerDetachedSessionTest` tests code that is only in `AnsweringStreamManager`; no test of `StreamManagerTest` needs a resumption or a detached session that is ended; no test name uses client/server for direction; the number of tests is the same as before; all tests pass.

**Outcome:** The fixture and the helpers of `StreamManagerTest` moved to a package-private `AbstractStreamManagerTest`, which the three test classes extend, so that the tests themselves moved unchanged. The tests named `testValidateClientAcknowledgement_clientAcks…` are named `testValidateOutboundAcknowledgement_peerAcks…`.

**Notes:** `git blame` does not follow tests that move between files by default; `git blame -C` does. The tests of what both ends share need a concrete subclass to run against, and use `AnsweringStreamManager`. Once session 9 adds `RequestingStreamManager`, session 11 runs `StreamManagerTest` against both subclasses, and session 22 does the same for `StreamManagerDetachedSessionTest`, which shows that what is shared behaves the same at both ends.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 5c.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md (decision 9, sessions 5b and 5c)

Goal: Arrange the tests of Stream Management by the class they test, and name them after direction, without changing what they test.

Working directory: Openfire repository root

Tasks:
1. Rename StreamManagerResumptionTest to AnsweringStreamManagerResumptionTest (proposed name), as a rename that git recognises.
2. Move the tests whose subject moved to AnsweringStreamManager in session 5b from StreamManagerTest to a new AnsweringStreamManagerTest: enabling Stream Management, detachIfNeeded, building <resumed/>, the validation of resume requests, and the handling of unacknowledged stanzas when a session closes (onClose).
3. Split the remaining tests, of what both ends share, in two. Tests that need a resumption, or a detached session that is ended, move to a new StreamManagerDetachedSessionTest (proposed name); the other tests stay in StreamManagerTest. A test that only reads, from a mocked session, whether that session is detached stays in StreamManagerTest.
4. Rename the tests named testValidateClientAcknowledgement... after direction (proposed: testValidateOutboundAcknowledgement...).
5. Do not change the setup, the steps or the assertions of any test, other than what the move requires. Every test that exists before this session exists after it.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: No test of StreamManagerTest or StreamManagerDetachedSessionTest tests code that is only in AnsweringStreamManager; no test of StreamManagerTest needs a resumption or a detached session that is ended; no test name uses client/server for direction; the number of tests is the same as before; all tests pass.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Phase 2: Acknowledgements on incoming connections

Requires OF-3433, which is merged (see Blocking Issues).

### Session 6: Settings for server connections

**Goal:** Add the settings that switch Stream Management on for server connections, separate from those for clients.

**Files:**
- `main/streammanagement/StreamManager.java` (or a new settings holder next to it)
- `i18n/src/main/resources/openfire_i18n.properties`

**Tasks:**
1. Add a system property that enables Stream Management on server connections (proposed key: `stream.management.server.active`), default `false`.
2. Add descriptions for the property in `openfire_i18n.properties`, following the pattern of the existing `stream.management.*` properties.
3. Do not use the property yet.

**Exit:** The property exists, is dynamic, defaults to `false`, and has a description; all tests pass.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 6.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md

Goal: Add the settings that switch Stream Management on for server connections, separate from those for clients.

Working directory: Openfire repository root

Tasks:
1. Add a system property that enables Stream Management on server connections (proposed key: stream.management.server.active), default false.
2. Add descriptions for the property in i18n/src/main/resources/openfire_i18n.properties, following the pattern of the existing stream.management.* properties.
3. Do not use the property yet.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: The property exists, is dynamic, defaults to false, and has a description; all tests pass.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Session 7: Advertise Stream Management on incoming connections

**Rework after session 5b:** The first version of this session put its checks in `StreamManager#enableAndBuildElement`. When it is replayed on top of session 5b, they go in `AnsweringStreamManager#enableAndBuildElement`; nothing else changes.

**Goal:** Advertise Stream Management to remote servers when the setting is enabled, and handle their requests for acknowledgement.

**Files:**
- `main/session/LocalIncomingServerSession.java`
- `main/streammanagement/StreamManager.java`
- `main/streammanagement/AnsweringStreamManager.java` (from session 5b)
- `main/net/ServerStanzaHandler.java`

**Tasks:**
1. In `getAvailableStreamFeatures`, add the Stream Management feature when the setting from session 6 is enabled.
2. Refuse `<enable/>` until at least one domain is authenticated on the session. With dialback there is no stream restart after authentication, so the feature is advertised before any domain is authenticated.
3. Refuse resumption on server sessions (`<enabled/>` without `resume`). Resumption follows in phase 5.
4. Check that `<r/>` and `<a/>` from a remote server reach `StreamManager` and that handled stanzas are counted, and fix what does not work.

**Exit:** With the setting enabled, the feature is advertised on incoming server connections and `<enable/>` succeeds after authentication; with it disabled, nothing changes; all tests pass.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 7.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md

Goal: Advertise Stream Management to remote servers when the setting is enabled, and handle their requests for acknowledgement.

Working directory: Openfire repository root

Tasks:
1. In LocalIncomingServerSession#getAvailableStreamFeatures, add the Stream Management feature when the setting from session 6 is enabled.
2. Refuse <enable/> until at least one domain is authenticated on the session. With dialback there is no stream restart after authentication, so the feature is advertised before any domain is authenticated.
3. Refuse resumption on server sessions (<enabled/> without 'resume'). Resumption follows in phase 5.
4. Check that <r/> and <a/> from a remote server reach StreamManager and that handled stanzas are counted, and fix what does not work.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: With the setting enabled, the feature is advertised on incoming server connections and <enable/> succeeds after authentication; with it disabled, nothing changes; all tests pass.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Session 8: Tests for acknowledgements on incoming connections

**Goal:** Test acknowledgements on incoming server connections with unit tests that use a mocked connection.

**Files:**
- `test/net/ServerStanzaHandlerStreamManagementTest.java` (new, proposed name)

**Tasks:**
1. Pass `<enable/>`, `<r/>` and stanzas to a `ServerStanzaHandler` with a mocked connection, and capture what is delivered to that connection.
2. Add tests: feature advertised or not depending on the setting; `<enable/>` refused before authentication; correct `h` after a number of stanzas; resumption not offered.
3. Cover sessions authenticated with dialback and with a certificate.

**Exit:** Tests pass for both authentication methods, and fail if the feature or counting is broken.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 8.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md

Goal: Test acknowledgements on incoming server connections with unit tests that use a mocked connection.

Working directory: Openfire repository root

Tasks:
1. In a new test class (proposed name: ServerStanzaHandlerStreamManagementTest), pass <enable/>, <r/> and stanzas to a ServerStanzaHandler with a mocked connection, and capture what is delivered to that connection. Follow StanzaHandlerClaimedIdentityTest; do not use the remote server dummies.
2. Add tests: feature advertised or not depending on the setting; <enable/> refused before authentication; correct 'h' after a number of stanzas; resumption not offered.
3. Cover sessions authenticated with dialback and with a certificate.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: Tests pass for both authentication methods, and fail if the feature or counting is broken.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Phase 3: Acknowledgements on outgoing connections

Requires OF-3434, which is merged (see Blocking Issues).

### Session 9: Enable Stream Management on outgoing connections

**Rework after session 5b:** The first version of this session put the end that asks in a negotiation class held by `StreamManager`, as the first version of session 5b did. When it is replayed on top of session 5b, it adds `RequestingStreamManager` instead (tasks 5 to 7). Its tests and its changes to `RespondingServerStanzaHandler` are kept.

**Goal:** Let Openfire enable Stream Management on server connections it opens, taking the role clients have today.

**Files:**
- `main/net/RespondingServerStanzaHandler.java`
- `main/session/LocalOutgoingServerSession.java`
- `main/streammanagement/StreamManager.java`
- `main/streammanagement/RequestingStreamManager.java` (new, proposed name, decision 9)

**Tasks:**
1. When the setting from session 6 is enabled and the remote server offers the feature after authentication, send `<enable/>` (without `resume` for now).
2. Process `<enabled/>` and `<failed/>`, and record the outcome on the session's `StreamManager`.
3. An `<r/>` from the remote server needs no meaningful answer (stanzas only flow one way), but must not break the connection.
4. Add unit tests with a mocked connection.
5. Add `RequestingStreamManager` (decision 9). Select it in `StreamManager.forSession` for `LocalOutgoingServerSession`, and override `getStreamManager()` in `LocalOutgoingServerSession` to return it. Extend the test from session 5b that checks which subclass each type of session gets.
6. Answer `<enable/>` and `<resume/>` from the remote server, an `<enabled/>` or `<failed/>` that does not answer a request of Openfire, and every other Stream Management element that this end does not process, with `<failed/>`, without changing the state of Stream Management. The fallback of the base class disables Stream Management, after which stanzas are no longer tracked and `onClose` does not handle the ones already tracked (sessions 11 and 12).
7. Let `onResume` refuse until session 22, and let `onClose` only disable Stream Management until session 12.

**Exit:** With the setting enabled, Openfire enables Stream Management on outgoing connections to servers that offer it, and does not try with servers that don't; a remote server cannot enable Stream Management on such a connection; elements that this end does not process leave the state of Stream Management unchanged; all tests pass.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 9.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md

Goal: Let Openfire enable Stream Management on server connections it opens, taking the role clients have today.

Working directory: Openfire repository root

Tasks:
1. When the setting from session 6 is enabled and the remote server offers the feature after authentication, send <enable/> (without 'resume' for now). See RespondingServerStanzaHandler and LocalOutgoingServerSession.
2. Process <enabled/> and <failed/>, and record the outcome on the session's StreamManager.
3. An <r/> from the remote server needs no meaningful answer (stanzas only flow one way), but must not break the connection.
4. Add unit tests with a mocked connection; do not use the remote server dummies.
5. Add RequestingStreamManager (decision 9). Select it in StreamManager.forSession for LocalOutgoingServerSession, and override getStreamManager() in LocalOutgoingServerSession to return it. Extend the test from session 5b that checks which subclass each type of session gets.
6. Answer <enable/> and <resume/> from the remote server, an <enabled/> or <failed/> that does not answer a request of Openfire, and every other Stream Management element that this end does not process, with <failed/>, without changing the state of Stream Management.
7. Let onResume refuse until session 22, and let onClose only disable Stream Management until session 12.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: With the setting enabled, Openfire enables Stream Management on outgoing connections to servers that offer it, and does not try with servers that don't; a remote server cannot enable Stream Management on such a connection; elements that this end does not process leave the state of Stream Management unchanged; all tests pass.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Session 10: Do not use a new outgoing connection before negotiation finishes

**Goal:** Implement decision 3: a new outgoing connection is only used once Stream Management negotiation has finished.

**Files:**
- `main/session/LocalOutgoingServerSession.java`
- `main/server/OutgoingSessionPromise.java`

**Tasks:**
1. Delay making the session available as a route (`addOutgoingDomainPair` / `addServerRoute`) until Stream Management is enabled, refused, not offered, or negotiation timed out.
2. Add a timeout for negotiation (proposed: a system property next to `INITIALISE_TIMEOUT_SECONDS`).
3. Make sure stanzas queued in `OutgoingSessionPromise` while waiting are sent once the session is available, in order.
4. Add unit tests.

**Exit:** No stanza is sent on a new outgoing connection before negotiation has finished or timed out; connections to servers without Stream Management are not delayed; all tests pass.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 10.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md (decision 3)

Goal: A new outgoing connection is only used once Stream Management negotiation has finished.

Working directory: Openfire repository root

Tasks:
1. Delay making the session available as a route (addOutgoingDomainPair / addServerRoute in LocalOutgoingServerSession) until Stream Management is enabled, refused, not offered, or negotiation timed out.
2. Add a timeout for negotiation (proposed: a system property next to INITIALISE_TIMEOUT_SECONDS).
3. Make sure stanzas queued in OutgoingSessionPromise while waiting are sent once the session is available, in order.
4. Add unit tests with mocked connections; do not use the remote server dummies.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: No stanza is sent on a new outgoing connection before negotiation has finished or timed out; connections to servers without Stream Management are not delayed; all tests pass.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Session 11: Track stanzas and process acknowledgements on outgoing connections

**Goal:** Track stanzas sent on outgoing connections, request acknowledgements, and process them.

**Files:**
- `main/session/LocalOutgoingServerSession.java`
- `main/streammanagement/StreamManager.java`
- `test/session/LocalOutgoingServerSessionStreamManagementTest.java` (new, proposed name)
- `test/streammanagement/StreamManagerTest.java` (from session 5c)

**Tasks:**
1. Record each stanza sent on an outgoing session with Stream Management enabled in the queue from session 4.
2. Send `<r/>` at the same frequency as for clients, and process `<a/>`.
3. On a broken connection, behave as today (handling unacknowledged stanzas follows in phase 4).
4. Add unit tests with a mocked connection that feed `<a/>` to the session.
5. Run the tests in `StreamManagerTest`, which test what both ends of a stream share without a resumption or a detached session that is ended (session 5c), against both `AnsweringStreamManager` and `RequestingStreamManager` (decision 9). Where a test needs a step that only one end has, such as enabling Stream Management, provide that step per subclass rather than leaving the test out.

**Exit:** Acknowledged stanzas are removed from the queue; invalid acknowledgements close the connection as for clients; the tests of what both ends share pass for both subclasses; tests pass.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 11.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md

Goal: Track stanzas sent on outgoing connections, request acknowledgements, and process them.

Working directory: Openfire repository root

Tasks:
1. Record each stanza sent on an outgoing session with Stream Management enabled in the queue from session 4.
2. Send <r/> at the same frequency as for clients, and process <a/>.
3. On a broken connection, behave as today (handling unacknowledged stanzas follows in phase 4).
4. Add unit tests with a mocked connection that feed <a/> to the session (proposed class: LocalOutgoingServerSessionStreamManagementTest); do not use the remote server dummies.
5. Run the tests in StreamManagerTest, which test what both ends of a stream share without a resumption or a detached session that is ended (session 5c), against both AnsweringStreamManager and RequestingStreamManager (decision 9). Where a test needs a step that only one end has, such as enabling Stream Management, provide that step per subclass rather than leaving the test out.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: Acknowledged stanzas are removed from the queue; invalid acknowledgements close the connection as for clients; the tests of what both ends share pass for both subclasses; tests pass.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Phase 4: Unacknowledged stanzas after an outgoing connection breaks

Requires OF-3442, which is merged (see Blocking Issues).

### Session 12: Send again or return unacknowledged stanzas

**Goal:** Implement decision 4 for outgoing connections: after a break, send unacknowledged stanzas again or return them to the sender, depending on their type.

**Files:**
- `main/session/LocalOutgoingServerSession.java`
- `main/streammanagement/RequestingStreamManager.java` (from session 9)
- `test/session/LocalOutgoingServerSessionStreamManagementTest.java`

**Tasks:**
1. When an outgoing session with Stream Management closes without resumption, route messages, presences and IQ results/errors again, so that they go out over a new connection. Implement this in `RequestingStreamManager#onClose` (decision 9).
2. Return IQ get/set requests to the sender as an error (proposed condition: `recipient-unavailable`).
3. Keep the client close behaviour (`AnsweringStreamManager#onClose`) unchanged.
4. Add unit tests.

**Exit:** After a break, each stanza type is handled as decision 4 describes; connections without Stream Management behave as today; client tests from phase 1 pass.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 12.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md (decision 4)

Goal: After an outgoing connection breaks, send unacknowledged stanzas again or return them to the sender, depending on their type.

Working directory: Openfire repository root

Tasks:
1. When an outgoing session with Stream Management closes without resumption, route messages, presences and IQ results/errors again, so that they go out over a new connection. Implement this in RequestingStreamManager#onClose (decision 9).
2. Return IQ get/set requests to the sender as an error (proposed condition: recipient-unavailable).
3. Keep the client close behaviour (AnsweringStreamManager#onClose) unchanged.
4. Add unit tests to LocalOutgoingServerSessionStreamManagementTest, using a mocked connection and packet router.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: After a break, each stanza type is handled as decision 4 describes; connections without Stream Management behave as today; client tests from phase 1 pass.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Session 13: Send a stanza again at most once

**Goal:** Prevent a stanza that breaks the connection from being sent again forever.

**Files:**
- `main/streammanagement/UnacknowledgedStanzaQueue.java` (from session 4)
- `main/session/LocalOutgoingServerSession.java`
- `test/session/LocalOutgoingServerSessionStreamManagementTest.java`

**Tasks:**
1. Mark stanzas that are sent again, using the marker added in session 4, and carry the marker when the stanza is routed to a new connection.
2. When a marked stanza is unacknowledged at a second break, return it to the sender as an error, whatever its type.
3. Add a unit test that breaks two connections in a row while the same stanza is unacknowledged.

**Exit:** A stanza that is unacknowledged at two breaks in a row is returned as an error after one retry; other stanzas are unaffected; tests pass.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 13.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md (decision 4)

Goal: Prevent a stanza that breaks the connection from being sent again forever.

Working directory: Openfire repository root

Tasks:
1. Mark stanzas that are sent again, using the marker added in session 4, and carry the marker when the stanza is routed to a new connection.
2. When a marked stanza is unacknowledged at a second break, return it to the sender as an error, whatever its type.
3. Add a unit test that breaks two connections in a row while the same stanza is unacknowledged, using mocked connections.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: A stanza that is unacknowledged at two breaks in a row is returned as an error after one retry; other stanzas are unaffected; tests pass.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Phase 5a: Keeping broken incoming sessions

Requires OF-3420 to be merged, and OF-3443, which is merged (see Blocking Issues).

### Session 14: Timeout per session type for detached sessions

**Goal:** Implement decision 5: detached server sessions get their own timeout.

**Files:**
- `main/SessionManager.java`
- `i18n/src/main/resources/openfire_i18n.properties`

**Tasks:**
1. Add a system property for the timeout of detached server sessions (proposed key: `xmpp.server.session.detach.timeout`).
2. Make the task that cleans up detached sessions use the timeout that matches the session type.
3. Make sure the task runs often enough for the shorter timeout.
4. Add unit tests.

**Exit:** Detached client sessions are cleaned up after the client timeout, detached server sessions after the server timeout; tests pass.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 14.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md (decision 5)

Goal: Detached server sessions get their own timeout.

Working directory: Openfire repository root

Tasks:
1. Add a system property for the timeout of detached server sessions (proposed key: xmpp.server.session.detach.timeout), with a description in openfire_i18n.properties.
2. Make the task in SessionManager that cleans up detached sessions use the timeout that matches the session type.
3. Make sure the task runs often enough for the shorter timeout.
4. Add unit tests.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: Detached client sessions are cleaned up after the client timeout, detached server sessions after the server timeout; tests pass.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Session 15: Detach broken incoming server sessions

**Goal:** Keep a broken incoming server session for the configured time when resumption is enabled for it.

**Files:**
- `main/SessionManager.java`
- `main/session/LocalIncomingServerSession.java`
- `main/streammanagement/StreamManager.java`

**Tasks:**
1. Add a system property that enables resumption on server connections (proposed key: `stream.management.server.resume.enabled`), default `false`.
2. When an incoming server session with resumption enabled loses its connection, detach it instead of closing it, as the client session close listener does for clients.
3. Do not offer resumption to remote servers yet; for testing, set the resumable state directly.
4. Add unit tests.

**Exit:** A broken incoming server session marked resumable is detached and cleaned up after the server timeout; other incoming server sessions close as today; client resumption behaves as before.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 15.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md

Goal: Keep a broken incoming server session for the configured time when resumption is enabled for it.

Working directory: Openfire repository root

Tasks:
1. Add a system property that enables resumption on server connections (proposed key: stream.management.server.resume.enabled), default false, with a description in openfire_i18n.properties.
2. When an incoming server session with resumption enabled loses its connection, detach it instead of closing it, as the client session close listener in SessionManager does for clients.
3. Do not offer resumption to remote servers yet; for testing, set the resumable state directly.
4. Add unit tests, following SessionManagerCloseListenerRouteOwnershipTest.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: A broken incoming server session marked resumable is detached and cleaned up after the server timeout; other incoming server sessions close as today; client resumption behaves as before.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Phase 5b: Accepting resumption on incoming connections

### Session 16: Offer resumption on incoming connections

**Goal:** Offer resumption to remote servers and reattach a kept session to the new connection.

**Files:**
- `main/streammanagement/StreamManager.java`
- `main/streammanagement/ServerResumption.java` (new, proposed name, implementing the interface from session 5)
- `main/session/LocalIncomingServerSession.java`
- `main/session/LocalSession.java`

**Tasks:**
1. Add a server implementation of the resumption interface from session 5, with a `previd` that identifies the session without relying on a resource (for example based on the stream ID).
2. Accept `<enable resume="true"/>` on incoming server sessions when resumption is enabled.
3. Look up the kept session and reattach it to the new connection, using `LocalSession#reattach`. That method, like `reattachForSasl2`, casts the session that provides the connection to `LocalClientSession`: make it work for server sessions.
4. For now, accept resumption from any authenticated session; session 17 adds the checks. Do not merge the pull request before session 17.
5. Add unit tests.

**Exit:** A remote server can resume a kept session, and stanza counts continue correctly; tests pass.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 16.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md

Goal: Offer resumption to remote servers and reattach a kept session to the new connection.

Working directory: Openfire repository root

Tasks:
1. Add a server implementation of the resumption interface from session 5 (proposed name: ServerResumption), with a previd that identifies the session without relying on a resource (for example based on the stream ID).
2. Accept <enable resume="true"/> on incoming server sessions when resumption is enabled.
3. Look up the kept session and reattach it to the new connection, using LocalSession#reattach. That method, like reattachForSasl2, casts the session that provides the connection to LocalClientSession: make it work for server sessions.
4. For now, accept resumption from any authenticated session; session 17 adds the checks. The pull request for this phase must not be merged before session 17 is done.
5. Add unit tests with mocked connections; do not use the remote server dummies.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: A remote server can resume a kept session, and stanza counts continue correctly; tests pass.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Session 17: Check who may resume

**Goal:** Implement decision 1: only the same remote server, with the same or a stronger authentication method, may resume a session.

**Files:**
- `main/streammanagement/ServerResumption.java` (from session 16)
- `main/session/LocalIncomingServerSession.java`
- `main/session/ServerSession.java`

**Tasks:**
1. Record the first domain authenticated on an incoming session, and its authentication method.
2. On resumption, compare the first authenticated domain of the new connection with that of the kept session, and the authentication method (`SASL_EXTERNAL` is stronger than `DIALBACK`).
3. Refuse resumption with `item-not-found` when the check fails, without revealing whether the session exists.
4. Add unit tests for: same domain and method; dialback to certificate (allowed); certificate to dialback (refused); other domain (refused); a claimed domain in the stream header that differs from the authenticated one.

**Exit:** All cases above behave as decision 1 describes; tests pass.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 17.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md (decision 1)

Goal: Only the same remote server, with the same or a stronger authentication method, may resume a session.

Working directory: Openfire repository root

Tasks:
1. Record the first domain authenticated on an incoming session, and its authentication method.
2. On resumption, compare the first authenticated domain of the new connection with that of the kept session, and the authentication method (SASL_EXTERNAL is stronger than DIALBACK).
3. Refuse resumption with item-not-found when the check fails, without revealing whether the session exists.
4. Add unit tests for: same domain and method; dialback to certificate (allowed); certificate to dialback (refused); other domain (refused); a claimed domain in the stream header that differs from the authenticated one.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: All cases above behave as decision 1 describes; tests pass.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Session 18: Extra domains after resumption on incoming connections

**Goal:** Implement decision 2 for incoming connections: extra domains remain valid after resumption.

**Files:**
- `main/session/LocalIncomingServerSession.java`
- `test/session/LocalIncomingServerSessionResumptionTest.java` (new, proposed name)

**Tasks:**
1. Keep all validated domains of the kept session after resumption.
2. Accept dialback for a domain that is already validated on the resumed session.
3. Add unit tests that resume a session with extra validated domains and then process stanzas from each of them, with and without authenticating them again.

**Exit:** Stanzas from all originally validated domains are accepted after resumption; authenticating one again is accepted; tests pass.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 18.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md (decision 2, incoming)

Goal: Extra domains remain valid after resumption on incoming connections.

Working directory: Openfire repository root

Tasks:
1. Keep all validated domains of the kept session after resumption.
2. Accept dialback for a domain that is already validated on the resumed session.
3. Add unit tests (proposed class: LocalIncomingServerSessionResumptionTest) that resume a session with extra validated domains and then process stanzas from each of them, with and without authenticating them again. Use mocked connections; do not use the remote server dummies.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: Stanzas from all originally validated domains are accepted after resumption; authenticating one again is accepted; tests pass.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Phase 5c: Cluster location hint

### Session 19: Location hint for server sessions

**Goal:** Implement decision 6: tell the remote server which cluster node to reconnect to.

**Files:**
- `main/streammanagement/StreamManager.java`
- `main/streammanagement/ServerResumption.java`

**Tasks:**
1. Include the `location` attribute in `<enabled/>` for server sessions, controlled by `stream.management.location.enabled` as for clients.
2. When a resumption attempt reaches a node that does not hold the session, fail with `item-not-found`, so that the remote server can start a new stream.
3. Add unit tests.

**Exit:** The location hint points to the node that holds the session; resumption on another node fails cleanly; tests pass.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 19.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md (decision 6)

Goal: Tell the remote server which cluster node to reconnect to.

Working directory: Openfire repository root

Tasks:
1. Include the 'location' attribute in <enabled/> for server sessions, controlled by stream.management.location.enabled as for clients.
2. When a resumption attempt reaches a node that does not hold the session, fail with item-not-found, so that the remote server can start a new stream.
3. Add unit tests.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: The location hint points to the node that holds the session; resumption on another node fails cleanly; tests pass.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Phase 6a: Keeping broken outgoing sessions and resuming them

### Session 20: Keep the route of a broken outgoing session

**Goal:** Implement the first part of decision 7: a broken outgoing session stays the route for its domains and queues new stanzas.

**Files:**
- `main/session/LocalOutgoingServerSession.java`
- `main/SessionManager.java`

**Tasks:**
1. Request resumption (`<enable resume="true"/>`) on outgoing connections when resumption is enabled, and record the `id` and `max` from `<enabled/>`.
2. When such a session loses its connection, detach it, and keep its server routes in the routing table.
3. Queue stanzas routed to the detached session behind the unacknowledged ones, up to a maximum (proposed: a new system property).
4. Add unit tests.

**Exit:** During a break, the session remains the route for its domains and new stanzas are queued on it, up to the maximum; tests pass.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 20.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md (decision 7)

Goal: A broken outgoing session stays the route for its domains and queues new stanzas.

Working directory: Openfire repository root

Tasks:
1. Request resumption (<enable resume="true"/>) on outgoing connections when resumption is enabled, and record the 'id' and 'max' from <enabled/>.
2. When such a session loses its connection, detach it, and keep its server routes in the routing table.
3. Queue stanzas routed to the detached session behind the unacknowledged ones, up to a maximum (proposed: a new system property, with a description in openfire_i18n.properties).
4. Add unit tests with mocked connections; do not use the remote server dummies.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: During a break, the session remains the route for its domains and new stanzas are queued on it, up to the maximum; tests pass.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Session 21: One outgoing connection per remote domain while resuming

**Goal:** Make sure no second outgoing connection to the same remote domain is opened while a session for it is detached, on any cluster node.

**Files:**
- `main/server/OutgoingSessionPromise.java`
- `main/session/LocalOutgoingServerSession.java`
- `main/spi/RoutingTableImpl.java`

**Tasks:**
1. Find every place where a new outgoing connection can be started for a domain pair (on this node, and from other nodes through the cluster-wide server routes).
2. When a detached session holds the route, send stanzas to that session (on its node) instead of starting a new connection.
3. Add unit tests for a stanza routed on the node that holds the session and on another node.

**Exit:** While a session is detached, stanzas for its domains reach it from every node, and no second connection is started; tests pass.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 21.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md (decision 7)

Goal: Make sure no second outgoing connection to the same remote domain is opened while a session for it is detached, on any cluster node.

Working directory: Openfire repository root

Tasks:
1. Find every place where a new outgoing connection can be started for a domain pair (on this node, and from other nodes through the cluster-wide server routes; see OutgoingSessionPromise and RoutingTableImpl).
2. When a detached session holds the route, send stanzas to that session (on its node) instead of starting a new connection.
3. Add unit tests for a stanza routed on the node that holds the session and on another node, using mocks for the routing table and cluster.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: While a session is detached, stanzas for its domains reach it from every node, and no second connection is started; tests pass.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Session 22: Resume a broken outgoing session

**Goal:** Reconnect and resume a detached outgoing session, then send what is missing in order.

**Files:**
- `main/session/LocalOutgoingServerSession.java`
- `main/net/RespondingServerStanzaHandler.java`
- `main/streammanagement/StreamManager.java`
- `main/streammanagement/RequestingStreamManager.java` (from session 9)

**Tasks:**
1. Open a new connection from the node that holds the session, authenticate the main domain, and send `<resume/>` with the recorded `id` and the inbound count.
2. On `<resumed/>`, attach the new connection to the session, remove the stanzas acknowledged by its `h`, and send the remaining unacknowledged stanzas (marked as sent again, decision 4), then the queued ones, in order. Implement `RequestingStreamManager#onResume` for this (decision 9).
3. Leave stanzas for extra domains waiting (session 23) and leave giving up (session 24) for later; for now, a failed attempt closes the session as in phase 4.
4. Add unit tests that feed `<resumed/>` to the handler and check the order of what is delivered to the mocked connection.
5. Run the tests in `StreamManagerDetachedSessionTest` (session 5c) against both `AnsweringStreamManager` and `RequestingStreamManager`, as session 11 does for `StreamManagerTest`.

**Exit:** After a break, the session resumes, the stanzas the remote server did not receive arrive first, followed by the queued ones, in order; the tests of detached sessions pass for both subclasses; tests pass.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 22.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md (decision 7)

Goal: Reconnect and resume a detached outgoing session, then send what is missing in order.

Working directory: Openfire repository root

Tasks:
1. Open a new connection from the node that holds the session, authenticate the main domain, and send <resume/> with the recorded 'id' and the inbound count.
2. On <resumed/>, attach the new connection to the session, remove the stanzas acknowledged by its 'h', and send the remaining unacknowledged stanzas (marked as sent again, decision 4), then the queued ones, in order. Implement `RequestingStreamManager#onResume` for this (decision 9).
3. Leave stanzas for extra domains waiting (session 23) and leave giving up (session 24) for later; for now, a failed attempt closes the session as in phase 4.
4. Add unit tests that feed <resumed/> to the handler and check the order of what is delivered to the mocked connection; do not use the remote server dummies.
5. Run the tests in StreamManagerDetachedSessionTest (session 5c) against both AnsweringStreamManager and RequestingStreamManager, as session 11 does for StreamManagerTest.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: After a break, the session resumes, the stanzas the remote server did not receive arrive first, followed by the queued ones, in order; the tests of detached sessions pass for both subclasses; tests pass.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Phase 6b: Extra domains on resumed outgoing connections

### Session 23: Authenticate extra domains again after resumption

**Goal:** Implement decision 2 for outgoing connections: authenticate each extra domain again before sending stanzas for it.

**Files:**
- `main/session/LocalOutgoingServerSession.java`
- `main/server/ServerDialback.java`
- `test/session/LocalOutgoingServerSessionStreamManagementTest.java`

**Tasks:**
1. After resumption, start dialback for each extra domain pair of the session.
2. Hold stanzas for an extra domain, including unacknowledged ones, until that domain is verified.
3. If verification fails, handle that domain's unacknowledged stanzas as in decision 4, and send its queued stanzas over a new connection.
4. Add unit tests for success and failure of verification, with the dialback outcome mocked.

**Exit:** Stanzas for an extra domain are only sent after it is verified; on failure they are handled as described; tests pass.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 23.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md (decision 2, outgoing)

Goal: Authenticate each extra domain again before sending stanzas for it after resuming an outgoing connection.

Working directory: Openfire repository root

Tasks:
1. After resumption, start dialback for each extra domain pair of the session.
2. Hold stanzas for an extra domain, including unacknowledged ones, until that domain is verified.
3. If verification fails, handle that domain's unacknowledged stanzas as in decision 4, and send its queued stanzas over a new connection.
4. Add unit tests for success and failure of verification, with the dialback outcome mocked; do not use the remote server dummies.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: Stanzas for an extra domain are only sent after it is verified; on failure they are handled as described; tests pass.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Phase 6c: Giving up on resumption

### Session 24: Give up on resumption and fall back

**Goal:** Implement the last part of decision 7: give up on resumption when it cannot succeed, and fall back to the behaviour from phase 4.

**Files:**
- `main/session/LocalOutgoingServerSession.java`
- `main/SessionManager.java`
- `test/session/LocalOutgoingServerSessionStreamManagementTest.java`

**Tasks:**
1. Give up when the server timeout (session 14) or the remote server's `max` expires, whichever comes first; when the remote server answers `<failed/>`; or when the queue is full.
2. On giving up, remove the session's routes, handle unacknowledged stanzas as in phase 4, and send queued stanzas over a new connection.
3. Add a unit test for each of the four reasons.

**Exit:** Each reason leads to the fallback; no stanza is lost or sent more than once beyond what decision 4 allows; tests pass.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 24.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md (decision 7)

Goal: Give up on resumption when it cannot succeed, and fall back to the behaviour from phase 4.

Working directory: Openfire repository root

Tasks:
1. Give up when the server timeout (session 14) or the remote server's 'max' expires, whichever comes first; when the remote server answers <failed/>; or when the queue is full.
2. On giving up, remove the session's routes, handle unacknowledged stanzas as in phase 4, and send queued stanzas over a new connection.
3. Add a unit test for each of the four reasons, using mocked connections.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: Each reason leads to the fallback; no stanza is lost or sent more than once beyond what decision 4 allows; tests pass.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Phase 7: Finishing

### Session 25: Admin console

**Goal:** Show Stream Management state for server sessions, and make the new settings available in the admin console with their final defaults.

**Files:**
- `xmppserver/src/main/webapp/server-session-details.jsp`
- `xmppserver/src/main/webapp/server-session-row.jspf`
- `xmppserver/src/main/webapp/connection-settings-socket-s2s.jsp`
- `i18n/src/main/resources/openfire_i18n.properties`

**Tasks:**
1. Show per server session whether Stream Management is enabled, whether it is resumable, whether it is detached, and the number of unacknowledged stanzas.
2. Add the new settings to the server-to-server connection settings page.
3. Set the final defaults of the new settings (see Open Questions).

**Exit:** The admin console shows the state of server sessions and lets an administrator change the new settings; the JSPs compile in a clean build; tests pass.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 25.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md

Goal: Show Stream Management state for server sessions, and make the new settings available in the admin console with their final defaults.

Working directory: Openfire repository root

Tasks:
1. In server-session-details.jsp and server-session-row.jspf, show per server session whether Stream Management is enabled, whether it is resumable, whether it is detached, and the number of unacknowledged stanzas.
2. Add the new settings to connection-settings-socket-s2s.jsp, with labels in openfire_i18n.properties.
3. Set the final defaults of the new settings as recorded in the plan's Open Questions. If they are not decided yet, stop and ask.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: The admin console shows the state of server sessions and lets an administrator change the new settings; the JSPs compile in a clean build; tests pass.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Session 26: Documentation

**Goal:** Update the protocol support documentation and guides.

**Files:**
- `documentation/openfire.doap`
- `documentation/protocol-support.html`
- Guides in `documentation/` that describe server-to-server connections or Stream Management

**Tasks:**
1. In `openfire.doap`, update the XEP-0198 entry: its status, and the note that says it is supported on client-to-server connections only.
2. Update the XEP-0198 entries in `protocol-support.html` to show support for server-to-server connections.
3. Describe the new settings, their defaults, and the behaviour after a broken connection in the relevant guides.

**Exit:** The DOAP file, the protocol support page and the guides describe server-to-server Stream Management and the new settings.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 26.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md

Goal: Update the protocol support documentation and guides.

Working directory: Openfire repository root

Tasks:
1. In documentation/openfire.doap, update the XEP-0198 entry: its status, and the note that says it is supported on client-to-server connections only.
2. Update the XEP-0198 entries in documentation/protocol-support.html to show support for server-to-server connections.
3. Find the guides in documentation/ that describe server-to-server connections or Stream Management, and describe the new settings, their defaults, and the behaviour after a broken connection.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: The DOAP file, the protocol support page and the guides describe server-to-server Stream Management and the new settings.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Session 27: ADRs

**Goal:** Record decisions 1, 2 and 4 as ADRs.

**Files:**
- `doc/adr/007-…`, `doc/adr/008-…`, `doc/adr/009-…` (new; use the next free numbers)

**Tasks:**
1. Write one ADR per decision, following the format in `doc/adr/README.md`.
2. In the ADR for decision 2, state that it depends on decision 1.
3. Base the ADRs on the decisions as implemented, including anything that changed during implementation.

**Exit:** Three ADRs exist that follow the format, and match the implementation.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 27.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md (decisions 1, 2 and 4)

Goal: Record decisions 1, 2 and 4 as ADRs.

Working directory: Openfire repository root

Tasks:
1. Write one ADR per decision in doc/adr/, following the format in doc/adr/README.md and using the next free numbers.
2. In the ADR for decision 2, state that it depends on decision 1.
3. Base the ADRs on the decisions as implemented, including anything that changed during implementation.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: Three ADRs exist that follow the format, and match the implementation.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Session 28: Cleanup

**Goal:** Remove leftovers from the earlier sessions and check the work against the verification checklist.

**Files:**
- Any file changed in sessions 1-27

**Tasks:**
1. Remove temporary code, TODOs and test-only shortcuts introduced in earlier sessions.
2. Check that log messages and Javadoc are consistent.
3. Go through the verification checklist and record which items still need testing by others.

**Exit:** No leftovers remain; the verification checklist shows what is done and what remains.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 28.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md

Goal: Remove leftovers from the earlier sessions and check the work against the verification checklist.

Working directory: Openfire repository root

Tasks:
1. Remove temporary code, TODOs and test-only shortcuts introduced in earlier sessions.
2. Check that log messages and Javadoc are consistent.
3. Go through the plan's verification checklist and record which items still need testing by others.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: No leftovers remain; the verification checklist shows what is done and what remains.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

## Critical Files

| Repository | File | Purpose |
|------------|------|---------|
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/streammanagement/StreamManager.java` | Existing XEP-0198 implementation; from session 5b the base class for what both ends of a stream share (decision 9) |
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/streammanagement/ResumeRequest.java` | Parsed resume request |
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/streammanagement/UnacknowledgedStanzaQueue.java` | Outbound stanzas that the peer has not acknowledged (session 4) |
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/streammanagement/Resumption.java` | Parts of resumption that depend on the type of session (session 5) |
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/streammanagement/ClientResumption.java` | Resumption rules for client sessions (session 5) |
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/streammanagement/AnsweringStreamManager.java` | Stream Management at the end of a stream that answers the peer's requests (session 5b, decision 9) |
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/streammanagement/RequestingStreamManager.java` | Stream Management at the end of a stream that asks for it, on connections Openfire opens to other servers (session 9, decision 9) |
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/session/LocalSession.java` | Holds the `StreamManager`; detach and reattach |
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/session/LocalIncomingServerSession.java` | Incoming server sessions; stream features, validated domains |
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/session/LocalOutgoingServerSession.java` | Outgoing server sessions; authentication and routes |
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/session/ServerSession.java` | `AuthenticationMethod` (`DIALBACK`, `SASL_EXTERNAL`) |
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/net/StanzaHandler.java` | Passes Stream Management elements to `StreamManager` |
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/net/ServerStanzaHandler.java` | Data received on incoming server connections |
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/net/RespondingServerStanzaHandler.java` | Data received on outgoing server connections, including stream features |
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/server/OutgoingSessionPromise.java` | Queues stanzas while an outgoing connection is set up |
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/server/ServerDialback.java` | Dialback |
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/spi/RoutingTableImpl.java` | Cluster-wide server routes |
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/SessionManager.java` | Detached sessions, their timeout and cleanup |
| Openfire | `xmppserver/src/test/java/org/jivesoftware/Fixtures.java` | Test fixtures, including a mocked `XMPPServer` |
| Openfire | `xmppserver/src/test/java/org/jivesoftware/openfire/SessionManagerCloseListenerRouteOwnershipTest.java` | Example of a mock-based session test |
| Openfire | `xmppserver/src/test/java/org/jivesoftware/openfire/net/StanzaHandlerClaimedIdentityTest.java` | Example of a mock-based stanza handler test |
| Openfire | `xmppserver/src/main/webapp/server-session-details.jsp` | Admin console: server session details |
| Openfire | `xmppserver/src/main/webapp/connection-settings-socket-s2s.jsp` | Admin console: server-to-server settings |
| Openfire | `documentation/openfire.doap` | Protocol support data (DOAP) |
| Openfire | `documentation/protocol-support.html` | Protocol support documentation |

---

## Verification Checklist

The detailed test approach is left to Bob Love. These items describe what must be shown, not how.

### Phase 1: Refactor (Sessions 1-5c)
- [ ] Tests cover enabling, acknowledgements, resumption, failed resumption and closing for client connections.
- [ ] Client connections behave exactly as before: no protocol, setting or admin console change.

### Phase 2: Acknowledgements on incoming connections (Sessions 6-8)
- [ ] With the setting disabled, incoming server connections behave as before.
- [ ] With the setting enabled, a remote server can enable Stream Management and receives correct counts.

### Phase 3: Acknowledgements on outgoing connections (Sessions 9-11)
- [ ] No stanza is sent on a new outgoing connection before negotiation has finished or timed out.
- [ ] Two Openfire servers acknowledge stanzas between them.

### Phase 4: Unacknowledged stanzas (Sessions 12-13)
- [ ] After a break, stanzas are sent again or returned as decision 4 describes.
- [ ] A stanza that makes the remote server close the connection is returned as an error after one retry.

### Phase 5: Resumption on incoming connections (Sessions 14-19)
- [ ] Resumption is refused after a downgrade from certificate to dialback, and for another domain.
- [ ] Extra dialback domains remain valid after resumption.
- [ ] In a cluster, the location hint is sent, and resumption on the wrong node fails cleanly.

### Phase 6: Resumption on outgoing connections (Sessions 20-24)
- [ ] During resumption, new stanzas are queued and arrive in order after it succeeds.
- [ ] In a cluster, no second connection to the remote domain is opened while resuming.
- [ ] Extra domains are authenticated again before stanzas are sent for them.
- [ ] Each reason to give up leads to the fallback without losing stanzas.

### Overall (Sessions 25-28)
- [ ] With all new settings disabled, server connections behave as before this work.
- [ ] Tested against at least one other server implementation that supports XEP-0198 on server connections, with Openfire opening the connection and with Openfire accepting it. Openfire writes both sides of this protocol, so tests between two Openfire servers cannot catch a bug that both sides share.
- [ ] Documentation, admin console and ADRs reflect the implemented behaviour.

---

## Open Questions

- Whether phases 5 and 6 (sessions 14-24) are worth building. Their only benefit is accuracy after a break (see Background), for the stanzas that are unacknowledged at that moment, and no other server is known to support resumption on server connections. This can be decided once phase 4 is done, as phases 1-4 are useful without resumption.
- Which other server implementation(s) to use for interoperability testing. [Prosody's mod_smacks](https://hg.prosody.im/trunk/file/tip/plugins/mod_smacks.lua) supports Stream Management on server connections (enabled by default with `smacks_enabled_s2s`), but not resumption: it issues no resumption ID for them, and does not request resumption on its outgoing connections. Support in ejabberd, MongooseIM and Tigase is unconfirmed. As far as known, resumption can therefore only be tested between Openfire servers.
- Default server resumption timeout: 1 or 2 minutes (decision 5).
- Default maximum size of the queue of new stanzas while resuming (decision 7).
- Final defaults of the new settings once the work is complete (session 25): should Stream Management and resumption on server connections be enabled by default?
- Names of the new system properties. The keys in this plan are proposals.
- Whether `stream.management.location.terminate-others.enabled` (which removes detached client sessions on other cluster nodes when a client fails to resume) should have an equivalent for server sessions.
- How to prevent messages from being handled twice when they are delivered on a connection that has closed (found in session 2, exists on `main`). A closed connection hands a stanza to its backup deliverer, which puts a message in offline storage, while `StreamManager` keeps the stanza as unacknowledged. The message is then handled a second time: `StreamManager#onClose` routes it again when the session is not resumed, or it is sent again on the new connection when the session is resumed. This happens in two places:
  - `StreamManager#redeliverUnackedStanzas`, when the connection of a resumed session closes while its unacknowledged stanzas are sent again.
  - `LocalClientSession#pushPackets()`, between the moment that the connection of a resumable session closes and the moment that its close listener detaches the session, which runs asynchronously.

  OF-3434 avoids this only after the maximum number of unacknowledged stanzas was exceeded: `StreamManager#sentStanza` then does not track a stanza that a closed connection hands to its fallback, and the check for the maximum and the re-delivery on resumption happen under one lock. Phase 6 sends stanzas again on resumed outgoing connections in the same way, so this needs to be resolved before then. A ticket is to be raised once the phases before that have shown whether more is involved.
- Which session an outgoing connection authenticated with SASL EXTERNAL uses (found in session 9, exists on `main`). `RespondingServerStanzaHandler` completes `isSessionAuthenticated` while it processes `<success/>`, and `NettySessionInitializer#waitForSession` reads the session of the handler at that moment. That is the session from before the stream restart. The stream header of the restarted stream then makes the handler replace it with a new session on the same connection (`transferConnectionToNewSession`), which adds the server route for itself. `LocalOutgoingServerSession#authenticateDomain` continues with the old session on another thread: it adds the server route for that one too, and registers its close listener for it. Which session ends up as the route depends on which thread gets there last. Both deliver over the same connection, so this has gone unnoticed, but Stream Management is negotiated on the new session, and its `StreamManager` records the outcome. Session 10 waits for that outcome, and session 11 tracks stanzas on the session that sends them, so both need the session on which Stream Management was negotiated to be the one that is returned and routed.
- How a stream manager should answer a Stream Management element that it does not process (found while planning session 5b, exists on `main`). `StreamManager#sendError` answers with `<failed/>` and disables Stream Management. After that, stanzas are no longer tracked, and `StreamManager#onClose` does not handle the unacknowledged stanzas it already holds: messages are not routed again, and IQ requests get no error response. For the end that asks, session 9 avoids this. For clients, changing it changes behaviour, so it needs its own ticket.
- Sessions 3 and 4 cannot leave `StreamManagerTest` entirely unchanged, as their exit criteria assume. The test sets the private counters `handledInboundStanzas` and `acknowledgedOutboundStanzas` by name (`HANDLED_COUNTER_FIELD` and `ACKNOWLEDGED_COUNTER_FIELD`), as sending 2^32 stanzas is not feasible in a unit test. It also calls `validateOutboundAcknowledgement` directly. Session 3 updated these names (they were `serverProcessedStanzas`, `clientProcessedStanzas` and `validateClientAcknowledgement`). Session 4 moved the counter of acknowledged stanzas and the validation into `UnacknowledgedStanzaQueue`: the test now reaches that counter through the queue (`unacknowledgedOutboundStanzas.acknowledged`), and calls `UnacknowledgedStanzaQueue#isValidAcknowledgement` instead. Only these references should change; the expected behaviour in the tests should not.
