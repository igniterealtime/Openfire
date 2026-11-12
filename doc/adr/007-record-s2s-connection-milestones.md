# ADR-007: Record the Milestones of Every Outbound S2S Connection Attempt

## Status

Accepted

## Context

The admin console's S2S Connection Test (OF-3439) reports a server-to-server connection attempt as a checklist of 11 checks: the domain entered, then the steps of the attempt (DNS, the TCP connection, the XMPP stream, TLS, the remote certificate, authentication, the session and a ping with its reply), then the warnings and errors logged. Each step needs a reliable answer to "did this happen in this attempt, and how did it end?".

Before this change, the test closed existing sessions to the domain, routed an XMPP ping, and showed three raw outputs: the stanzas, the certificates and everything the server logged during the test. Measured against two Openfire servers in a federation test environment:

- With default logging, the log captured for a successful test is 6 lines; DNS, TCP, the stream, TLS and outbound SASL EXTERNAL do not appear in it. With trace logging it is 247 lines.
- A DNS failure and a TCP failure produce identical output.
- A certificate name mismatch is logged in the main log, but not in the per-attempt diagnostic log that OF-3037 added for failed attempts.
- Every failure shows a `remote-server-not-found` error that Openfire generated itself, addressed as if from the remote domain.

The test does not make the connection itself. It routes a ping, and the connection attempt runs on the `OutgoingSessionPromise` thread pool, through the same code as any federation traffic. Whatever records the steps must therefore live in that code path, and must not change how a connection proceeds.

## Options Considered

### Option 1: Derive the Steps from the Log

Capture what the server logs during the test, as the test already did, and interpret the lines to decide each step.

**Consequences**:

- ✅ No change to the connection code
- ❌ Most steps are not logged at default log levels, so the result would depend on the administrator's logging configuration
- ❌ Log wording is not a stable interface: any change to a log message would silently change a check
- ❌ Lines from other activity on the server interleave with the attempt's own lines, and nothing in a line ties it to the attempt
- ❌ DNS and TCP failures cannot be told apart from the existing lines

### Option 2: Run Separate DNS and TCP Probes from the Test

Before routing the ping, have the test resolve the domain and open a TCP connection itself, and report those results for the first checks.

**Consequences**:

- ✅ Simple to implement, with no change to the connection code for those two checks
- ❌ A probe can disagree with the real attempt, which uses Happy Eyeballs, direct TLS SRV records (`_xmpps-server`) and DNS overrides
- ❌ Covers only the first two steps; the stream, TLS, certificate and authentication still need another source

### Option 3: Record Milestones Only for Attempts the Test Starts

Pass a recorder from the test into the connection code, so that only test attempts record their steps.

**Consequences**:

- ✅ No recording cost for ordinary federation traffic
- ❌ The test does not start the attempt: it routes a ping, and `OutgoingSessionPromise` makes the attempt on its own thread pool, so there is no call through which to hand the recorder over without changing how routing starts sessions
- ❌ A failed attempt outside the test (the OF-3037 failed-session page) would gain nothing

### Option 4: Record the Milestones of Every Outbound Attempt (CHOSEN)

Give every outbound S2S attempt a thread-safe `AttemptRecorder`. `OutgoingSessionPromise` creates it and keeps it as the latest attempt for the domain pair before calling `LocalOutgoingServerSession.authenticateDomain`. The recorder travels to the connection code as a Netty channel attribute (`NettyConnection.ATTEMPT_RECORDER`, following the `DOMAIN_PAIR` precedent), so no handler constructor changes. The code that decides each step records its outcome at that point: `SocketUtil` (DNS and per-address connection outcomes), `NettySessionInitializer`, `NettyOutboundConnectionHandler` (TLS handshake and certificate decision) and `RespondingServerStanzaHandler` (stream, STARTTLS and authentication).

**Consequences**:

- ✅ Each check is judged on a fact recorded during this attempt, at any log level
- ✅ The steps are the real attempt's, including Happy Eyeballs, direct TLS and DNS overrides, and the S2S access policy applies as for any connection
- ✅ The OF-3037 failed-session page gains one line per step for any failed attempt, not only for tests
- ✅ Each recording hook is testable with the existing outbound session test harness
- ❌ Every outbound attempt carries a recorder, and the connection code gains recording calls that future changes must keep accurate. A forgotten call breaks nothing at run time; the safeguards below catch it in the build, or make the test report it as a close rather than a wrong cause
- ❌ Uses extra memory on each cluster node to keep the latest attempt per domain pair: about 1.5 KB for a typical attempt, for at most 1000 pairs; at most about 41 KB for an attempt in which the remote pushes every value it controls to its limit (see Performance)

