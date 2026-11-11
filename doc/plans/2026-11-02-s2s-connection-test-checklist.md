---
generated_by: Claude Opus 5.5
generation_date: 2026-11-02
model_version: claude-opus-5-5
purpose: implementation_plan
status: draft
human_reviewer: matthewvivian
implementation_tracking: completed
tags: [s2s, admin-console, diagnostics, federation]
jira_ticket: OF-3439
branch: OF-3439
session_status:
  session_1_checklist_model: completed (2026-11-02)
  session_2_results_page: completed (2026-11-02)
  session_3_attempt_recorder: completed (2026-11-02)
  session_4_dns_and_tcp: completed (2026-11-02)
  session_5_stream_tls_certificate: completed (2026-11-02)
  session_6_authentication: completed (2026-11-03)
  session_7_edge_cases_and_log_check: completed (2026-11-03)
  session_8_demonstration_and_coverage: completed (2026-11-03)
  session_9_end_user_documentation: completed (2026-11-03)
---

# S2S connection test checklist: show which step of a server-to-server connection failed, and why

## Summary

Rework the admin console's server-to-server connection test (Sessions, Tools, S2S Connection Test) so the result is a checklist of 11 checks, each with a pass, warning, failure, not-reached or not-observed status, and a specific explanation on the check that failed. The checks come from milestones recorded during the real connection attempt and from the state of the resulting session, not from interpreting log text.

## Background

The existing tool (`S2STestService` and `server-connectiontest.jsp`) closes existing sessions to the domain, sends an XMPP ping and shows three raw text boxes: the stanzas, the certificates and everything the server logged during the test. Reading those boxes needs XMPP expertise, and the requested improvement is a tick for each check that passes and a specific diagnostic for the one that fails. OF-3037 added a per-attempt diagnostic log for failed outbound attempts, shown on the failed-session details page.

Measured with the openfire-docker-compose federation environment (two Openfire servers, 5.2.0-SNAPSHOT):

- With default logging, the captured log for a successful test is 6 lines; DNS, TCP, stream, TLS and outbound SASL EXTERNAL do not appear. With trace logging, 247 lines show every step.
- A DNS failure and a TCP failure produce identical output.
- A certificate name mismatch is logged in the main log but not in the OF-3037 diagnostic log.
- Every failure shows a `remote-server-not-found` error that our own server generated, addressed as if from the remote domain.
- The certificate box prints the public key under a `BEGIN CERTIFICATE` header.
- The captured log lines carry no level, so warnings cannot be told from informational lines.

## The 11 checks and their rules

| # | Check | Pass | Warning | Fail |
|---|---|---|---|---|
| 1 | Target XMPP domain valid | Valid domain, not local, not a component, allowed by S2S policy | | Invalid, local, a component of this server, or blocked by policy (shown as a failed row, not only an error box) |
| 2 | DNS resolved to server address | Addresses found: from SRV records, from a DNS override configured on this server, or, when there are none, from the domain name itself (the RFC 6120 fallback); the detail says which | | No address for the domain |
| 3 | TCP connection established to an S2S endpoint | Connected, with no address of a higher DNS priority failing before (a failure beside it in the same priority, such as an unreachable IPv6 address, does not count); shows the address and port used | Connected only after every address of a higher DNS priority failed, each listed with what happened (up to 10); or connected to the domain name itself although the domain publishes SRV records (RFC 6120 says not to fall back then), with the failures, or saying that no server the records name had an address to try | Refused, or timed out, each with its own hint; failed for another reason, or in different ways at different addresses (each address shown with what happened); or no address attempted, as each found uses a connection type this server does not accept |
| 4 | XMPP server stream opened | Remote opened a stream; shows the name it gave itself, or that it gave none | | Remote closed the connection, stayed silent until this server stopped waiting (the wait is shown), answered without a stream ID, or sent a stream error before TLS (the error condition and text are shown) |
| 5 | TLS negotiated successfully | Protocol and cipher shown | The connection went ahead unencrypted, as this server's STARTTLS policy allows (check 6 then shows "Not reached", as no certificate is exchanged) | Not offered (when this server requires TLS), refused by the remote, handshake failed (with reason), stopped because this server rejected the remote certificate (check 6 says why), required by the remote while this server's policy disables TLS, over direct TLS, the handshake not completed before this server stopped waiting; or, over STARTTLS, the stream opened but TLS was not set up before this server stopped waiting, or the remote closed the connection first |
| 6 | Remote certificate received, valid, trusted and matches target | All four hold | | Which of the four failed: not received, expired or not yet valid, untrusted, revoked, or names do not match |
| 7 | Authentication succeeded using SASL EXTERNAL | Authenticated with SASL EXTERNAL | Authenticated with Server Dialback because the remote refused or did not offer certificate login, or for a reason not recorded (after the plain-connection fallback) | EXTERNAL refused with dialback disabled; dialback refused; no method both servers can use; a stream error during authentication; dialback over a plain connection, tried after a failed TLS handshake, also failed; authentication not completed before this server stopped waiting, or the remote closed the connection first; the connection closed after the remote accepted SASL EXTERNAL but before it restarted the stream; a restarted stream refused for reusing the previous stream ID (with `xmpp.server.outgoing.accept-reused-stream-id` false) or for lacking one |
| 8 | Authenticated outgoing S2S session registered | Session registered and authenticated | | No session |
| 9 | XMPP ping successful | A reply came back from the remote server itself, of any type | | Timed out (hint: the remote may be unable to connect back to us): no reply within the socket timeout, or, when the session was set up only after that wait ran out, within 5 more seconds; or only our own server answered |
| 10 | Expected response received | The reply was a result | The reply was an error that still proves the round trip (for example service-unavailable), condition named | None: without a reply from the remote, check 9 has failed and this check is not reached |
| 11 | No errors appear in the log | No warnings or errors logged for this attempt | Warnings logged for this attempt | Errors logged for this attempt |

