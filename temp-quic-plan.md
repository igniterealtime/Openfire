# XMPP over QUIC (C2S) Plan

## Goals
- Add XMPP-over-QUIC support for client connections.
- First iteration uses one reliable QUIC stream per authenticated XMPP session.
- Integrate with existing client networking/session pipeline (reuse `ClientStanzaHandler`, SASL, stream management, routing, session lifecycle).
- Keep design extensible for future multi-stream-per-session support.

## Non-Goals (Iteration 1)
- No server-to-server QUIC.
- No multiple QUIC streams mapped to one XMPP session yet.
- No datagram/partially-reliable channels.
- No plugin API for QUIC-specific channel injection yet (can be added once core model stabilizes).

## Current Architecture Anchors
- Connection orchestration: `ConnectionManagerImpl`, `ConnectionListener`, `ConnectionAcceptor`.
- Socket acceptor implementation: `NettyConnectionAcceptor` + `NettyServerInitializer`.
- C2S business logic: `NettyClientConnectionHandler` -> `ClientStanzaHandler`.
- Transport abstraction: `Connection` / `AbstractConnection` / `NettyConnection`.

The QUIC design should plug into these same seams, not bypass them.

## High-Level Design

### 1) Add a new client connection type for QUIC
- Add `QUIC_C2S` to `ConnectionType`.
- Use `SOCKET_C2S` as fallback for certificate/trust defaults where useful.
- Add dedicated properties (port/enabled/threading/rate-limit/idle):
- `xmpp.quic.client.enabled`
- `xmpp.quic.client.port` (new default, do not reuse TCP C2S port)
- `xmpp.quic.client.idle`
- Optional: `xmpp.quic.client.max-streams` (set to 1 for now, but keep configurable)

### 2) Add QUIC listener lifecycle integrated with existing manager
- Extend `ConnectionManagerImpl` to create/start/stop a QUIC listener similar to other listeners.
- Add admin/config wiring so QUIC is visible as a first-class listener (enabled, port, cert/trust status).
- Preserve existing listener semantics (restart on config update, certificate reload handling).

### 3) Introduce a QUIC-specific acceptor and initializer
- Add `NettyQuicConnectionAcceptor` (or `QuicConnectionAcceptor`) extending `ConnectionAcceptor`.
- Use Netty QUIC codec (`io.netty.incubator.codec.quic`) and a UDP bootstrap.
- Configure:
- TLS 1.3 only (QUIC requirement)
- ALPN token for XMPP over QUIC (final token to be fixed and documented in code comments)
- Connection-level flow control and max streams (set bidi stream limit to 1 in iteration 1)
- New-connection rate limiting hooks equivalent to current `NewConnectionRateLimitHandler` behavior.

### 4) Reuse existing stanza processing with minimal adaptation
- Add `QuicConnection` implementing `Connection` (extend `AbstractConnection`), analogous to `NettyConnection`.
- Add `QuicClientConnectionHandler` that mirrors `NettyClientConnectionHandler` and creates `ClientStanzaHandler`.
- Decode/encode XML stanzas on the single reliable QUIC stream and pass `String` stanzas into the same `StanzaHandler.process(...)` path.
- Keep TLS state as "already encrypted" from connection start (no STARTTLS stage for QUIC transport).

### 5) Keep session model unchanged for iteration 1
- Continue 1 QUIC stream : 1 `LocalClientSession` mapping.
- Keep existing auth/SASL/SASL2/stream-management behavior unchanged at protocol layer.
- Ensure connection close semantics still invoke session cleanup listeners and routing-table removal exactly once.

## Future-Proofing for Multi-Channel Iteration

Design now to avoid repainting later:
- Introduce a small internal abstraction: `ClientTransportContext` (or similarly named), owned by session.
- In iteration 1, it wraps one inbound/outbound reliable channel.
- In iteration 2, it can track multiple active channel bindings under one session, each supporting inbound and outbound stanza traffic.
- Keep stanza dispatch path independent from underlying channel identifier, while preserving channel metadata for routing policy.
- Add stream/channel identifiers to connection-level trace logs now, even though only one exists.

Recommended preparatory boundaries:
- Keep QUIC stream-to-session binding logic in one class (eg. `QuicSessionBinder`) so multi-stream evolution is localized.
- Avoid scattering assumptions that transport == session.

Hard requirement for the future model:
- A single session must be able to both send and receive stanzas across multiple channels concurrently.
- Session logic must not assume a single reader or writer channel.
- Introduce a `SessionChannelMultiplexer` (name TBD) that:
- accepts inbound stanzas from any bound channel and forwards them to the existing stanza pipeline with channel context;
- selects outbound channel per stanza using a policy interface (initial policy can be trivial in iteration 1);
- handles channel add/remove without breaking the parent session.

Ordering and delivery semantics to define early:
- Preserve per-channel ordering (guaranteed by reliable channel semantics).
- Define cross-channel semantics explicitly (no implicit global ordering guarantee unless enforced by policy).
- Add optional sequencing hooks now (disabled in iteration 1) so future strict-order modes can be implemented without protocol churn.