## Decision

Every outbound S2S connection attempt records its milestones in an `AttemptRecorder`, written by the connection code at the point each step is decided. The S2S Connection Test builds its checklist from the recorder of the attempt that started during the test, from the state of the resulting session and from the outcome of the ping. It does not interpret log text and does not probe separately.

Two related choices follow from the same principle, that a check is judged only on what this attempt did:

- **A reply is classified by where the ping went.** Openfire routes its own `remote-server-not-found` error through the same router as a reply from the remote, and the incoming side cannot tell them apart. The outgoing side can: `LocalSession.process` passes the session to the packet interceptors when a stanza is sent. If the ping was seen leaving over an outgoing server session that was still open, a reply came from the remote; if it never left, any reply is this server's own. The test takes the reply through an `IQRouter` result listener, so it reaches the test whichever cluster node receives it.
- **Recording never changes the connection.** Every recording call on the connection path is wrapped in `AttemptRecorder.safely`, so a failure while recording is logged at debug level and the connection proceeds as it would have without it.
- **A failure path that records nothing fails the build.** When the connection closes or this server stops waiting, the recorder fills in only that, against the step under way, without saying which side closed the connection; the order of the steps is kept in one place (`AttemptRecorder.stepUnderWay`). The failure exits of `RespondingServerStanzaHandler` that end initiation go through one method that records the failure (`failInitiation`). The settings matrix in `LocalOutgoingServerSessionTest` fails when a failed attempt's first recorded failure is such a filled-in close or wait, so a failure path that forgets to record breaks the build there. None of this stops a new exit from closing the connection directly; the matrix test is the backstop.

### How It Fits Together

Recording and reading are separate. Every outbound connection records its attempt, whether or not a test started it; the S2S Connection Test and the OF-3037 failed-session page each read the record.

```
EVERY OUTBOUND S2S CONNECTION (normal federation, or one the test starts)

  OutgoingSessionPromise
    creates an AttemptRecorder for the attempt and keeps it as the
    latest for the domain pair (LatestAttempts, up to 1000 pairs per node)
        |
        v
  DNS --> TCP --> XMPP stream --> TLS --> certificate --> authentication
   *       *          *            *          *                *
   * = the code that decides the step records its outcome there.
       The recorder travels with the connection (a Netty channel
       attribute); it observes only, and a failure while recording
       never changes the connection.

WHO READS THE RECORD

  S2S Connection Test page (on demand)       Failed-session page (OF-3037)
   1. close existing sessions to the domain   a failed attempt's diagnostic
   2. send an XMPP ping, which makes          log now ends with one line
      Openfire connect out and record         per step, whether or not a
      the attempt                             test started the attempt
   3. wait for the reply and for the
      attempt to finish
   4. read the record of the attempt this
      test started
   5. show 11 checks, each judged only on
      what was recorded: nothing recorded
      means "Not observed", never Pass
```

## Rationale

The steps the checklist needs happen inside the connection code, and nowhere else is there a reliable record of them. Options 1 and 2 report something other than the real attempt: Option 1 depends on log configuration and wording, and Option 2 measures a different connection, which can disagree with the real one. Option 3 has no way to reach an attempt that the test does not start. Recording on every attempt is the only option that observes the real attempt without changing how sessions are started, and the same record improves the diagnostics for failed attempts outside the test.

## Consequences

### What Becomes Easier

- An administrator can see which step of a connection failed, and why, without trace logging or XMPP expertise
- Failed attempts outside the test show the same steps on the OF-3037 failed-session page
- A new failure case can be reported by recording one more outcome where it is decided, and tested in isolation

### What Becomes Harder

- Changes to the outbound connection code must keep the recorded milestones accurate; a step that stops being recorded shows "Not observed" in the checklist
- The recording calls add code to the connection path that reviewers must read

### Performance

Measured on Temurin 21 with `System.nanoTime` loops, `ThreadMXBean` allocation counts and the heap retained after garbage collection; the figures are within about 20%.