After the first failure, a check shows "Not reached" unless its step still ran: Openfire can carry on past some failures (dialback after our server rejects the remote certificate), and a step that ran shows its own result. A session, ping or response check that would fail right after a failed step is its consequence and shows "Not reached"; once a later step has passed or warned (the attempt carried on), they show their own result. A step under way when the remote closed the connection, or when this server stopped waiting, fails (the stream, TLS or authentication, whichever had not completed); a stream error the remote sent, when it arrives, replaces that inferred failure. If check 1 fails, no attempt is made and every other check shows "Not reached"; otherwise check 11 is judged after any failure, once the attempt has finished and handed over its diagnostic log; while it is still running, check 11 shows "Not observed". A check whose step did not happen in this attempt (for example because an existing session was reused, or another cluster node holds the route) shows "Not observed", never a pass: check 7 is judged only on what the attempt recorded, never on the state of the session. When the server logged other warnings or errors during the test, check 11's detail line also gives their number, marked as possibly unrelated. A warning or error is this attempt's when the attempt's diagnostic log holds it, or when the connection code that logged it (the outbound connection handler, the stream handler, the session initialiser and `SocketUtil`) also counted it against the attempt. Over direct TLS the handshake, and the certificate with it, comes before the stream, so a failed handshake leaves check 4 "Not reached".

Relaxed certificate settings (accepting self-signed certificates, not checking validity dates) never make a certificate count as valid: they apply only to the TLS handshake, and check 6 uses the full certificate check (`TrustStore.getEndEntityCertificate`). Check 6 therefore shows the real reason, red. With strict certificate validation off, the connection carries on with Server Dialback, which check 7 reports.

## Design Decisions

### 1. Checks come from recorded milestones and session state, not log text
- The server's logs at default levels do not hold most steps, and log wording is not a stable interface.
- Rationale: reliable at any log level, and testable.

### 2. Record on every outbound attempt
- The test only routes a ping; the connection attempt runs on the `OutgoingSessionPromise` thread pool, so the tool cannot hand a test-only recorder to it.
- `OutgoingSessionPromise` creates the recorder and stores it as the latest attempt before calling `LocalOutgoingServerSession.authenticateDomain`, so an attempt still in progress can be read. `authenticateDomain` records a reused session as well as a new one, and passes the recorder through `createOutgoingSession` to `NettySessionInitializer`, which sets it as a channel attribute (`NettyConnection.ATTEMPT_RECORDER`, following the `DOMAIN_PAIR` precedent), so no handler constructor changes.
- The test uses an attempt only if it started after the test did, so an earlier attempt's milestones are never reported as this test's.
- It is thread-safe: handlers write from the blocking handler executor while the caller waits on a future and may time out first.
- `OutgoingSessionPromise` keeps the latest attempt per domain pair, on success as well as failure (a success still clears the failure record).
- The clustered `FailedOutgoingServerSessionAttempt` keeps its serialised form as plain text lines; the recorder renders its milestones into those lines, so the OF-3037 details page improves and clusters running mixed versions are unaffected. The structured checklist is built only from the live recorder on the node that runs the test.

### 3. No separate DNS or TCP probe
- DNS results and per-address connection outcomes are recorded inside `SocketUtil` during the real attempt.
- Rationale: a separate probe bypasses the S2S access policy (an admin could probe any host and port), can disagree with the real attempt (happy eyeballs, `_xmpps-server` direct TLS, DNS overrides), and cannot report a DNS failure because `DNSUtil` always adds a fallback record.

### 4. A reply is classified by where it came from
- Openfire routes the `remote-server-not-found` error it generates itself through the same router as a reply from the remote, and the routers pass the interceptor a client-session lookup that is null for both, so the incoming side cannot tell them apart.
- The outgoing side can: `LocalSession.process` invokes the interceptors when a stanza is sent over a session, passing that session. If the interceptor saw the ping sent over an outgoing server session that was still open, the reply came from the remote; if the ping never left, any reply is our own. Before routing the ping, the test waits (bounded by `xmpp.server.outgoing.close-wait-timeout`) for the teardown of the session it closed, so the ping is not handed to that session.
- The reply itself comes through an `IQRouter` result listener, which receives it whichever cluster node it arrives on. When the session is set up only after the reply wait ran out, the test waits up to 5 more seconds for the reply.
- In a cluster, a route held by another node sends the ping from that node, which this node's interceptor does not see. Over such a route only a result (which the remote alone sends) is judged; an error or a missing reply leaves checks 9 and 10 "Not observed".

### 5. Admin console: servlet plus JSP
- A servlet in `org.jivesoftware.admin.servlet` runs the test and builds the checklist; the JSP only renders it, using `admin:infobox`, `jive-table` and the existing status icons (with an empty alt text, as the status label sits beside each icon).
- Every value that the remote server or the request controls is rendered with `c:out` or `fn:escapeXml`; i18n messages hold no markup and use placeholders.
- The three raw outputs stay, collapsed, below the checklist; the certificate output shows the certificate, not its public key; the captured log lines carry their level.

### 6. Existing behaviour kept
- The test still closes existing sessions to the domain first, so it observes a fresh connection. The form warns that this interrupts federation with that domain.

