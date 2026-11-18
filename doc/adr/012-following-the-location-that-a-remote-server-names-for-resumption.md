# ADR-012: Following the Location That a Remote Server Names for Resumption

## Status

Accepted

## Context

OF-2425 adds Stream Management (XEP-0198) to server-to-server connections. On a connection that Openfire opens to another server, Openfire asks for a stream that can be resumed, and tries to resume that stream on a new connection when the connection breaks.

When the remote server enables Stream Management, it can name a location at which it prefers the stream to be resumed. XEP-0198 (version 1.6.3) § 3 says:

> The `<enabled/>` element MAY include a 'location' attribute to specify the server's preferred IP address or hostname (optionally with a port) for reconnection, in the form specified in Section 4.9.3.19 of RFC 6120 (i.e., "domainpart:port", where IPv6 addresses are enclosed in square brackets "[...]" as described in RFC 5952); if reconnection to that location fails, the standard XMPP connection algorithm specified in RFC 6120 applies.

and § 5 describes that the reconnecting entity opens a new connection, "preferring the address specified in the 'location' attribute (if any)". Neither uses MUST or SHOULD: following the location is optional.

The location matters for a remote server that is a cluster. Openfire itself names the host name of the cluster node that holds the session (`stream.management.location.enabled`, which is enabled by default), and resumes a stream only on that node. A connection that DNS sends to another node of such a cluster cannot resume the stream.

The remote server chooses the location, and Openfire connects to it before the remote server has authenticated on the new connection. Openfire sends a stream header to that address, and a TLS handshake when TLS is used. The remote server learns only whether the attempt to resume succeeded, not what the address answered. A remote server that controls the DNS of its own domain can already point Openfire at any address and port with SRV records. Following the location gives the same reach to whoever runs the remote server without controlling its DNS, and it does so for any address, including those of the host of Openfire and of its own network.

The stream that is negotiated on a connection to the location is addressed to the domain of the remote server, and the remote server must authenticate as that domain, as on any new connection. A location is not used when a DNS override applies to the domain of the remote server. Resumption on server connections is disabled by default.

## Options Considered

### Option 1: Follow the Location Without Restriction

**Description**: Connect to any address and port that the location names or resolves to.

**Consequences**:

- ✅ Simplest; resumption works against any cluster
- ❌ A remote server can make Openfire connect to addresses of its own host (loopback) or network

### Option 2: Refuse Loopback, Link-Local and Unspecified IP Addresses in the Location

**Description**: Refuse a location that is written as such an IP address.

**Consequences**:

- ✅ Small
- ❌ A location that is a host name that resolves to such an address is still connected to

### Option 3: Accept Only Hosts That DNS Gives for the Domain of the Remote Server

**Description**: Use the location only when it is one of the hosts that the SRV records of the remote domain name.

**Consequences**:

- ✅ Adds nothing beyond what DNS already allows
- ❌ The host name of a cluster node is usually not among those hosts, so the location is rarely of use

### Option 4: Do Not Follow the Location

**Description**: Always resume over the hosts that DNS gives for the remote domain.

**Consequences**:

- ✅ No new reach for a remote server
- ✅ Allowed by XEP-0198
- ❌ Against a cluster, a stream can be resumed only when DNS happens to lead to the node that holds it

### Option 5: Follow the Location, Filtering the Addresses That It Resolves To (CHOSEN)

**Description**: Resolve the location, and connect only to addresses that are not loopback, link-local, unspecified or multicast, and that are private only when the original connection was.

**Consequences**:

- ✅ Resumption against a cluster works, also when the location is a host name
- ✅ A host name that resolves to a forbidden address is caught as well
- ✅ Servers that federate on a private network can still name private addresses of their nodes
- ❌ A remote server on the public internet can still name any public address and port, as its DNS can
- ❌ The filter does not know the addresses of the host of Openfire: a public address of that host, or a private one after a private original connection, is not refused

## Decision

Openfire follows the location that a remote server names, with these rules:

- The location is used in the first attempt to resume a stream only, for at most `xmpp.server.session.stream-management-negotiation-timeout`. When no connection to it can be made, that attempt connects to the hosts of the remote domain, and later attempts do not use the location again.
- The location is not used when a DNS override applies to the domain of the remote server. A DNS override for the host of the location applies to that host.
- Each address that the location resolves to is checked before it is connected to. Loopback, link-local, unspecified and multicast addresses are never connected to. Private addresses (the IPv4 ranges of RFC 1918, and IPv6 unique local addresses, RFC 4193) are connected to only when the original connection to the remote server, on which it named the location, was made to a private address too. Other addresses are connected to.
- The port is not restricted, as XEP-0198 allows any port, and SRV records can name any port already.
- There is no separate setting to stop following the location. Resumption on server connections is already disabled by default, and can be disabled on its own.

## Rationale

Following the location is what makes resumption work against a cluster, including a cluster of Openfire, which names its node by default. Filtering the addresses that the location resolves to, rather than the location as it is written, covers host names as well as IP addresses. Comparing the kind of address with that of the original connection keeps federation on a private network working, while a remote server on the public internet cannot lead Openfire into a private network. What remains, any public address and port, is what the DNS of the remote domain can already do.

## Consequences

### What Becomes Easier

- A stream with a remote server that is a cluster can be resumed on the node that holds it

### What Becomes Harder

- A location that resolves only to addresses that are filtered is not used, and resumption then depends on DNS
- A cluster of which the nodes have private addresses, behind a public address for federation, cannot be resumed at those private addresses by a server on the public internet

### Constraints

- The address of the original connection is recorded when the remote server enables Stream Management with a stream that can be resumed. When it cannot be determined, private addresses are not connected to

## References

- **OF-2425**: Stream Management for server-to-server connections
- **OF-1925**: Openfire names the cluster node that holds a session as the location
- **XEP-0198**: Stream Management, version 1.6.3, § 3 (Enabling Stream Management) and § 5 (Resumption)
- **RFC 6120**: XMPP Core, § 4.9.3.19 (see-other-host, the form of a location)
- **RFC 1918**: Address Allocation for Private Internets
- **RFC 4193**: Unique Local IPv6 Unicast Addresses
