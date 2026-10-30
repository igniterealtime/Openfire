---
generated_by: Claude Opus 5.5
generation_date: 2026-10-23
model_version: claude-opus-5-5
purpose: implementation_plan
status: draft
human_reviewer: guusdk
session_status:
  session_1_characterise_acks: not-started
  session_2_characterise_resumption: not-started
  session_3_rename_inbound_outbound: not-started
  session_4_extract_unacked_queue: not-started
  session_5_isolate_client_resumption: not-started
  session_6_server_settings: not-started
  session_7_advertise_incoming: not-started
  session_8_incoming_ack_tests: not-started
  session_9_outgoing_negotiation: not-started
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

Prerequisite: OF-3420 fixes thread-safety problems in the existing implementation and must be merged before phase 5.

## Background

Openfire currently supports Stream Management for client connections only. In that setup, the client always starts Stream Management: it enables it and requests resumption, while Openfire only responds. Openfire has never taken the client's role, and the existing implementation contains no code for it.

On incoming server connections, where a remote server sends stanzas to Openfire, Openfire would have the same responding role as with clients. The existing implementation already handles most of the protocol there (`StanzaHandler` passes Stream Management elements to `StreamManager` for every session type), but Openfire does not advertise support for it. Outgoing server connections have no support at all. As a result, when an outgoing connection breaks today, Openfire loses the stanzas that the remote server had not yet received.

For clients, a broken session is detached by the client session close listener in `SessionManager` and cleaned up after `xmpp.session.detach.timeout` (default 10 minutes). When a client session closes without being resumed, `StreamManager#onClose` routes unacknowledged messages again and discards all other unacknowledged stanzas.

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
4. Do not change production code. If a test exposes a bug, record the current behaviour and add the bug to the plan's Open Questions.

**Exit:** New tests pass against unchanged production code, and cover the behaviour listed above.

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
4. Do not change production code. If a test exposes a bug, record the current behaviour and add the bug to the plan's Open Questions.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: New tests pass against unchanged production code, and cover the behaviour listed above.

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
4. Add a test that records that `StreamManager#onClose` routes unacknowledged messages again and discards other stanzas.
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
4. Add a test that records that StreamManager#onClose routes unacknowledged messages again and discards other stanzas.
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

### Phase 2: Acknowledgements on incoming connections

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

**Goal:** Advertise Stream Management to remote servers when the setting is enabled, and handle their requests for acknowledgement.

**Files:**
- `main/session/LocalIncomingServerSession.java`
- `main/streammanagement/StreamManager.java`
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

### Session 9: Enable Stream Management on outgoing connections

**Goal:** Let Openfire enable Stream Management on server connections it opens, taking the role clients have today.

**Files:**
- `main/net/RespondingServerStanzaHandler.java`
- `main/session/LocalOutgoingServerSession.java`
- `main/streammanagement/StreamManager.java`

**Tasks:**
1. When the setting from session 6 is enabled and the remote server offers the feature after authentication, send `<enable/>` (without `resume` for now).
2. Process `<enabled/>` and `<failed/>`, and record the outcome on the session's `StreamManager`.
3. An `<r/>` from the remote server needs no meaningful answer (stanzas only flow one way), but must not break the connection.
4. Add unit tests with a mocked connection.

**Exit:** With the setting enabled, Openfire enables Stream Management on outgoing connections to servers that offer it, and does not try with servers that don't; all tests pass.

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

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: With the setting enabled, Openfire enables Stream Management on outgoing connections to servers that offer it, and does not try with servers that don't; all tests pass.

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

**Tasks:**
1. Record each stanza sent on an outgoing session with Stream Management enabled in the queue from session 4.
2. Send `<r/>` at the same frequency as for clients, and process `<a/>`.
3. On a broken connection, behave as today (handling unacknowledged stanzas follows in phase 4).
4. Add unit tests with a mocked connection that feed `<a/>` to the session.

**Exit:** Acknowledged stanzas are removed from the queue; invalid acknowledgements close the connection as for clients; tests pass.

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

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: Acknowledged stanzas are removed from the queue; invalid acknowledgements close the connection as for clients; tests pass.

