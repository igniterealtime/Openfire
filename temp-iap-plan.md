# Initial Authentication Pipelining (XEP-0509) Implementation Plan

## Scope and Goals
- Add XEP-0509 support for client-to-server authentication flows that use SASL2 in Openfire.
- Keep behavior fully backward-compatible for existing non-pipelining clients.
- Ensure the advertised `config-version` changes whenever server-side configuration affects initial authentication behavior.
- Cover wire behavior and configuration/version behavior with automated tests.

## Relevant Existing Code Paths
- SASL2 feature advertisement: `xmppserver/src/main/java/org/jivesoftware/openfire/net/SASLAuthentication.java` (`getSASLMechanismsElement(..., true)`).
- C2S stream negotiation and SASL state transitions:
  - `xmppserver/src/main/java/org/jivesoftware/openfire/net/StanzaHandler.java`
  - `xmppserver/src/main/java/org/jivesoftware/openfire/net/SocketReadingMode.java`
  - `xmppserver/src/main/java/org/jivesoftware/openfire/websocket/WebSocketClientStanzaHandler.java`
- Pre/post-auth stream feature composition:
  - `xmppserver/src/main/java/org/jivesoftware/openfire/session/LocalClientSession.java`
  - `xmppserver/src/main/java/org/jivesoftware/openfire/http/HttpSession.java`
- Existing SASL2 unit tests:
  - `xmppserver/src/test/java/org/jivesoftware/openfire/sasl/SASLAuthenticationTest.java`

## High-Level Design

### 1) Wire Protocol Support (XEP-0509)
1. Add a dedicated `InitialAuthenticationPipelining` helper (new class) responsible for:
- Determining if pipelining is enabled.
- Building/parsing XEP-0509 metadata on SASL2 `authentication` feature.
- Validating `config-version` supplied by clients.

2. Extend SASL2 feature advertisement:
- In `SASLAuthentication.getSASLMechanismsElement(..., usingSASL2=true)`, append XEP-0509 data when enabled.
- Keep deterministic ordering of child elements and attributes.

3. Add pipelined request handling in a single place:
- Parse and validate XEP-0509 data in `SASLAuthentication.handle(...)` (SASL2 path), not in transport handlers.
- Keep `StanzaHandler` and `SocketReadingMode` as transport/state orchestration only (they pass the stanza through as they do now).
- Preserve current behavior for non-pipelined clients and SASL1.

4. Ensure transport parity:
- Apply equivalent behavior for WebSocket flow (`WebSocketClientStanzaHandler`) so websocket clients see the same SASL2 + pipelining capabilities.

5. Backward compatibility & safety:
- Guard all XEP-0509 behavior behind a new dynamic property (default `false` or aligned with your SASL2 rollout policy).
- Keep stanza validation strict; reject malformed or stale pipelining requests per spec.

## Config-Version Strategy

### Recommended Default (A): Canonical Hash of Effective Initial-Auth Features
Use a deterministic hash of the *effective* initial authentication feature set, generated just before advertising SASL2 authentication.

Implementation notes:
- Build a canonical representation from the feature model (not raw XML text).
- Include only data that can impact initial authentication semantics.
- Sort all unordered collections (mechanisms, attributes, child feature descriptors).
- Hash with SHA-256, encode as lowercase hex or base64url.
- Cache per node with short-lived invalidation/event-based invalidation.

Why this is best:
- Automatically tracks changes without manually maintaining a property allowlist.
- Robust against future additions to initial auth capabilities.
- Easy to assert in tests (same input => same version; changed feature => changed version).

### Option B: Event-Driven Monotonic Version Counter
Maintain an integer/long `config-version` bumped by property listeners whenever relevant config changes.

Pros:
- Cheap to compute and compare.
- Easy operational introspection.

Cons:
- High risk of missing a relevant change unless all contributors are wired correctly.
- Harder to keep correct as new features/mechanisms are added.

### Option C: Hybrid (Counter + Hash)
Expose version as `<epoch>-<hash>` where:
- `epoch` increments on known events.
- `hash` is canonical feature hash.

Pros:
- Combines quick coarse invalidation with correctness.
- Easier debugging in clustered/mixed-version rollouts.

Cons:
- Slightly more complex implementation and test surface.

### What To Include In Hash Input
Include:
- Enabled SASL2 mechanisms actually offered to this session context.
- TLS policy impact on offered mechanisms (e.g., EXTERNAL only if valid cert context).
- XEP-0509-related toggles.
- Any initial-auth inline features (bind2/sm/etc.) and required parameters.

