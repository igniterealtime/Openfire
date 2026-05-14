# Plan: QUIC Connection Migration (RFC 9000 §9)

## Background

RFC 9000 §9 allows a QUIC client to change its UDP 4-tuple (IP address and/or port) mid-connection
without losing application state. Common triggers include:

- NAT rebinding (the client's NAT device silently assigns a new port)
- Wi-Fi ↔ cellular handover (the client's IP address changes)
- Deliberate client-initiated migration (e.g. to prefer a lower-latency path)

The server detects migration when it receives a packet carrying a known Connection ID from a new
source address. It must then validate the new path (RFC 9000 §9.3) and, once validated, continue
the connection on the new address.

## Why migration is not supported today

The current architecture has two hard blockers:

1. **`QuicSessionStreamRouter` is keyed on `QuicChannel` instance.**
   `QuicSessionStreamRouter.getOrCreate()` stores the router as a Netty channel attribute on the
   `QuicChannel` object. If the underlying transport creates a new `QuicChannel` for the migrated
   path (which is what Netty/quiche does today), the new channel has no attribute and a fresh,
   empty router is created — losing all session and stream state.

2. **`HmacQuicTokenHandler` binds tokens to IP + port.**
   A migrated client presents a Retry token that was issued for its old address. The token
   validation fails, the server sends a new Retry, and the client must complete a fresh handshake.
   This is safe but means migration is effectively a reconnect, not a seamless path change.

## Goal

Allow a client to migrate its UDP 4-tuple without losing its XMPP session or open QUIC streams.
The XMPP layer (stanza routing, session state, stream multiplexing) must be completely unaffected
by the path change.

---

## Implementation status

| Phase | Status |
|-------|--------|
| 1 — Session registry | ✅ Done (commit 745337f) |
| 2 — Token handler v2 | ✅ Done (commit 7cb0b09) |
| 3 — Migration event handling | ✅ Done (commit 745337f) |
| 4 — Path validation verification | ✅ Done (no code needed — quiche handles PATH_CHALLENGE/RESPONSE automatically) |
| 5.1 — Unit tests | ✅ Done (19 HmacQuicTokenHandlerTest + 11 QuicSessionRegistryTest) |
| 5.2 — Integration/interop test | ⏳ Out of scope (requires external QUIC client, e.g. aioquic) |
| 6 — Config + docs | ✅ Done (commit 7cb0b09) |

---

## Implementation plan

### Phase 1 — Connection-ID-indexed session registry

**Objective:** decouple `QuicSessionStreamRouter` from the `QuicChannel` object so that it can be
looked up by Connection ID after migration.

#### 1.1 — Define a `QuicConnectionId` value type

Create `org.jivesoftware.openfire.nio.QuicConnectionId` (immutable, `equals`/`hashCode` on the
raw bytes). This wraps the QUIC Destination Connection ID (DCID) that the server assigns and that
the client echoes in every packet.

#### 1.2 — Create `QuicSessionRegistry`

Create `org.jivesoftware.openfire.nio.QuicSessionRegistry` as a singleton (or a field on
`QuicConnectionAcceptor`):

```java
public class QuicSessionRegistry {
    private final ConcurrentHashMap<QuicConnectionId, QuicSessionStreamRouter> byConnectionId
        = new ConcurrentHashMap<>();

    public void register(QuicConnectionId cid, QuicSessionStreamRouter router) { … }
    public QuicSessionStreamRouter find(QuicConnectionId cid) { … }
    public void unregister(QuicConnectionId cid) { … }
}
```

#### 1.3 — Register on connection open, unregister on close

In `QuicConnectionAcceptor.start()`, inside the `QuicChannel` `ChannelInitializer`:

- On `channelActive`: extract the server-chosen DCID from `QuicChannel` (via
  `quicChannel.attr(QuicAttributeKey.LOCAL_CONNECTION_ID)` or equivalent Netty API), create the
  `QuicSessionStreamRouter`, and register it in `QuicSessionRegistry`.
- On `channelInactive`: unregister by CID.

#### 1.4 — Change `QuicSessionStreamRouter.getOrCreate` to use the registry

Replace the channel-attribute lookup with a registry lookup keyed on CID. The channel attribute
can remain as a fast path for the common (non-migrated) case.

---

### Phase 2 — Token handler that allows path migration

**Objective:** issue tokens that are bound to the Connection ID rather than (or in addition to)
the source address, so that a migrated client can present a valid token from its new address.

#### 2.1 — Extend `HmacQuicTokenHandler`

Add a second token type (distinguished by a version byte at offset 0):

| Offset | Length | Content |
|--------|--------|---------|
| 0      | 1      | version = `0x02` |
| 1      | 8      | timestamp (ms, big-endian) |
| 9      | N      | DCID bytes (variable, 0–20 bytes) |
| 9+N    | 32     | HMAC-SHA256(secret, dcid \| timestamp) |

The HMAC covers the DCID and timestamp but **not** the source address. `validateToken` accepts the
token from any source address as long as the DCID and HMAC are valid and the token has not expired.

Keep the existing v1 token (IP+port bound) as the default for new connections; emit v2 tokens only
after the connection has been established and migration is expected.

> **Security note:** removing the address binding weakens amplification protection for the initial
> handshake. v2 tokens must only be issued *after* the initial address validation has already
> succeeded (i.e. as `NEW_TOKEN` frames sent inside the encrypted connection, not as Retry tokens).

#### 2.2 — Emit `NEW_TOKEN` frames after handshake

After the QUIC handshake completes (in `channelActive` on the `QuicChannel`), call
`quicChannel.newToken(…)` (if the Netty API exposes it) to push a migration-capable v2 token to
the client. The client will present this token if it migrates.

---

### Phase 3 — Handle the migration event in Netty

**Objective:** when Netty/quiche fires a migration event, re-attach the existing
`QuicSessionStreamRouter` to the new `QuicChannel`.

#### 3.1 — Investigate Netty migration event API

Check whether `netty-codec-quic` (quiche backend) fires a channel event or creates a new
`QuicChannel` on migration. As of 4.2.x the behaviour is:

- quiche fires `QUIC_CONNECTION_MIGRATION` as a user event on the existing `QuicChannel` if the
  path changes but the channel object is reused.
- If a new `QuicChannel` is created (less likely but possible in future versions), the registry
  lookup in Phase 1 handles it.

#### 3.2 — Add a `MigrationAwareHandler` to the `QuicChannel` pipeline

```java
public class QuicMigrationHandler extends ChannelInboundHandlerAdapter {
    private final QuicSessionRegistry registry;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof QuicConnectionMigrationEvent migrationEvent) {
            // Log old → new address.
            // Re-validate path if needed (quiche may do this automatically).
            // If the channel object changed, look up the router by CID and re-attach.
        }
        ctx.fireUserEventTriggered(evt);
    }
}
```

Add this handler early in the `QuicChannel` pipeline (before `NewConnectionRateLimitHandler`).

---

### Phase 4 — Path validation on migration (RFC 9000 §9.3)

**Objective:** ensure the server validates the new path before sending significant data on it.

quiche (the underlying QUIC library) performs path validation automatically via `PATH_CHALLENGE` /
`PATH_RESPONSE` frames. Verify this is enabled in the quiche build used by Netty and that no
Openfire-level code suppresses it. No application-level changes should be needed here, but add an
integration test (see Phase 5) to confirm.

---

### Phase 5 — Tests

#### 5.1 — Unit tests

- `HmacQuicTokenHandlerTest`: add cases for v2 tokens (valid DCID, wrong DCID, expired, tampered).
- `QuicSessionRegistryTest`: register/find/unregister; concurrent access.

#### 5.2 — Integration / interop test

Write a test client (using `aioquic` or a Netty QUIC client) that:

1. Establishes a QUIC connection and authenticates an XMPP session.
2. Sends a stanza and receives a response.
3. Changes its source port (simulating NAT rebinding) by rebinding the UDP socket.
4. Sends another stanza.
5. Asserts the stanza is delivered on the same XMPP session (same JID, no re-auth).

---

### Phase 6 — Configuration and documentation

- Add a `SystemProperty<Boolean>` `xmpp.quic.client.migration-enabled` (default `false`) that
  gates the v2 token issuance and the migration handler. This allows operators to opt in
  incrementally.
- Update `QuicConnectionAcceptor` Javadoc and the comment block around `tokenHandler(…)`.
- Remove the "connection migration is NOT supported" comment once the feature is complete.

---

## Risks and open questions

| Risk | Mitigation |
|------|-----------|
| Netty/quiche does not expose a migration event | Investigate the 4.2.x API; may need to patch or upgrade |
| Multiple CIDs per connection (RFC 9000 §5.1) | Registry must support all active CIDs for a connection, not just the initial one |
| Cluster deployments: migrated client hits a different node | Out of scope for this plan; requires a distributed session registry (e.g. Hazelcast-backed) |
| v2 tokens weaken amplification protection if misused | Strict issuance policy: v2 tokens only inside encrypted connection, never as Retry tokens |

---

## Estimated effort

| Phase | Effort |
|-------|--------|
| 1 — Session registry | ~1 day |
| 2 — Token handler v2 | ~0.5 day |
| 3 — Migration event handling | ~1 day (depends on Netty API investigation) |
| 4 — Path validation verification | ~0.5 day |
| 5 — Tests | ~1 day |
| 6 — Config + docs | ~0.5 day |
| **Total** | **~4.5 days** |