When you're done, set this session's entry in the plan's session_status frontmatter to "completed (YYYY-MM-DD)".
```

---

### Phase 4: Unacknowledged stanzas after an outgoing connection breaks

### Session 12: Send again or return unacknowledged stanzas

**Goal:** Implement decision 4 for outgoing connections: after a break, send unacknowledged stanzas again or return them to the sender, depending on their type.

**Files:**
- `main/session/LocalOutgoingServerSession.java`
- `main/streammanagement/StreamManager.java`
- `test/session/LocalOutgoingServerSessionStreamManagementTest.java`

**Tasks:**
1. When an outgoing session with Stream Management closes without resumption, route messages, presences and IQ results/errors again, so that they go out over a new connection.
2. Return IQ get/set requests to the sender as an error (proposed condition: `recipient-unavailable`).
3. Keep the client close behaviour (`StreamManager#onClose`) unchanged.
4. Add unit tests.

**Exit:** After a break, each stanza type is handled as decision 4 describes; connections without Stream Management behave as today; client tests from phase 1 pass.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 12.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md (decision 4)

Goal: After an outgoing connection breaks, send unacknowledged stanzas again or return them to the sender, depending on their type.

Working directory: Openfire repository root

Tasks:
1. When an outgoing session with Stream Management closes without resumption, route messages, presences and IQ results/errors again, so that they go out over a new connection.
2. Return IQ get/set requests to the sender as an error (proposed condition: recipient-unavailable).
3. Keep the client close behaviour (StreamManager#onClose) unchanged.
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

Requires OF-3420 to be merged.

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
3. Look up the kept session and reattach it to the new connection, using `LocalSession#reattach`.
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
3. Look up the kept session and reattach it to the new connection, using LocalSession#reattach.
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

**Tasks:**
1. Open a new connection from the node that holds the session, authenticate the main domain, and send `<resume/>` with the recorded `id` and the inbound count.
2. On `<resumed/>`, attach the new connection to the session, remove the stanzas acknowledged by its `h`, and send the remaining unacknowledged stanzas (marked as sent again, decision 4), then the queued ones, in order.
3. Leave stanzas for extra domains waiting (session 23) and leave giving up (session 24) for later; for now, a failed attempt closes the session as in phase 4.
4. Add unit tests that feed `<resumed/>` to the handler and check the order of what is delivered to the mocked connection.

**Exit:** After a break, the session resumes, the stanzas the remote server did not receive arrive first, followed by the queued ones, in order; tests pass.

**Session Prompt:**
```
I'm implementing OF-2425: Stream Management for server-to-server connections. This is Session 22.

Read the engineering plan at doc/plan/2026-10-23-stream-management-s2s.md (decision 7)

Goal: Reconnect and resume a detached outgoing session, then send what is missing in order.

Working directory: Openfire repository root

Tasks:
1. Open a new connection from the node that holds the session, authenticate the main domain, and send <resume/> with the recorded 'id' and the inbound count.
2. On <resumed/>, attach the new connection to the session, remove the stanzas acknowledged by its 'h', and send the remaining unacknowledged stanzas (marked as sent again, decision 4), then the queued ones, in order.
3. Leave stanzas for extra domains waiting (session 23) and leave giving up (session 24) for later; for now, a failed attempt closes the session as in phase 4.
4. Add unit tests that feed <resumed/> to the handler and check the order of what is delivered to the mocked connection; do not use the remote server dummies.

When the exit criteria are met, commit the change. Write the commit message as described by the commit-message skill.

Exit when: After a break, the session resumes, the stanzas the remote server did not receive arrive first, followed by the queued ones, in order; tests pass.

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
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/streammanagement/StreamManager.java` | Existing XEP-0198 implementation (client role of Openfire: responding) |
| Openfire | `xmppserver/src/main/java/org/jivesoftware/openfire/streammanagement/ResumeRequest.java` | Parsed resume request |
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

### Phase 1: Refactor (Sessions 1-5)
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