### 7. Suggested next steps
- Below the table, a "Suggested next steps" box advises on the first failed check, or, when none failed, on each warning. `S2SAdvice` chooses the advice by the result's message key: the advice for that message when there is one, otherwise the advice for the check and status (for example `certificate.fail`). A test checks that every failure and warning message has advice. When check 5 fails because this server rejected the remote certificate, the advice is that of check 6, which says why. Advice for one exact message can fill in that result's values (the name-mismatch advice names the domain and the names in the certificate); a test checks that it uses only values the result carries.
- Advice leaves out "On this server" where nothing on this server can change the result (an expired or revoked remote certificate). Every certificate failure but revocation carries one note: settings on the Server to Server settings page can let such a connection go ahead using Server Dialback, they apply to every remote server, and the guide describes them under check 6. The note does not prescribe them, as relaxing them weakens the checks for all federation. A revoked certificate gets no note and no admin console link: its issuer withdrew it, and nothing on this server should change.
- DNS overrides are rarely configured, so the advice mentions one only when check 2 shows that one was used and check 3, 4 or 5 then failed, naming where it sent the connection; otherwise only check 2 (no address) and the check 3 warning point to them. Each piece of advice puts the most likely cause first: a review traced every text to the code and judged how often each cause occurs in practice.
- Each piece of advice says what to look at on this server, links to the admin console pages that hold those settings (fixed addresses in `S2SAdvice.AdminPage`, never from message text), and, where it helps, names possible causes on the remote server. The test sees only this server's side, so the remote causes are possibilities for the administrator to weigh; the page does not offer text to send to the remote administrator, which could state as fact what the test only infers.

---

## Human checkpoints

- After session 2: review the page and the rules table with the project lead before the recording sessions.
- Before the pull request: link the pull request to OF-3439.

## Build and test

Inside a git worktree, Maven needs `-Dmaven.gitcommitid.skip=true`. Both commands build `i18n` with `xmppserver`, so that the tests read the current messages rather than an installed copy.

```
./mvnw -pl i18n,xmppserver test -Dmaven.gitcommitid.skip=true -Dtest='<TestClass>' -Dsurefire.failIfNoSpecifiedTests=false   # focused tests
./mvnw -pl i18n,xmppserver verify -Dmaven.gitcommitid.skip=true                                                              # all xmppserver tests
```

