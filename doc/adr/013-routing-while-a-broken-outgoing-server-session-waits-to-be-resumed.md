# ADR-013: Routing While a Broken Outgoing Server Session Waits to Be Resumed

## Status

Accepted

## Context

OF-2425 adds Stream Management (XEP-0198) to server-to-server connections. When a connection that Openfire opened to another server breaks while its stream can be resumed, Openfire keeps the session, and tries to resume the stream on a new connection. That takes at least the time to open, secure and authenticate a new connection, and longer when the remote server cannot be reached for a while.

Meanwhile, new stanzas for that remote domain keep arriving, on the cluster node that holds the session and on other cluster nodes. XEP-0198 does not say what a server does with them. It does say what happens on resumption: § 5 requires the stanzas that the peer did not receive to be sent again on the resumed stream.

Several facts shape the options:

- Openfire has one route per domain pair (a local and a remote domain). Other cluster nodes route stanzas for that domain pair to the node that holds the route.
- RFC 6120 § 10.1 requires a server to route the stanzas of a client to a recipient at a remote domain in the order in which it received them, and: "To help ensure in-order processing, the routing server MUST route such stanzas over a single output stream to the remote domain, rather than sending some stanzas over one server-to-server stream and other stanzas over another server-to-server stream." A second connection to the same remote domain, used while the first is resumed, would do just that.
- The remote server decides whether, and for how long, it keeps the stream. It can report a preferred maximum resumption time in the 'max' attribute of `<enabled/>` (XEP-0198 § 5), after which it need not resume the stream.
- A stanza that waits for resumption takes memory on the node that holds the session.

## Options Considered

### Option 1: Open a New Connection at Once

**Description**: Send new stanzas over a new connection straight away, while the broken stream is resumed separately.

**Consequences**:

- ✅ New stanzas are not delayed
- ❌ Two routes for one domain pair, so stanzas can arrive out of order, contrary to RFC 6120 § 10.1
- ❌ A successful resumption has to be reconciled with the other connection

### Option 2: Return New Stanzas While Waiting

**Description**: Return new stanzas to their senders as an error until the stream is resumed or given up.

**Consequences**:

- ✅ Nothing is held in memory
- ❌ A brief interruption produces errors for stanzas that could have waited

### Option 3: Queue New Stanzas on the Broken Session (CHOSEN)

**Description**: The broken session stays the route, and queues new stanzas behind the unacknowledged ones until the stream is resumed or given up.

**Consequences**:

- ✅ One route per domain pair, so stanzas keep their order
- ✅ Nothing has to be reconciled after a successful resumption
- ❌ New stanzas wait while Openfire tries to resume
- ❌ Queued stanzas take memory, which needs a limit

## Decision

While a broken outgoing server session waits to be resumed:

- **Route**: the session stays the route for its domain pair, on every cluster node. Openfire does not open a second connection for that domain pair. Another domain pair, such as one of another local domain, gets a connection of its own as usual.
- **Queue**: a stanza for the domain pair is queued on the session, behind the unacknowledged stanzas. Whether a stanza is queued or sent is decided under the lock under which resumption stops queueing, so that no stanza is sent on a new connection ahead of queued ones.
- **Resumed**: the stanzas that the remote server reports as not received are sent first, then the queued stanzas, in order. Then queueing stops.
- **Given up**: the unacknowledged stanzas are handled as ADR-009 describes, and the queued stanzas, which were never sent, are routed again after them, in order, so that they go out over a new connection. This is done under the routing mutex of the domain pair, after its route is removed, so that newer stanzas follow them. The route is removed before that mutex is taken, so a stanza that is routed in between can go out first (see ADR-009).
- **Queue limit**: when more stanzas are queued than `stream.management.server.resume.max-queued` allows, Openfire gives up on resumption at once, and falls back as above.
- **Time**: the session is kept, and attempts to resume it are made, for the shorter of `xmpp.server.session.detach.timeout` and the preferred maximum resumption time that the remote server reported, counted from the moment that the connection broke. An attempt that fails because no connection could be made or authenticated, or because the remote server did not answer, is made again after a delay that starts at 1 second and doubles up to 30 seconds, while the next attempt would start in time. A refusal of the remote server ends the session at once.

## Rationale

One route per domain pair keeps stanzas in order, and a successful resumption then needs nothing else: it is the same stream, continued. Waiting is the cost, which the time limit bounds, and which is short when the remote server is reachable again soon. The queue limit bounds the memory that a remote server that stays away can make Openfire use. The remote server's preferred maximum is respected, as it need not resume the stream after that.

## Consequences

### What Becomes Easier

- A short interruption of a connection to another server neither reorders nor loses stanzas, and does not return errors

### What Becomes Harder

- Stanzas for a remote domain wait while Openfire tries to resume, up to the time limit
- Memory use on the node that holds a broken session grows with the stanzas that are queued, up to the queue limit

### Constraints

- A stanza that is routed on another cluster node reaches the queue through that node's routing to the node that holds the session; it is not queued on the other node
- Resumption on server connections is disabled by default (`stream.management.server.resume.enabled`)

## References

- **OF-2425**: Stream Management for server-to-server connections
- **ADR-009**: Unacknowledged Stanzas When an Outgoing Server Stream Is Not Resumed
- **XEP-0198**: Stream Management, version 1.6.3, § 5 (Resumption)
- **RFC 6120**: XMPP Core, § 10.1 (In-Order Processing)