Concrete hash input list (normalized key/value model):
- `sasl2.enabled`: boolean (`xmpp.auth.sasl2`).
- `iap.enabled`: boolean (`xmpp.auth.sasl2.pipelining`).
- `sasl2.mechanisms`: sorted list of offered SASL2 mechanisms after policy filtering for this connection context.
- `tls.state-class`: one of `unencrypted|encrypted-no-client-cert|encrypted-trusted-client-cert` (captures EXTERNAL offerability conditions without using cert identity data).
- `inline.bind2.enabled`: boolean (when Bind2 support is present).
- `inline.bind2.required`: boolean (if server policy can require bind2 semantics).
- `inline.sm.resumption.enabled`: boolean (if stream management resumption can be negotiated inline at auth time).
- `inline.sm.limits`: stable tuple of advertised SM-relevant limits that affect auth-time negotiation (only those that alter accept/reject behavior).
- `inline.csi.enabled`: boolean, only if CSI is actually part of initial-auth inline negotiation (otherwise exclude).
- `inband-registration.enabled-during-initial-auth`: boolean if this affects initial auth pipeline acceptability.
- `auth-policy.anonymous-allowed`: boolean.
- `auth-policy.external-client-cert-policy`: normalized enum representing policy gates that affect offered/accepted auth paths.
- `server-capability-set.version`: optional static implementation fingerprint, bumped only when code-level initial-auth feature semantics change across releases.

Normalization rules:
- Canonical serialization with stable field ordering and explicit field names.
- Sorted values for unordered collections.
- Omit fields that are not applicable in current runtime (or include with explicit `null` marker, but do this consistently).
- Do not include identities/secrets/ephemeral values (stream ID, timestamps, raw cert subject, JID/resource).

Exclude:
- Per-connection ephemeral data (stream id, timestamps).
- Post-auth-only features unrelated to initial auth.
- Presentation-only ordering/whitespace.

## Detailed Implementation Steps
1. Create `InitialAuthenticationPipelining` service/helper in `org.jivesoftware.openfire.net`.
2. Add new properties:
- `xmpp.auth.sasl2.pipelining` (dynamic boolean).
- Optional: `xmpp.auth.sasl2.pipelining.config-version.strategy` (`hash|counter|hybrid`) if you want pluggability now.
3. Add canonical feature model + serializer + hasher.
4. Integrate with `SASLAuthentication.getSASLMechanismsElement(..., true)` to advertise `config-version` and pipelining capability data.
5. Update auth handling paths with single-point validation:
- `SASLAuthentication.handle(...)`:
- Parse pipelined input from SASL2 `<authenticate/>`.
- Validate `config-version`.
- Execute pipelined operations in strict order with fail-fast behavior.
- `StanzaHandler`, `SocketReadingMode`, `WebSocketClientStanzaHandler`:
- No protocol parsing logic for `config-version`; keep these focused on stream/frame lifecycle.
6. Ensure no behavior changes when:
- SASL2 disabled.
- Pipelining disabled.
- Client omits pipelining data.
7. Add logging (debug/trace) for:
- Advertised config-version.
- Config-version mismatch decisions.
- Accepted/rejected pipelined requests.

## Test Plan

### Unit Tests
1. `SASLAuthenticationTest` additions:
- SASL2 `authentication` feature includes XEP-0509 markers when enabled.
- `config-version` is present when expected.
- Deterministic output (repeated generation stable for unchanged config).

2. New tests for canonical version computation (new test class, e.g. `InitialAuthenticationPipeliningTest`):
- Same feature set in different insertion order => same hash.
- Change one relevant input (mechanism toggle/property) => version changes.
- Change irrelevant/ephemeral input => version unchanged.

3. Parser/validator unit tests:
- Valid client-provided config-version accepted.
- Mismatch rejected with correct error handling path.
- Missing/malformed pipelining payload handled safely.
- Verification that both socket-based flows (`StanzaHandler` and `SocketReadingMode`) and websocket flow behave identically because they share `SASLAuthentication.handle(...)` logic.

### Flow / Integration-Style Tests
1. Extend SASL2 authentication flow tests to include pipelined request path:
- Successful SASL2 + pipelined operation in one round trip.
- Failure when pipelined operation conflicts with current advertised initial features.

2. WebSocket parity tests:
- Ensure websocket stream feature advertisement includes same SASL2 pipelining metadata as TCP path.

3. Regression tests:
- Existing SASL2 behavior remains unchanged when pipelining is disabled.
- SASL1 and non-SASL flows unaffected.

## Acceptance Criteria
- Server advertises XEP-0509 metadata on SASL2 authentication feature when enabled.
- Pipelined authentication requests are processed correctly and safely.
- `config-version` changes whenever effective initial-auth configuration changes.
- `config-version` remains stable for irrelevant changes.
- All new tests pass; no regressions in existing SASL/SASL2 tests.

## Rollout / Risk Mitigation
- Introduce behind feature flag, default off initially.
- Add targeted debug logging for early deployments.
- Document operational knobs and expected client behavior in admin/developer docs.
- After confidence period, consider default-on for SASL2-enabled deployments.

## Suggested Sequencing
1. Implement canonical config-version generator + unit tests.
2. Wire feature advertisement in `SASLAuthentication` + unit tests.
3. Implement pipelined request parsing/processing in `SASLAuthentication.handle(...)` + flow tests.
4. Verify transport parity for TCP blocking/non-blocking and websocket paths + parity tests.
5. Run full relevant test suite and document behavior.
