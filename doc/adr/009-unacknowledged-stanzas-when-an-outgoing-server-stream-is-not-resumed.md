# ADR-009: Unacknowledged Stanzas When an Outgoing Server Stream Is Not Resumed

## Status

Accepted

## Context

OF-2425 adds Stream Management (XEP-0198) to server-to-server connections. On a connection that Openfire opens to another server, Openfire sends the stanzas, keeps a copy of each until the remote server acknowledges it, and can try to resume the stream when the connection breaks.

When such a connection breaks and its stream is not resumed (resumption is not enabled, the attempt fails, or the time to resume runs out), Openfire holds stanzas that the remote server has not acknowledged. It cannot know whether they were received. Sending them again can create duplicates. Returning an error can report a failure for a stanza that did arrive.

For a stream that is not resumed, XEP-0198 (version 1.6.3) leaves the choice to the implementation. § 4 says that "until a stanza has been affirmed as handled by the server, that stanza is the responsibility of the sender (e.g., to resend it or generate an error if it is never affirmed as handled by the server)", and:

> Before the session state is discarded, implementations SHOULD take alternative action regarding any unhandled stanzas (i.e., stanzas sent after the most recent 'h' value received):
>
> A server SHOULD treat unacknowledged stanzas in the same way that it would treat a stanza sent to an unavailable resource, by either returning an error to the sender, delivery to an alternate resource, or committing the stanza to offline storage. (Note that servers SHOULD add a delay element with the original (failed) delivery timestamp, as per Delayed Delivery (XEP-0203)).

On a server connection, the recipients are on the other server, so Openfire has no alternate resource or offline storage for them. What it can do is route a stanza again, so that it goes out over a new connection, or return it to its sender.

For a stream that is resumed, XEP-0198 leaves no such choice. § 5 says that an entity handles the 'h' attribute of `<resumed/>` "as it would handle it on an &lt;a/&gt; element (i.e., marking stanzas in its outgoing queue as handled), except that after processing it MUST re-send to the peer any stanzas that are still marked as unhandled". The limit of one retry that this ADR sets applies on resumption too, which deviates from that requirement (see "Deviation from XEP-0198 § 5" below).

Several further facts shape the options:

- RFC 6120 § 8.2.3 requires a response to every IQ request (type 'get' or 'set'). An IQ request can have side effects, such as publishing an item, that are repeated when it is processed twice.
- Messages and presences that arrive twice are mostly harmless, and a duplicate can be recognised by its ID.
- A stanza can be the reason that a connection ends, in one of two ways, with different consequences:
  - The remote server refuses the stanza and ends the stream properly, with a stream error such as `<policy-violation/>`, without acknowledging the stanza. Openfire does not resume a stream that the remote server ended, and handles its unacknowledged stanzas as for any stream that is not resumed: a message or presence is routed again, over a new connection, where the remote server refuses it in the same way.
  - The stanza triggers a fault in the remote server, or in an intermediary, which drops the connection without ending the stream. Openfire cannot tell this from a network failure, so it resumes the stream, and sends the stanza again on the resumed stream, which then breaks in the same way.

  Either way, a stanza that is sent again each time would end every new or resumed connection in turn. Neither tells Openfire for certain which stanza was the cause.
- RFC 6120 § 8.3.1 forbids answering an error with an error, and § 8.2.3 forbids answering an IQ response with an IQ response.

This ADR applies to the end of a stream that asks for Stream Management, which in Openfire is the outgoing server session (`RequestingStreamManager`). At the end that answers, the behaviour is unchanged: a client session that closes without being resumed routes its unacknowledged messages again, answers its unacknowledged IQ requests with `<service-unavailable/>` and discards the rest (OF-3442), and Openfire sends no stanzas on incoming server sessions, so it tracks none there. Connections on which Stream Management is not enabled are not affected: Openfire does not track their stanzas, so it has nothing to act on.

## Options Considered

### Option 1: Send Everything Again

**Description**: Route every unacknowledged stanza again.

**Consequences**:

- ✅ No stanza is reported as failed while it may have arrived
- ❌ Repeated IQ requests can repeat side effects, such as publishing an item twice
- ❌ A stanza that breaks the connection does so on every new connection

### Option 2: Return Everything as an Error

**Description**: Return every unacknowledged stanza to its sender as an error.

**Consequences**:

- ✅ No duplicates, no repeated side effects
- ❌ A brief network interruption produces many errors, mostly for stanzas that were probably delivered
- ❌ Messages that did not arrive are lost, unless their senders send them again

### Option 3: By Type, Without a Limit

**Description**: Route messages, presences and IQ responses again, and return IQ requests as an error, each time a connection breaks.

**Consequences**:

- ✅ No messages lost, no side effects repeated
- ❌ A stanza that makes the remote server close the connection is sent again on every new connection, breaking each one in turn

### Option 4: By Type, Each Stanza Sent Again at Most Once (CHOSEN)

**Description**: As option 3, but a stanza is sent again at most once, whether on a resumed stream or on a new connection. A stanza that is still unacknowledged when that connection breaks as well is returned to its sender as an error, or dropped when no error may be returned for it.