Manual testing uses the `federation` environment of openfire-docker-compose. Build the image from a `git archive` export of the commit (a worktree's `.git` file breaks the image build), tag it `openfire:s2s-checklist`, and start it with `federation/start.sh -n s2s-checklist`. The environment's certificates must be current for certificate-based authentication to succeed. It enables trace logging by default; turn it off (`log.trace.enabled=false`) to see what a default installation shows.

---

## Implementation Sessions

### Session 1: Checklist model and rules

**Goal:** A pure Java model that turns recorded milestones, session facts and the ping outcome into the 11-row checklist, with the rules in the table above.

**Files:**
- `xmppserver/src/main/java/org/jivesoftware/openfire/server/s2stest/` (new package; names indicative): `S2SCheck` (enum of the 11 checks), `CheckStatus` (PASS, WARNING, FAIL, NOT_REACHED, NOT_OBSERVED), `CheckResult` (status, i18n key, parameters), `S2SChecklist`, `S2SChecklistBuilder`, `AttemptMilestones` (interface or value type the recorder will fill), `PingOutcome`
- `xmppserver/src/test/java/org/jivesoftware/openfire/server/s2stest/S2SChecklistBuilderTest.java`

**Tasks:**
1. Write the tests first, one per rule in the table: each pass, warning and failure; "Not reached" after the first failure; "Not observed" for steps absent from a reused session; dialback fallback as a warning; EXTERNAL refused with no fallback as a failure; error reply as ping pass and response warning; our own `remote-server-not-found` as a ping failure; log check levels.
2. Implement the model and builder with no dependency on `XMPPServer` or other singletons.
3. Results carry i18n keys and plain parameters, never HTML.

**Exit:** All builder tests pass; the package has no static access to server singletons.

**Session Prompt:**
```
I'm implementing OF-3439, the S2S connection test checklist. This is Session 1.

Read the engineering plan at doc/plans/2026-11-02-s2s-connection-test-checklist.md, especially "The 11 checks and their rules".

Goal: a pure Java model that turns recorded milestones, session facts and the ping outcome into the 11-row checklist.

Working directory: the Openfire repository root, on branch OF-3439.

Tasks:
1. Write the tests first, one per rule in the table (pass, warning, failure, not reached, not observed, dialback warning, error-reply rules, log levels).
2. Implement the model and builder in a new package with no static access to XMPPServer or other singletons.
3. Results carry i18n keys and plain parameters, never HTML.

Do NOT commit changes. When complete, print a suggested commit message titled with the Jira key (`OF-3439: ...`), whose body explains why the change is made.

Exit when: the builder tests pass and the new package has no singleton access.

When you're done, update the plan file to mark this session as done.
```

---

### Session 2: Results page from what the server already knows

**Goal:** The new results page, end to end, filling the checks that need no connection-code change and showing "Not observed" for the rest.

**Depends on:** 1

**Files:**
- `xmppserver/src/main/java/org/jivesoftware/admin/servlet/S2SConnectionTestServlet.java` (new, `@WebServlet`)
- `xmppserver/src/main/java/org/jivesoftware/util/S2STestService.java` (returns a result object; interceptor classifies replies by session; certificate output fixed; appender layout includes the level)
- `xmppserver/src/main/webapp/server-connectiontest.jsp`, renamed to `server-connectiontest-view.jsp`: the servlet takes over the page's URL, so the sidebar entry is unchanged
- `i18n/src/main/resources/openfire_i18n.properties`
- Tests for the servlet's input validation and for `S2STestService`'s reply classification and certificate formatting

**Tasks:**
1. Tests first: domain validation (invalid, local, component, blocked by policy) produces a failed check 1; reply classification (remote result, remote error, our own error, timeout); certificate output is PEM of the certificate.
2. Move validation and the test run into the servlet; the JSP renders the checklist with `admin:infobox`, `jive-table` and existing icons with alt text; escape every remote- or request-controlled value.
3. Fill checks 1, 7 (method only), 8, 9, 10 from session state and the interceptor; others "Not observed".
4. Keep the three raw outputs, collapsed; add the "closes existing sessions" warning to the form.
5. Build an image and check in the federation environment: success against `xmpp2.localhost.example`; failure against `db2` (resolves, nothing listening).

**Exit:** Tests pass; the page works in the federation environment for success and failure.

**Session Prompt:**
```
I'm implementing OF-3439, the S2S connection test checklist. This is Session 2.

Read the engineering plan at doc/plans/2026-11-02-s2s-connection-test-checklist.md.

Goal: the new results page, end to end, filling the checks that need no connection-code change.

Working directory: the Openfire repository root, on branch OF-3439.

Tasks:
1. Write tests first for domain validation, reply classification (remote result, remote error, our own error, timeout) and certificate output.
2. Add S2SConnectionTestServlet; the JSP renders only, using admin:infobox, jive-table and existing status icons with alt text; escape every remote- or request-controlled value.
3. Fill checks 1, 7 (method only), 8, 9 and 10; others show Not observed.
4. Keep the raw outputs collapsed; add the warning that the test closes existing sessions.
5. Verify in the federation environment (success against xmpp2.localhost.example, failure against db2).

Do NOT commit changes. When complete, print a suggested commit message titled with the Jira key (`OF-3439: ...`), whose body explains why the change is made.

Exit when: tests pass and the page works for both cases.

When you're done, update the plan file to mark this session as done.
```

---

### Session 3: Attempt recorder

**Goal:** Every outbound S2S attempt records its milestones in a thread-safe recorder that the test can read afterwards, on success and on failure.

**Depends on:** 1

**Files:**
- `xmppserver/src/main/java/org/jivesoftware/openfire/session/LocalOutgoingServerSession.java` (`authenticateDomain`, `createOutgoingSession`)
- `xmppserver/src/main/java/org/jivesoftware/openfire/session/DomainAuthResult.java`
- `xmppserver/src/main/java/org/jivesoftware/openfire/server/OutgoingSessionPromise.java` (latest attempt per domain pair)
- `xmppserver/src/main/java/org/jivesoftware/openfire/nio/NettySessionInitializer.java` (channel attribute)
- New recorder class in the session 1 package
- `xmppserver/src/test/java/org/jivesoftware/openfire/session/LocalOutgoingServerSessionTest.java`

**Tasks:**
1. Tests first: extend `LocalOutgoingServerSessionTest` (which already runs a matrix of TLS, certificate and dialback settings against `RemoteReceivingServerDummy`) to assert a recorder exists for each attempt and records start and outcome.
2. Implement the recorder (thread-safe), attach it as a channel attribute in `NettySessionInitializer.init`, return it through `DomainAuthResult`, keep the latest attempt per domain pair in `OutgoingSessionPromise`.
3. Render milestones into the existing diagnostic log lines; leave the serialised `FailedOutgoingServerSessionAttempt` form unchanged.
4. Wire the servlet to read the latest attempt for the tested domain pair.

**Exit:** The extended matrix passes; a successful attempt's record is readable after the session is established.

**Session Prompt:**
```
I'm implementing OF-3439, the S2S connection test checklist. This is Session 3.

Read the engineering plan at doc/plans/2026-11-02-s2s-connection-test-checklist.md, especially design decision 2.

Goal: every outbound S2S attempt records its milestones in a thread-safe recorder that the test can read afterwards, on success and failure.

Working directory: the Openfire repository root, on branch OF-3439.

Tasks:
1. Tests first: extend LocalOutgoingServerSessionTest to assert a recorder per attempt with start and outcome.
2. Implement the recorder; attach it as a Netty channel attribute in NettySessionInitializer.init (follow NettyConnection.DOMAIN_PAIR); return it via DomainAuthResult; keep the latest attempt per domain pair in OutgoingSessionPromise.
3. Render milestones into the existing diagnostic log lines; do not change FailedOutgoingServerSessionAttempt's serialised form.
4. Wire the servlet to read the latest attempt.

Do NOT commit changes. When complete, print a suggested commit message titled with the Jira key (`OF-3439: ...`), whose body explains why the change is made.

Exit when: the extended test matrix passes and a successful attempt's record is readable.

When you're done, update the plan file to mark this session as done.
```

---

### Session 4: DNS and connection milestones

**Goal:** Checks 2 and 3 report what DNS returned and what happened with each address, so "no such domain", "connection refused" and "timed out" read differently.

**Depends on:** 3

**Files:**
- `xmppserver/src/main/java/org/jivesoftware/openfire/net/SocketUtil.java` (`createSocketToXmppDomain`)
- `xmppserver/src/main/java/org/jivesoftware/openfire/nio/NettySessionInitializer.java`
- Session 1 builder and i18n keys
- Tests using `DNSUtil.setDnsOverride` and a closed local port

**Tasks:**
1. Tests first: resolved records (SRV versus fallback, both a pass) and per-address outcomes are recorded; refused and timed out are distinguished; the address finally used is recorded.
2. Record inside `SocketUtil` and `NettySessionInitializer` (including the direct-TLS restart in `exceptionCaught`, which re-runs `init` on a new channel).
3. Federation environment: `nonexistent.invalid` and `db2` now produce different checks 2 and 3.

**Exit:** Tests pass; the two failures are distinguishable on the page.

**Session Prompt:**
```
I'm implementing OF-3439, the S2S connection test checklist. This is Session 4.

Read the engineering plan at doc/plans/2026-11-02-s2s-connection-test-checklist.md.

Goal: checks 2 and 3 report what DNS returned and what happened with each address.

Working directory: the Openfire repository root, on branch OF-3439.

Tasks:
1. Tests first (DNSUtil.setDnsOverride, a closed local port): SRV versus fallback records, refused versus timed out, the address used.
2. Record inside SocketUtil.createSocketToXmppDomain and NettySessionInitializer, including the direct-TLS restart path.
3. Federation environment: nonexistent.invalid and db2 must now read differently.

Do NOT commit changes. When complete, print a suggested commit message titled with the Jira key (`OF-3439: ...`), whose body explains why the change is made.

Exit when: tests pass and the two failures are distinguishable.

When you're done, update the plan file to mark this session as done.
```

---

### Session 5: Stream, TLS and certificate milestones

**Goal:** Checks 4, 5 and 6 say whether the stream opened, whether TLS was set up, and exactly which certificate property failed.

**Depends on:** 3

**Files:**
- `xmppserver/src/main/java/org/jivesoftware/openfire/nio/NettyOutboundConnectionHandler.java` (`userEventTriggered`: handshake outcome, `SASLAuthentication.verifyCertificates`, strict-validation abort)
- `xmppserver/src/main/java/org/jivesoftware/openfire/net/RespondingServerStanzaHandler.java` (stream header, STARTTLS offered or required)
- `xmppserver/src/main/java/org/jivesoftware/openfire/net/StanzaHandler.java` (remote stream errors)
- Tests in `LocalOutgoingServerSessionTest`

**Tasks:**
1. Tests first: stream opened; STARTTLS absent or refused; handshake failures classified as untrusted, expired, not yet valid, revoked; name mismatch; remote stream error text captured.
2. Record each milestone at the point it is decided.
3. Federation environment: prove where untrusted and expired certificates fail (expected during the TLS handshake, before check 6) and make the rows and hints match.

**Exit:** Tests pass; untrusted, expired and mismatched certificates give distinct results.

**Session Prompt:**
```
I'm implementing OF-3439, the S2S connection test checklist. This is Session 5.

Read the engineering plan at doc/plans/2026-11-02-s2s-connection-test-checklist.md.

Goal: checks 4, 5 and 6 report the stream, TLS and the exact certificate failure.

Working directory: the Openfire repository root, on branch OF-3439.

Tasks:
1. Tests first: stream opened, STARTTLS absent or refused, handshake failure classified (untrusted, expired, not yet valid, revoked), name mismatch, remote stream error text.
2. Record in NettyOutboundConnectionHandler.userEventTriggered, RespondingServerStanzaHandler and StanzaHandler.
3. Federation environment: prove where untrusted and expired certificates fail and make the rows and hints match.

Do NOT commit changes. When complete, print a suggested commit message titled with the Jira key (`OF-3439: ...`), whose body explains why the change is made.

Exit when: tests pass and untrusted, expired and mismatch results are distinct.

When you're done, update the plan file to mark this session as done.
```

---

### Session 6: Authentication milestones

**Goal:** Check 7 distinguishes certificate login, fallback to dialback (warning) and failed authentication, with the reason.

**Depends on:** 3

**Files:**
- `xmppserver/src/main/java/org/jivesoftware/openfire/net/RespondingServerStanzaHandler.java` (`processUnknowPacket`: EXTERNAL offered or not, success, failure, dialback fallback)
- `xmppserver/src/main/java/org/jivesoftware/openfire/server/ServerDialback.java` (`createOutgoingSession`: the fallback opens a second connection through `SocketUtil`)
- Tests in `LocalOutgoingServerSessionTest`

**Tasks:**
1. Tests first: EXTERNAL success; EXTERNAL not offered then dialback; EXTERNAL refused then dialback; EXTERNAL refused with dialback disabled; dialback refused. A stream error after TLS (for example in answer to the SASL attempt) is recorded against authentication; the recorder leaves check 4 as opened.
2. Record the milestones; the plain-connection dialback fallback records only its outcome, from the outbound handler (decided later; see Open Questions and Decisions).
3. Federation environment: dialback fallback (remote does not trust our certificate) shows the warning; with dialback disabled on our side, the same case fails.

**Exit:** Tests pass; both cases match the rules table.

**Session Prompt:**
```
I'm implementing OF-3439, the S2S connection test checklist. This is Session 6.

Read the engineering plan at doc/plans/2026-11-02-s2s-connection-test-checklist.md.

Goal: check 7 distinguishes certificate login, fallback to dialback (warning) and failed authentication.

Working directory: the Openfire repository root, on branch OF-3439.

Tasks:
1. Tests first: EXTERNAL success, not offered then dialback, refused then dialback, refused with dialback disabled, dialback refused. A stream error after TLS fails authentication; check 4 stays opened.
2. Record in RespondingServerStanzaHandler.processUnknowPacket and on ServerDialback's fallback connection.
3. Federation environment: the dialback fallback shows a warning; with dialback disabled it fails.

Do NOT commit changes. When complete, print a suggested commit message titled with the Jira key (`OF-3439: ...`), whose body explains why the change is made.

Exit when: tests pass and both cases match the rules table.

When you're done, update the plan file to mark this session as done.
```

---

### Session 7: Edge cases and the log check

**Goal:** The checklist never shows a pass it did not earn, and check 11 follows the agreed rule.

**Depends on:** 2, 4, 5, 6

**Files:**
- Session 1 builder; `S2STestService`; `LocalOutgoingServerSession` (session reuse paths); `OutgoingSessionPromise` (route held by another cluster node); certificate trust settings read from `OpenfireX509TrustManager` configuration

**Tasks:**
1. Tests first: a reused session (dialback piggybacking, or a route on another cluster node) shows "Not observed" for steps it skipped; accepting self-signed certificates still leaves check 6 failed with the real reason; direct TLS records TLS before the stream; check 11 counts warnings and errors for this attempt and reports other server warnings in the window as information. A remote that closes the connection, or stays silent, before opening a stream is recorded as check 4 failing (tell a close by the remote from this server giving up after the initialise timeout); a stream header without a stream ID (the OF-2692 path) is recorded.
2. Implement; confirm two tests run at the same time do not mix their records.

**Exit:** Tests pass; no path yields a green check without a recorded milestone from this attempt.

**Session Prompt:**
```
I'm implementing OF-3439, the S2S connection test checklist. This is Session 7.

Read the engineering plan at doc/plans/2026-11-02-s2s-connection-test-checklist.md.

Goal: the checklist never shows a pass it did not earn, and check 11 follows the agreed rule.

Working directory: the Openfire repository root, on branch OF-3439.

Tasks:
1. Tests first: reused sessions show Not observed; accepting self-signed certificates still leaves check 6 failed; direct TLS order; check 11 rule with other server warnings as information. Remote close or silence before the stream, and a stream header without an ID, fail check 4.
2. Implement; confirm two simultaneous tests do not mix their records.

Do NOT commit changes. When complete, print a suggested commit message titled with the Jira key (`OF-3439: ...`), whose body explains why the change is made.

Exit when: tests pass and no path yields an unearned pass.

When you're done, update the plan file to mark this session as done.
```

---

### Session 8: Demonstration and coverage

**Goal:** Every success and failure scenario demonstrated against the federation environment, and coverage of the change measured.

**Depends on:** 7

**Tasks:**
1. Run each scenario and confirm it matches the rules table: success; no DNS; nothing listening; firewall timeout; untrusted certificate; expired certificate; name mismatch; dialback fallback; EXTERNAL refused with dialback disabled; ping error reply.
2. Run with trace logging off, so the results reflect a default installation.
3. Run JaCoCo on `xmppserver` and record coverage of changed and added code.
4. Security review of the change (escaping, CSRF, input validation) before the pull request.

**Exit:** Every scenario matches the rules table; coverage recorded; security review done.

**Results (2026-11-03):** run against the federation environment on the branch image, with trace logging off, testing from xmpp1. Extra domains came from DNS overrides on xmpp1.

| Scenario | Set-up | First failure and what the page said |
|---|---|---|
| Success | as committed | All 11 checks pass |
| No DNS | `nowhere.localhost.example` | Check 2: no address found |
| Nothing listening | `db2` (port 5269 closed) | Check 3: refused |
| Firewall timeout | override to 192.0.2.1, which drops packets; `xmpp.server.read.timeout` 30 s | Check 3: timed out, firewall hint |
| No route to host | override to an unused address on the lab network | Check 3: failed, with the system's reason |
| Not an XMPP service | override to PostgreSQL on db2:5432 | Check 4: closed without a stream |
| Untrusted certificate | xmpp2 removed from xmpp1's trust store | Check 5: stopped by the certificate; check 6: untrusted |
| Expired certificate | xmpp2 given an expired leaf from a lab authority that xmpp1 trusts | Check 5: stopped by the certificate; check 6: expired, with the date |
| Name mismatch | xmpp2 given a leaf for other.example.org from that authority | Check 6: names other.example.org; check 5 passes |
| Dialback fallback | xmpp1 removed from xmpp2's trust store | Check 7 warns (EXTERNAL not offered); check 9 fails, as xmpp2 cannot connect back |
| Dialback disabled | as above, and dialback disabled on xmpp1 | Check 7: no method both servers can use |
| Ping error reply | override `pubsub.xmpp2.localhost.example` to xmpp2 | Check 9 passes; check 10 warns with service-unavailable |

Every row matches the rules table. Findings fixed in this session:
- The test stopped waiting for the ping at the moment a connection that timed out gave up, as both use the same timeout, so check 3 read "Not observed". The test now waits up to 10 more seconds for an attempt that is still in progress.
- An operating system can time out a connection before Java does, which Java reports as `ConnectException` like a refusal; the message now tells the two apart.
- Docker Desktop answers a connection to most unroutable addresses with a reset after 75 seconds, which Java reports as refused. The page shows what the network said; 192.0.2.1 gives a real timeout.
- "EXTERNAL refused with dialback disabled" does not arise against Openfire: it offers EXTERNAL only to a peer it trusts, so the same set-up reads as no common method.
- Cosmetic: for no route to host, the JDK's message repeats the address after the page's own.

Coverage (JaCoCo, `-P coverage`, 3142 tests): 89.9% of the executable lines the branch adds or changes in `xmppserver` (868 of 965). The model package is at 91 to 100% per class. The gaps are where a running server is needed: `S2STestService.run` (63%), `LocalOutgoingServerSession.authenticateDomain` (35%), `OutgoingSessionPromise` and `DomainAuthResult`; the scenarios above exercise them.

Security review (independent reviewer, whole branch): no high or medium findings. Fixed: a malformed or unknown error condition in the ping reply ended the test with a generic error; the log appender and interceptor were not removed safely if adding them or reloading the logging configuration failed; recording hooks outside the outbound handler had no guard against an exception; control characters from the remote could forge lines in the diagnostic log. Noted and unchanged: the raw log and stanza outputs show everything logged and exchanged with the domain during the test, as before; admins see where a domain's SRV records point, which federation already connects to.

**Session Prompt:**
```
I'm implementing OF-3439, the S2S connection test checklist. This is Session 8.

Read the engineering plan at doc/plans/2026-11-02-s2s-connection-test-checklist.md.

Goal: every scenario demonstrated against the federation environment, and coverage measured.

Working directory: the Openfire repository root, on branch OF-3439.

Tasks:
1. Run each scenario listed in the plan and confirm it matches the rules table.
2. Run with trace logging off.
3. Run JaCoCo on xmppserver; record coverage of changed and added code.
4. Run a security review of the change.

Do NOT commit changes. When complete, print a suggested commit message titled with the Jira key (`OF-3439: ...`), whose body explains why the change is made.

Exit when: every scenario matches the rules table, coverage is recorded and the security review is done.

When you're done, update the plan file to mark this session as done.
```

---

### Session 9: End-user documentation

**Goal:** An administrator can learn from Openfire's own documentation what the S2S Connection Test does, what each of the 11 checks verifies, and what each result means.

**Depends on:** 7 (final wording), 8 (scenarios)

**Files:**
- `documentation/s2s-connection-test-guide.html` (new; same structure as the other guides: header, link back to the index, one `section` per topic, `style.css`)
- `documentation/index.html` (entry under Advanced Server Administration)
- `documentation/ssl-guide.html` and `documentation/network-configuration-guide.html` (link to the guide where they discuss server-to-server connections)
- `documentation/images/` (screenshots)

**Tasks:**
1. Write the guide:
   - What the test does: a real outbound connection attempt to the domain, followed by a ping. It first closes existing sessions to that domain, which interrupts federation with it.
   - How to run it, and how to read the summary line and the table, including "Not reached" and "Not observed".
   - One section per check: what it verifies, every message it can show (quoted exactly from `openfire_i18n.properties`), what each tells the administrator, and the configuration settings that affect it (for example strict certificate validation, Server Dialback, the TLS policy and DNS overrides).
   - The raw details (XMPP, certificates, log), and the OF-3037 failed-session page that shows the same steps for attempts outside the test.
   - Limitations: the test sees only this server's side; a route held by another cluster node; an expired certificate that is trusted directly reads as untrusted.
2. Take success, warning and failure screenshots in the federation environment, with trace logging off, and add them under `documentation/images/`.
3. List the guide in `index.html` and link it from the server-to-server sections of the SSL and network configuration guides.
4. Check every quoted message against `openfire_i18n.properties`, and every link.

**Exit:** The guide is listed in the index; every message it quotes matches the i18n file; links resolve.

**Results (2026-11-03):** `documentation/s2s-connection-test-guide.html` documents every message the page can show. A script compared every quoted message with `openfire_i18n.properties` and checked every link and image of the guide and of the pages that link to it. The screenshots were taken in the federation environment on the branch image with trace logging off: success, a ping error reply (warning) and an untrusted certificate (failure). Fixed while writing the guide:
- "Retry now" on the failed-session page opens the test with the domain in the address. The old page put it in the form; the servlet ignored it and showed an empty form. It fills the form again, and the test still runs only when the form is submitted.
- No code path recorded the "Authentication failed" message or its outcome, so both were removed.

**Reviews (2026-11-03):** independent security, code and documentation reviews of the whole branch. The security review found nothing above Low: no injection into the page or logs, CSRF and access control sound, memory bounded, and recording never changes a connection or trust decision. Fixed after the reviews:
- The test read the attempt before it had finished (the outcome was set before the diagnostic log was stored), so in the timeout case check 11 could read "Not observed". An attempt now finishes when its diagnostic log is recorded, which `authenticateDomain` does last; the test reads the attempt once, and the raw output shows this attempt's own diagnostic log (kept on the recorder, capped at about 16,000 characters, from its start and its end) instead of the domain's latest failure record.
- Check 7 was filled from the session's authentication method when the attempt recorded none, which could show a pass this attempt did not earn. It is now judged only on what the attempt recorded, and the test's start time is taken before the existing sessions are closed.
- A step under way when the remote closed the connection, or when this server stopped waiting, recorded nothing, so check 7 read "Not observed" and check 8 took the blame. It now fails TLS or authentication, with new messages; the remote's own stream error replaces that inferred failure.
- The certificate verdict called the trust store again, which logged its warning a second time on every connection with an untrusted chain; it now uses a variant that does not log.
- An expired or not yet valid intermediate certificate, rejected during the handshake, was reported as the remote server's own certificate expiring, with the intermediate's date. Only the server's own certificate is now named; an intermediate failure reads as untrusted.
- Check 3's "no address attempted" case and check 10's unreachable failure had English text or no path; the first has its own message, the second was removed. Check 4's pass message says the name is the one the remote gave itself, and the "Not observed" message names the realistic cause.
- Hardening: the servlet passes on an interruption, `SocketUtil` closes a failed socket before recording it, and the log appender is added inside the `try` that removes it. Unused code was removed (`DomainAuthResult`'s recorder, `AttemptMilestones.toBuilder`, a duplicate recording helper, an unused i18n key), and three weak tests were fixed.
- Kept as a limitation at the time, and fixed during the pull request review: a ping handed to a connection that closes at that moment could make this server's own error read as the remote's reply. The test now waits for the closed session's teardown before routing the ping, and a ping handed to a closed session does not count as sent.

**Session Prompt:**
```
I'm implementing OF-3439, the S2S connection test checklist. This is Session 9.

Read the engineering plan at doc/plans/2026-11-02-s2s-connection-test-checklist.md.

Goal: end-user documentation of the S2S Connection Test in documentation/.

Working directory: the Openfire repository root, on branch OF-3439.

Tasks:
1. Write documentation/s2s-connection-test-guide.html following the structure of the existing guides: what the test does and its side effect, how to read the results, one section per check with every message it can show, limitations.
2. Take success, warning and failure screenshots in the federation environment, with trace logging off.
3. List the guide in documentation/index.html; link it from ssl-guide.html and network-configuration-guide.html.
4. Check every quoted message against openfire_i18n.properties, and every link.

Do NOT commit changes. When complete, print a suggested commit message titled with the Jira key (`OF-3439: ...`), whose body explains why the change is made.

Exit when: the guide is listed in the index, its messages match the i18n file and its links resolve.

When you're done, update the plan file to mark this session as done.
```

---

## Critical Files

| File | Purpose |
|---|---|
| `xmppserver/src/main/java/org/jivesoftware/util/S2STestService.java` | Runs the test today |
| `xmppserver/src/main/java/org/jivesoftware/admin/servlet/S2SConnectionTestServlet.java`, `xmppserver/src/main/webapp/server-connectiontest-view.jsp` | The test page: the servlet runs the test, the view renders it |
| `xmppserver/src/main/java/org/jivesoftware/openfire/session/LocalOutgoingServerSession.java` | `authenticateDomain`, `createOutgoingSession`: where an attempt starts |
| `xmppserver/src/main/java/org/jivesoftware/openfire/server/OutgoingSessionPromise.java` | Runs attempts; keeps failure records |
| `xmppserver/src/main/java/org/jivesoftware/openfire/server/FailedOutgoingServerSessionAttempt.java` | Clustered failure record (OF-3037) |
| `xmppserver/src/main/java/org/jivesoftware/openfire/server/diagnostics/` | `AttemptRecorder`, `AttemptMilestones`, `LatestAttempts`, `CertificateVerdicts`: the record of each attempt |
| `xmppserver/src/main/java/org/jivesoftware/openfire/server/s2stest/` | The checklist and its rules |
| `xmppserver/src/main/java/org/jivesoftware/openfire/net/SocketUtil.java` | DNS resolution and connection attempts |
| `xmppserver/src/main/java/org/jivesoftware/openfire/nio/NettySessionInitializer.java` | Opens the outbound channel |
| `xmppserver/src/main/java/org/jivesoftware/openfire/nio/NettyOutboundConnectionHandler.java` | TLS handshake and certificate decision |
| `xmppserver/src/main/java/org/jivesoftware/openfire/net/RespondingServerStanzaHandler.java` | Stream features, SASL EXTERNAL, dialback |
| `xmppserver/src/main/java/org/jivesoftware/openfire/server/ServerDialback.java` | Dialback fallback connection |
| `xmppserver/src/test/java/org/jivesoftware/openfire/session/LocalOutgoingServerSessionTest.java` | Existing test matrix to extend |

---

## Verification Checklist

### Page and model (sessions 1 to 2)
- [x] Builder tests cover every rule in the table
- [x] Page renders success and failure in the federation environment
- [x] Every remote- or request-controlled value on the page is escaped

### Recording (sessions 3 to 6)
- [x] A record exists for successful and failed attempts
- [x] DNS failure, refused and timed out read differently
- [x] Untrusted, expired and mismatched certificates read differently
- [x] Dialback fallback shows a warning; with dialback disabled it fails
- [x] The OF-3037 failed-session page shows the richer lines; its serialised form is unchanged

### Edge cases and delivery (sessions 7 to 9)
- [x] Reused sessions never show unearned passes
- [x] Relaxed certificate settings leave check 6 showing the real reason
- [x] Every scenario matches the rules table
- [x] Coverage of changed and added code recorded
- [x] Security review done
- [x] End-user guide in documentation/, listed in the index, its messages matching the i18n file

---

## Open Questions and Decisions

- Session size: sessions 5 and 6 touch the most delicate code; split either if it runs long.
- An expired certificate that is trusted directly (a pinned self-signed certificate) reads as untrusted on check 6, with no date: `OpenfireX509TrustManager` leaves expired certificates out of its trust anchors (tested in `OpenfireX509TrustManagerTest.testInvalidChainExpiredTrustAnchor`), so the handshake error only says no chain was found. The untrusted message covers this case. Naming the expiry would need the trust manager to attach the end-entity validity failure as the cause of its rejection, without changing the decision.
- `RespondingServerStanzaHandler.processUnknowPacket` handles a STARTTLS `<failure/>` as a SASL failure, and then tries Server Dialback on the unencrypted stream when it is enabled. After a STARTTLS refusal or a stream error, the attempt waits for the whole initialise timeout before it gives up. Both are existing behaviour, left unchanged here; either may deserve its own ticket.
- Decided (2026-11-03): `ServerDialback` gets no attempt recorder. The plain-connection dialback fallback, used only after a failed TLS handshake with strict certificate validation off, TLS not required and dialback enabled, records its outcome on check 7 from the outbound handler; the warnings `ServerDialback` itself logs count as other warnings on check 11, and its second connection's DNS and TCP steps are not recorded. The outbound handler's own warning when the fallback fails does count for the attempt. The end-user guide lists this as a limitation. Recording inside `ServerDialback` would change code shared by five inbound and outbound callers for one rare path.
- `LocalOutgoingServerSessionTest` cannot make direct TLS connections (an existing TODO), so the direct TLS ordering is covered by the recorder and builder tests only. Session 8 can demonstrate it if the federation environment can be given an `_xmpps-server` record; whether a DNS override can select direct TLS has not been checked.