- Nothing runs per stanza: recording happens only while a connection is set up
- Each outbound attempt costs about 0.5 µs of CPU and 3.5 to 3.9 KB of short-lived allocation, against about 1 ms for a loopback TLS handshake alone
- There is no measurable lock contention. The only shared lock, that of `LatestAttempts`, is held for about 50 ns per attempt stored, inside the per-domain-pair lock that is already held for the whole attempt; storing attempts runs at 1.8 million per second on 1 thread and 2.8 million per second on 256
- When a certificate chain is rejected after the TLS handshake, explaining why costs one extra PKIX path build, about 0.2 ms
- A typical attempt kept as the latest for its domain pair retains about 1.5 KB, so about 1.5 MB for 1000 domain pairs
- With every value that the remote controls at its largest (10 DNS targets and 10 certificate names of 253 characters, text cut to 200 characters) and the diagnostic log filling its 16,000 characters with long lines, an attempt retains about 27 KB, so about 27 MB for 1000 domain pairs. When that text holds characters outside Latin-1, which Java then stores at two bytes each, it is about 41 KB, so about 41 MB. The log cap counts characters, so thousands of very short lines would cost more, but the number of lines is set by the connection code (a few tens per attempt), not by the remote
- A failed-attempt record in the clustered cache (OF-3037) grows from about 1.1 KB to 1.5 KB, with the lines that describe each step

### Memory and Clustering

- `OutgoingSessionPromise` keeps the latest attempt for each domain pair, on success as well as failure, for up to 1000 pairs per cluster node, evicting the pair stored least recently. An outbound connection also holds its attempt until the connection closes, so an evicted attempt whose connection is still open stays in memory: the bound is 1000 attempts plus one for each open outbound connection
- The remote decides how much some milestones hold (its DNS answer lists the targets, each of which can resolve to several addresses), so these are bounded within each attempt: at most 10 DNS targets, 10 addresses tried and 10 names of a certificate that does not match the domain, each followed by a count of the rest; text the remote sends, cut to 200 characters; and diagnostic log lines of at most 500 characters each, at most 16,000 characters in all, of which a longer log keeps whole lines from its start (up to 4,000 characters) and from its end (the rest), with one line saying how many were left out. A cut is marked with ASCII dots, so that the text keeps Java's one-byte-per-character form. The names of a certificate that does not match the domain are kept only for a certificate that passed this server's trust check. The diagnostic log that the clustered failure record keeps (OF-3037) is not capped, as before this change
- The structured record lives only on the node that made the attempt. The clustered `FailedOutgoingServerSessionAttempt` keeps its serialised form as plain text lines, into which the recorder renders its milestones, so clusters running mixed versions are unaffected
- When another cluster node holds the route to the remote domain, the steps of its attempt are not visible on this node, and the checklist shows them as "Not observed". That node also sends the ping, so this node cannot see whether it left; only a result reply is judged then, and an error or a missing reply leaves the ping and reply checks "Not observed"

### Known Limitations

- The test identifies its attempt as the latest attempt for the domain pair that started after the test did. An attempt started in the same window by other traffic for the same domain pair cannot be told apart from the test's own. This is unlikely, as the test closes the sessions to the domain and routes the ping itself.
- The plain-connection Server Dialback fallback, used after a failed TLS handshake when Server Dialback is enabled and the TLS policy is not "required" (and, when the handshake failed on the certificate, strict certificate validation is off), opens a second connection inside `ServerDialback` without the recorder. Its outcome is recorded by the outbound connection handler, but the steps of that second connection are not, and the warnings `ServerDialback` logs count as other warnings. Recording inside `ServerDialback` would change code shared by inbound and outbound callers for one rare path.

## References

- **OF-3439**: Admin console S2S connection test: show which step of the connection failed, and why
- **OF-3037**: Admin console page to review failed S2S connection attempts
- **RFC 6120, section 3.2**: Resolution of Fully Qualified Domain Names, including the fallback to the domain itself when no SRV records exist - https://datatracker.ietf.org/doc/html/rfc6120#section-3.2
- `doc/plans/2026-11-02-s2s-connection-test-checklist.md`: the implementation plan, with the rules for each check
- `documentation/s2s-connection-test-guide.html`: the administrator's guide to the test