## Protocol/Behavior Decisions to Lock Early
- ALPN identifier for XMPP-over-QUIC.
- Whether QUIC C2S should advertise/allow stream compression features (recommended: do not advertise transport compression over QUIC).
- How direct-TLS/legacy TLS policy flags map to QUIC listener semantics (recommended: QUIC implies encrypted transport, independent from TCP STARTTLS settings).
- Port defaults and coexistence behavior with existing C2S TCP listeners.

## Security and Ops Considerations
- Reuse existing identity/trust stores from `ConnectionConfiguration` to avoid split certificate management.
- Enforce TLS 1.3 cipher/protocol constraints suitable for QUIC.
- Ensure connection-level DoS controls exist for UDP-based handshake and stream creation.
- Add metrics:
- QUIC accepted/rejected connections
- active QUIC sessions
- handshake failures
- per-transport bytes in/out

## Implementation Phases

### Phase 1: Plumbing and feature-flagged listener
- Add `ConnectionType.QUIC_C2S`.
- Add config properties and listener definition in `ConnectionManagerImpl`.
- Add a disabled-by-default QUIC listener startup path.

### Phase 1.5: Admin Console Surface (early, but not required for first merge)
- Expose QUIC listener in admin connection settings pages (enabled flag, port, bind address, cert/trust overview, idle timeout, max streams).
- Add validation and conflict checks (port in use, invalid ranges, incompatible TLS options).
- Show runtime status in admin UI (running/stopped, bound address, active sessions/connections, recent handshake failures).
- Ensure admin changes follow existing listener lifecycle semantics (apply/restart behavior consistent with TCP listeners).

### Phase 2: QUIC transport runtime
- Add QUIC acceptor and channel initializer.
- Add `QuicConnection` and `QuicClientConnectionHandler`.
- Wire XML decode/encode pipeline to existing `ClientStanzaHandler`.

### Phase 3: Behavior parity and hardening
- Validate full auth flows (SASL/SASL2, pipelining).
- Validate stream restart behavior and session close semantics.
- Add rate limits/idle handling/logging/metrics parity.
- Introduce session-channel multiplexer scaffolding and channel-aware logging/context propagation (still single channel active in iteration 1).

### Phase 4: Admin visibility and docs
- Complete admin console support:
- add advanced QUIC tuning fields (flow control caps, stream limits, transport timeouts, optional rate-limit controls);
- add transport-specific troubleshooting panel/log hints (ALPN mismatch, handshake errors, rejected stream-open attempts);
- document safe defaults and operational guidance in admin help text/tooltips.
- Document client connection requirements and interoperability caveats.

## Test Plan

### Unit Tests
- `ConnectionType` and fallback behavior for `QUIC_C2S`.
- Listener config parsing/defaults for QUIC properties.
- `QuicConnection` close/notify behavior matches existing `Connection` contract.
- `QuicClientConnectionHandler` creates `ClientStanzaHandler` and forwards stanzas correctly.
- Admin form binding/validation tests for QUIC listener settings.

### Integration Tests (preferred with embedded Netty QUIC)
- Connect QUIC client, open stream, complete stream header negotiation.
- Complete SASL2 auth flow and resource binding over QUIC.
- Verify retries/error paths and stream errors mirror TCP behavior.
- Verify idle timeout closes session and cleanup occurs.
- Verify one-stream limit enforced in iteration 1.
- Add architecture tests around channel-multiplexer behavior in single-channel mode (channel registration, channel context propagation, clean detach).
- Admin config round-trip test: set QUIC listener config through admin endpoint/UI handler, verify persisted properties and listener restart behavior.

### Multi-Channel Readiness Tests (can start as disabled/spec tests)
- Session accepts inbound stanzas from multiple channels mapped to same session.
- Session can emit outbound stanzas on multiple channels per selection policy.
- Per-channel ordering preserved; cross-channel behavior matches defined policy.
- Channel failure does not implicitly destroy session when other channels remain healthy (policy-driven).

### Regression Tests
- Existing TCP C2S tests still pass.
- Existing websocket/BOSH behavior unaffected.
- Mixed transport operation (TCP + QUIC enabled simultaneously) works.

## Risks and Mitigations
- **Risk:** Netty QUIC introduces platform/native dependency complexity.
- **Mitigation:** keep QUIC listener optional; fail-fast startup diagnostics; CI profile for QUIC-enabled test job.

- **Risk:** Session cleanup divergence across transports.
- **Mitigation:** share `ClientStanzaHandler` and `Connection` lifecycle semantics; add explicit close-path tests.

- **Risk:** Future multi-stream redesign churn.
- **Mitigation:** isolate stream/session binding in dedicated classes now.

## Suggested First PR Scope
- Introduce `QUIC_C2S` type + config properties.
- Add disabled-by-default listener skeleton in `ConnectionManagerImpl`.
- Add empty/stub QUIC acceptor implementation behind feature flag.
- Add basic tests for config and listener wiring.

Then follow with transport implementation PRs.