**Consequences**:

- ✅ No messages lost after a single break, no side effects repeated
- ✅ A stanza that breaks the connection does so at most twice
- ✅ Duplicates only occur where they are harmless or can be recognised by their ID
- ❌ Two breaks in quick succession turn a stanza that was probably delivered into an error
- ❌ Openfire has to remember which stanzas it sent again, also across sessions

For the error that is returned, two conditions were considered:

#### Option A: `<service-unavailable/>`, type 'cancel' (CHOSEN)

- ✅ RFC 6121 § 8.5.3.2.3 requires it for an IQ to a resource that is unavailable, which XEP-0198 § 4 says unacknowledged stanzas are to be treated as
- ✅ RFC 6120 § 8.3.3.19 requires it instead of `<recipient-unavailable/>` where the latter "would provide information about the intended recipient's network availability to an entity that is not authorized to know such information"
- ✅ The same condition as the answering end uses for clients (OF-3442)
- ❌ Type 'cancel' means "do not retry (the error cannot be remedied)" (RFC 6120 § 8.3.2), while the same request may well succeed over a new connection

#### Option B: `<recipient-unavailable/>`, type 'wait'

- ✅ RFC 6120 § 8.3.3.13 defines it for a recipient that "is temporarily unavailable", with type 'wait', which § 8.3.2 defines as "retry after waiting (the error is temporary)", which fits a connection that broke
- ❌ Differs from the answering end, which uses `<service-unavailable/>` for clients
- ❌ RFC 6120 § 8.3.3.19 requires `<service-unavailable/>` instead where `<recipient-unavailable/>` "would provide information about the intended recipient's network availability to an entity that is not authorized to know such information". The error is returned for every unacknowledged stanza on the connection, whatever the presence of its recipient and whether its sender is allowed to see that presence, so Openfire cannot tell when the error would reveal it

## Decision

We will implement **Option 4: By type, each stanza sent again at most once**, with **Option A: `<service-unavailable/>`, type 'cancel'**, as the error.

When an outgoing server session with Stream Management closes without being resumed, `RequestingStreamManager#onClose` handles it one domain pair at a time: while holding the mutex with which stanzas for the domain pair are routed (`OutgoingSessionPromise#getMutex`), it removes the route of the domain pair, so that a stanza that is routed again goes out over another connection, and then handles each unacknowledged stanza of that domain pair in order, without holding the lock of the stream manager, so that a stanza for the domain pair that is routed meanwhile follows them:

- **Sent again**: messages, presences, and IQ responses (type 'result' or 'error') are routed again as copies (`RequestingStreamManager#isToBeSentAgain`). Messages and presences get a delay stamp (XEP-0203) with the time at which they were first sent, from the domain of this server, unless they have one already.
- **Returned**: an IQ request (type 'get' or 'set') is answered with `<service-unavailable/>`, type 'cancel'.
- **At most once**: a stanza that was sent again before, and is unacknowledged again, is not sent again a third time. It is returned to its sender with `<service-unavailable/>`, type 'cancel', when it is a message or an IQ request. It is dropped when it is of type 'error' (RFC 6120 § 8.3.1), an IQ result (RFC 6120 § 8.2.3), or a presence: one without a type or of type 'unavailable', for which RFC 6121 § 8.5.3.2.2 says that "the server MUST silently ignore the stanza" when it is sent to an unavailable resource, a presence probe, which a server sends on behalf of a user (RFC 6121 § 4.3), or a subscription presence ('subscribe', 'subscribed', 'unsubscribe' or 'unsubscribed'), which a server stamps with the bare JID of the user (RFC 6121 § 3), to which Openfire does not route a presence.
- **Error responses** swap the addresses of the stanza and keep its ID (and the thread of a message), but do not include its content (`RequestingStreamManager#createErrorResponse`). They are routed after the other stanzas are handled, without holding a mutex: they go to local senders, such as a local component, which can route other stanzas in turn on the same thread, for example to another remote domain, of which the mutex can be held by another thread that ends a session for that domain.
- **On resumption**: the stanzas that remain unacknowledged after the 'h' of `<resumed/>` are sent again on the resumed stream, once the remote server has confirmed the resumption with that element, and each is marked as sent again once it is sent; those that are not sent, as the connection closed first, are not marked. One that was sent again before is returned as an error, or dropped, as above, instead of being sent again, and the others are numbered as if it had not been sent (`RequestingStreamManager#onResume`). When the resumed stream breaks before the remote server has sent an acknowledgement (`<a/>`) on it, the time for which the session is kept while detached is not extended: it is still measured from the moment at which the session was detached before the resumption (`LocalOutgoingServerSession#beforeDetached`).
- **The marker**: a copy that is routed again is recorded in a set in memory, which compares stanzas by identity and holds them weakly. The session that sends the copy next looks it up when it tracks the stanza, and records on its entry in the queue of unacknowledged stanzas that it was sent again (`UnacknowledgedStanzaQueue.Entry#isSentAgain`). Code that copies a stanza on its way to an outgoing session passes the marker on to the copy (`RequestingStreamManager#copyRoutedAgainMarker`). A resumed stream marks the entries that it sends again directly, as they stay in the same queue.
- **Refused resumption**: when the remote server refuses a resumption with `<failed/>` that carries an 'h' attribute (XEP-0198 § 5), the stanzas that it acknowledges are removed before the session is ended, so that they are neither sent again nor returned. A value that is not valid is ignored.
- **Not tracked**: stanzas that were sent while the request to enable Stream Management awaited an answer are not tracked, and are therefore not handled.

### Deviation from XEP-0198 § 5

On resumption, Openfire does not send again every stanza that the 'h' of `<resumed/>` leaves unhandled, as § 5 requires ("MUST re-send to the peer any stanzas that are still marked as unhandled"):

- A stanza that was sent again once before is not sent again, but returned to its sender as an error, or dropped, as described above. A stanza that makes the connection break, for example because the remote server fails on it, would otherwise break every resumed connection in turn, as it would every new one. When the remote server instead ends the stream, with a closing stream tag or a stream error, the stream is not resumed, and its unacknowledged stanzas are handled as for any stream that is not resumed.
- The stanzas of a domain pair other than the one for which the session was created are held until that domain pair is verified again with dialback on the new connection (ADR-008). They are sent once it is verified, and are handled as when the session ends when it is given up.

The same handling applies to the unacknowledged stanzas of a domain pair that is given up after a resumption (ADR-008). Stanzas that were queued or held while the connection was broken, and that were never sent, are routed again after the unacknowledged ones, in order, without a delay stamp; that follows from how a broken outgoing session queues stanzas (decision 7 of the OF-2425 plan), not from this ADR.

## Rationale

Sorting by type avoids losing messages without repeating side effects. Messages, presences and IQ responses can be sent twice without harm, or with duplicates that their recipients can recognise by their ID, and the delay stamp tells recipients when the stanza was first sent, as XEP-0198 § 4 recommends. IQ requests are the stanzas whose repetition can do harm, and their senders need a response anyway.

The limit of one retry stops a stanza that makes the remote server close the connection from breaking every new connection in turn. Allowing one retry, rather than none, keeps the benefit of sending again after a single break.

XEP-0198 § 4 says to treat unacknowledged stanzas as stanzas sent to an unavailable resource, and for an IQ sent to such a resource a server returns `<service-unavailable/>` (RFC 6121 § 8.5.3.2.3), as the answering end does for clients. `<recipient-unavailable/>`, with type 'wait', would describe a broken connection more closely, but RFC 6120 § 8.3.3.19 does not allow it where it would reveal the availability of the recipient to an entity that is not authorized to know it, and the error is returned without regard to the presence of the recipient or to what its sender may know of it.

## Consequences

### What Becomes Easier

- A brief break of an outgoing server connection does not lose the stanzas that were unacknowledged at that moment
- Senders of IQ requests get a response instead of waiting for their own timeout
- A stanza that breaks connections stops doing so after one retry

### What Becomes Harder

- Recipients can receive a message or presence twice, after a break, and need to recognise duplicates by their ID
- Two breaks in quick succession turn stanzas that were probably delivered into errors
- The outgoing end handles unacknowledged stanzas differently from the answering end, which routes only messages again for clients and discards presences and IQ responses
- The type 'cancel' tells the sender of an IQ request not to retry it, although the same request may succeed over a new connection

### Constraints

- The marker exists in memory on one cluster node only, and is not part of the stanza, so nothing about it is sent to the remote server. A stanza that another cluster node delivers, or that is serialized, loses it, and can then be sent again once more on that node
- Stanzas sent while the request to enable Stream Management is pending are counted, as the remote server counts them, but not tracked, so they are not covered. Decision 3 of the OF-2425 plan keeps a new outgoing connection from being used until that negotiation has finished, but only for up to `xmpp.server.session.stream-management-negotiation-timeout`, after which the connection is used anyway
- The stanzas of a domain pair must be handled after its route is removed, as a stanza that is routed again would otherwise reach the closing session, and under the mutex of that domain pair, as a newer stanza could otherwise go out ahead of them. The errors that return stanzas to their senders are routed without holding that mutex, so they can reach their senders after newer stanzas for the domain pair

## References

- **OF-2425**: Stream Management for server-to-server connections (engineering plan: `doc/plan/2026-10-23-stream-management-s2s.md`, decisions 3, 4 and 7)
- **OF-3442**: Unacknowledged stanzas when a session closes (behaviour at the answering end)
- **ADR-008**: Extra Domains After Resuming a Server Connection
- **XEP-0198**: Stream Management, version 1.6.3, § 4 (Acks) and § 5 (Resumption)
- **XEP-0203**: Delayed Delivery
- **RFC 6120**: XMPP Core, § 8.2.3, § 8.3.1, § 8.3.3.13 and § 8.3.3.19
- **RFC 6121**: XMPP Instant Messaging and Presence, § 3, § 4.3, § 8.5.3.2.2 and § 8.5.3.2.3
