/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.openfire.streammanagement;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.MapMaker;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * XEP-0198 Stream Management at the end of a stream that asks for it, and that the peer answers, such as a connection
 * that Openfire opened to another server.
 *
 * Only Openfire asks on such a stream. Every Stream Management element of the peer that this end does not process,
 * including a request to enable Stream Management or to resume a stream, is answered with {@code <failed/>}, without
 * changing the state of Stream Management.
 *
 * Once the peer enabled Stream Management, the stanzas that Openfire sends are tracked until the peer acknowledges
 * them, and acknowledgements are requested, as at the end that answers. Stanzas that are sent while a request to enable
 * Stream Management awaits an answer are counted, as the peer counts them from the request on (XEP-0198 § 4), but are
 * not tracked.
 *
 * When the session closes without being resumed, the stanzas that the peer did not acknowledge are routed again, or
 * returned to their sender as an error, depending on their type (see {@link #onClose(PacketRouter, JID)}). A stanza is
 * sent again at most once: a stanza that was sent again and is unacknowledged when that session closes too, is returned
 * to its sender as an error.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class RequestingStreamManager extends StreamManager
{
    /**
     * The progress of a request to enable Stream Management that Openfire sent to the peer.
     */
    public enum EnableRequestStatus {
        /**
         * Openfire has not asked the peer to enable Stream Management.
         */
        NOT_SENT,

        /**
         * Openfire asked the peer to enable Stream Management, and has not received an answer yet.
         */
        PENDING,

        /**
         * The peer enabled Stream Management at the request of Openfire.
         */
        ENABLED,

        /**
         * The peer refused to enable Stream Management at the request of Openfire.
         */
        REFUSED,

        /**
         * Openfire decided not to ask the peer to enable Stream Management on this stream, for example because the peer
         * did not offer it (see {@link #skipEnable()}). No request is sent after this.
         */
        SKIPPED
    }

    /**
     * The progress of a request to enable Stream Management that Openfire sent to the peer. Changed under the lock of
     * this instance.
     */
    private volatile EnableRequestStatus enableRequestStatus = EnableRequestStatus.NOT_SENT;

    /**
     * The namespace of the request to enable Stream Management that Openfire sent to the peer, if any. Guarded by the
     * lock of this instance.
     */
    private String requestedNamespace;

    /**
     * Completes when negotiation of Stream Management has finished, with the outcome: {@link EnableRequestStatus#ENABLED}
     * or {@link EnableRequestStatus#REFUSED} when the peer answered a request, or {@link EnableRequestStatus#SKIPPED}
     * when Openfire decided not to ask.
     */
    private final CompletableFuture<EnableRequestStatus> negotiationOutcome = new CompletableFuture<>();

    /**
     * The number of stanzas that Openfire sent while a request to enable Stream Management awaited an answer. Guarded by
     * the lock of this instance.
     */
    private long sentWhilePending = 0;

    /**
     * Stanzas that are routed again after the session on which they were unacknowledged closed, so that the session that
     * sends them next can track them as sent again. Stanzas are compared by identity, and are held weakly, so that a
     * stanza that is not sent, or not tracked, does not stay in this set.
     */
    private static final Set<Packet> ROUTED_AGAIN = Collections.newSetFromMap( new MapMaker().weakKeys().makeMap() );

    RequestingStreamManager(@Nonnull final LocalSession session)
    {
        super(session);
    }

    @Override
    boolean processNegotiation( @Nonnull final Element element )
    {
        switch ( element.getName() ) {
            case "enabled":
            case "failed":
                processEnableResponse( element );
                break;
            default:
                // OF-2425: The fallback of the base class disables Stream Management, after which the stanzas that it tracks are not handled when the session closes.
                Log.debug( "Refusing '{}' from the peer of session {}, as Openfire asks for Stream Management on this stream.", element.getName(), session );
                refuse( element );
        }
        return true;
    }

    /**
     * Asks the peer to enable Stream Management. Resumption is not requested. The answer of the peer is processed by
     * {@link #process(Element)}, and its outcome is available from {@link #getEnableRequestStatus()}.
     *
     * Stream management is requested at most once per stream: an invocation after an earlier request, after
     * {@link #skipEnable()}, or while Stream Management is enabled, has no effect.
     *
     * The request is sent under the lock of this instance, under which a stanza that is sent meanwhile is counted (see
     * {@link #sentStanza(Packet)}), so that Openfire counts exactly the stanzas that follow the request, as the peer
     * does (XEP-0198 § 4).
     *
     * @param namespace The namespace of the version of Stream Management that the peer offers.
     */
    public void requestEnable(@Nonnull final String namespace)
    {
        synchronized ( this ) {
            if ( enableRequestStatus != EnableRequestStatus.NOT_SENT || isEnabled() ) {
                Log.debug( "Not asking the peer of session {} to enable Stream Management, as that was asked or skipped before (status: {}) or it is enabled (enabled: {}).", session, enableRequestStatus, isEnabled() );
                return;
            }
            requestedNamespace = namespace;
            enableRequestStatus = EnableRequestStatus.PENDING;
            Log.debug( "Asking the peer of session {} to enable Stream Management ({}).", session, namespace );
            session.deliverRawText( DocumentHelper.createElement( QName.get( "enable", namespace ) ).asXML() );
        }
    }

    /**
     * Records that Openfire does not ask the peer to enable Stream Management on this stream, for example because the
     * peer did not offer it: the status becomes {@link EnableRequestStatus#SKIPPED}, after which no request is sent.
     * This finishes negotiation, as reported by {@link #getNegotiationOutcome()}.
     *
     * This has no effect after an earlier request, or while Stream Management is enabled.
     */
    public void skipEnable()
    {
        synchronized ( this ) {
            if ( enableRequestStatus != EnableRequestStatus.NOT_SENT || isEnabled() ) {
                Log.trace( "Ignoring that Stream Management is not requested on session {}, as that was asked or skipped before (status: {}) or it is enabled (enabled: {}).", session, enableRequestStatus, isEnabled() );
                return;
            }
            enableRequestStatus = EnableRequestStatus.SKIPPED;
        }
        negotiationOutcome.complete( EnableRequestStatus.SKIPPED );
    }

    /**
     * Returns a future that completes when negotiation of Stream Management has finished: when the peer enabled or
     * refused Stream Management at the request of Openfire, or when Openfire decided not to ask (see
     * {@link #skipEnable()}). The future completes with {@link EnableRequestStatus#ENABLED},
     * {@link EnableRequestStatus#REFUSED} or {@link EnableRequestStatus#SKIPPED} respectively.
     *
     * The future does not complete when the peer does not answer, or when the stream closes before negotiation has
     * finished. Completing the returned future does not affect this instance.
     *
     * @return a future for the outcome of negotiation.
     */
    @Nonnull
    public CompletableFuture<EnableRequestStatus> getNegotiationOutcome()
    {
        return negotiationOutcome.copy();
    }

    /**
     * Returns the progress of a request to enable Stream Management that Openfire sent using
     * {@link #requestEnable(String)}.
     *
     * @return the progress of the request.
     */
    @Nonnull
    public EnableRequestStatus getEnableRequestStatus()
    {
        return enableRequestStatus;
    }

    /**
     * Processes the {@code <enabled/>} or {@code <failed/>} element with which the peer answers a request of Openfire to
     * enable Stream Management. Such an element that does not answer a request of Openfire is refused.
     *
     * @param element The answer of the peer.
     */
    private void processEnableResponse(@Nonnull final Element element)
    {
        final String requested;
        final boolean answersRequest;
        final boolean enabled;
        synchronized ( this ) {
            requested = requestedNamespace;
            answersRequest = enableRequestStatus == EnableRequestStatus.PENDING;
            // A peer that answers in a version other than the one requested is not taken to have enabled that version.
            enabled = answersRequest && "enabled".equals( element.getName() ) && element.getNamespaceURI().equals( requested );
            if ( enabled ) {
                // OF-2425: The peer counts the stanzas that were sent since the request (XEP-0198 § 4), so those that follow are numbered after them.
                skipOutboundStanzas( sentWhilePending );
                setNamespace( requested );
                enableRequestStatus = EnableRequestStatus.ENABLED;
            } else if ( answersRequest ) {
                enableRequestStatus = EnableRequestStatus.REFUSED;
            }
        }

        if ( !answersRequest ) {
            Log.debug( "Refusing '{}' from the peer of session {}, as no request of Openfire to enable Stream Management awaits an answer (status: {}).", element.getName(), session, enableRequestStatus );
            refuse( element );
            return;
        }

        if ( enabled ) {
            // Resumption was not requested, so an offer of it in the answer is ignored.
            Log.debug( "The peer of session {} enabled Stream Management ({}). Stanzas sent while the request awaited an answer, which are counted but not tracked: {}. Offered resumption, which is not used: {}", session, requested, sentWhilePending, element.attributeValue( "resume" ) );
        } else {
            Log.debug( "The peer of session {} did not enable Stream Management ({}). It answered with '{}' in namespace '{}', reporting: {}", session, requested, element.getName(), element.getNamespaceURI(), element.elements().stream().map( Element::getName ).findFirst().orElse( "(no condition)" ) );
        }
        negotiationOutcome.complete( enabled ? EnableRequestStatus.ENABLED : EnableRequestStatus.REFUSED );
    }

    /**
     * Registers that Openfire sends a stanza to the peer. While a request to enable Stream Management awaits an answer,
     * the stanza is only counted, as the peer counts it once it enables Stream Management. Otherwise, this behaves as
     * {@link StreamManager#sentStanza(Packet)}: the stanza is tracked while Stream Management is enabled. A stanza that
     * is routed again after another session closed (see {@link #onClose(PacketRouter, JID)}) is tracked as sent again.
     *
     * @param packet The stanza that is sent.
     */
    @Override
    public void sentStanza(Packet packet)
    {
        synchronized ( this ) {
            if ( enableRequestStatus == EnableRequestStatus.PENDING ) {
                sentWhilePending++;
                return;
            }
            super.sentStanza( packet, isRoutedAgain( packet ) );
        }
    }

    /**
     * Gives a copy of a stanza the marker that records that the stanza is routed again after the session on which it was
     * unacknowledged closed, if the stanza has that marker. Code that copies a stanza on its way to an outgoing server
     * session, and sends the copy instead of the stanza, invokes this so that the session that sends the copy tracks it
     * as sent again, and does not send it again a second time.
     *
     * The marker is kept in memory on this cluster node only. A stanza that is delivered by another cluster node, or
     * that is serialized, loses it.
     *
     * @param original The stanza that is copied.
     * @param copy The copy that is sent instead of the stanza.
     */
    public static void copyRoutedAgainMarker(@Nonnull final Packet original, @Nonnull final Packet copy)
    {
        if ( ROUTED_AGAIN.contains( original ) ) {
            ROUTED_AGAIN.add( copy );
        }
    }

    /**
     * Checks whether a stanza is routed again after the session on which it was unacknowledged closed.
     *
     * @param packet The stanza to check.
     * @return true if the stanza is routed again.
     */
    @VisibleForTesting
    static boolean isRoutedAgain(@Nonnull final Packet packet)
    {
        return ROUTED_AGAIN.contains( packet );
    }

    /**
     * Answers an element of the peer that is unexpected on this stream with {@code <failed/>}, in the namespace of that
     * element. Unlike {@link #sendError(PacketError)}, this leaves the state of Stream Management as it is.
     *
     * @param element The unexpected element.
     */
    private void refuse(@Nonnull final Element element)
    {
        session.deliverRawText( buildFailedElement( element.getNamespaceURI(), PacketError.Condition.unexpected_request ).asXML() );
    }

    /**
     * Handles the stanzas that the peer did not acknowledge, for a session that closed without being resumed, and
     * disables Stream Management for the session.
     *
     * Openfire cannot know whether the peer received these stanzas. Messages, presences and IQ responses (of type
     * 'result' or 'error') are routed again, so that they can reach their recipient over another connection. Messages
     * and presences that are routed again get a delay stamp (XEP-0203) with the time at which they were first sent, if
     * they do not have one. An IQ request (of type 'get' or 'set') is not routed again, as it can have side effects that
     * must not be repeated: its sender receives a 'service-unavailable' error instead, as RFC 6120 § 8.2.3 requires a
     * response to every IQ request.
     *
     * A stanza is sent again at most once, so that a stanza that makes the peer close the connection does not do so on
     * every new connection. A stanza that was sent again, and is unacknowledged when this session closes, is returned to
     * its sender as a 'service-unavailable' error. No error is returned for a stanza of type 'error' (RFC 6120 § 8.3.1),
     * an IQ result (RFC 6120 § 8.2.3), or a presence, of any type (RFC 6121 § 8.5.3.2.2): those are dropped. A stanza
     * that is routed again keeps a marker in memory, with which the session that sends it next tracks it as sent again
     * (see {@link #copyRoutedAgainMarker(Packet, Packet)}).
     *
     * Stanzas that were sent while a request to enable Stream Management awaited an answer are not tracked, and are
     * therefore not handled. When Stream Management is not enabled, no stanzas are handled.
     *
     * This must be invoked after the routes of the session are removed. While the session is a route, a stanza that is
     * routed again reaches that session rather than another connection.
     *
     * @param router the router used to route the stanzas.
     * @param serverAddress this server's bare-domain address, used to stamp delay information on messages and presences.
     */
    @Override
    public void onClose(PacketRouter router, JID serverAddress)
    {
        final Collection<UnacknowledgedStanzaQueue.Entry> unacknowledged;
        synchronized ( this ) {
            if ( !isEnabled() ) {
                return;
            }
            unacknowledged = getUnacknowledgedEntries();
            setNamespace( null );
        }

        // OF-2425: The stanzas are routed without holding the lock, as routing can deliver them to another session.
        int sentAgain = 0;
        int returned = 0;
        int dropped = 0;
        for ( final UnacknowledgedStanzaQueue.Entry entry : unacknowledged ) {
            final Packet packet = entry.getPacket();
            try {
                if ( isToBeSentAgain( entry ) ) {
                    Log.trace( "Routing unacknowledged '{}' stanza of type '{}' with ID '{}' of session {} again.", packet.getElement().getName(), packet.getElement().attributeValue( "type" ), packet.getID(), session );
                    final Packet copy = copyForSendingAgain( entry, serverAddress );
                    ROUTED_AGAIN.add( copy );
                    router.route( copy );
                    sentAgain++;
                    continue;
                }
                final Packet error = createErrorResponse( packet );
                Log.trace( "Not routing unacknowledged '{}' stanza of type '{}' with ID '{}' of session {} again (sent again before: {}). Returning it to its sender as an error: {}", packet.getElement().getName(), packet.getElement().attributeValue( "type" ), packet.getID(), session, entry.isSentAgain(), error != null );
                if ( error != null ) {
                    router.route( error );
                    returned++;
                } else {
                    dropped++;
                }
            } catch ( Exception e ) {
                // The stanza itself is not logged, as it can have private content.
                Log.warn( "Unable to handle an unacknowledged '{}' stanza of type '{}' with ID '{}', of session {} that closed.", packet.getElement().getName(), packet.getElement().attributeValue( "type" ), packet.getID(), session, e );
            }
        }
        if ( !unacknowledged.isEmpty() ) {
            Log.debug( "Session {} closed without being resumed, with {} unacknowledged stanza(s): {} routed again, {} returned to the sender as an error, {} dropped as no error can be returned for them.", session, unacknowledged.size(), sentAgain, returned, dropped );
        }
    }

    /**
     * Decides whether a stanza that the peer did not acknowledge when the session closed is to be sent again, rather than
     * returned to its sender as an error. Messages, presences and IQ responses are sent again, IQ requests are not. A
     * stanza that was sent again before is not sent again a second time.
     *
     * @param entry The unacknowledged stanza.
     * @return true if the stanza is to be sent again.
     */
    private static boolean isToBeSentAgain(@Nonnull final UnacknowledgedStanzaQueue.Entry entry)
    {
        return !entry.isSentAgain() && !( entry.getPacket() instanceof IQ iq && iq.isRequest() );
    }

    /**
     * Returns a copy of an unacknowledged stanza to route again. A message or presence gets a delay stamp (XEP-0203)
     * with the time at which it was first sent, unless it has one already.
     *
     * @param entry The unacknowledged stanza.
     * @param serverAddress this server's bare-domain address, which is recorded as the entity that delayed the stanza.
     * @return a copy of the stanza.
     */
    @Nonnull
    private static Packet copyForSendingAgain(@Nonnull final UnacknowledgedStanzaQueue.Entry entry, @Nonnull final JID serverAddress)
    {
        final Packet copy = entry.getPacket().createCopy();
        if ( ( copy instanceof Message || copy instanceof Presence ) && copy.getExtension( "delay", "urn:xmpp:delay" ) == null ) {
            final Element delayInformation = copy.getElement().addElement( "delay", "urn:xmpp:delay" );
            delayInformation.addAttribute( "stamp", XMPPDateTimeFormat.format( entry.getTimestamp() ) );
            delayInformation.addAttribute( "from", serverAddress.toBareJID() );
        }
        return copy;
    }

    /**
     * Creates the error with which the sender of a stanza learns that the stanza may not have reached its recipient, if
     * an error can be returned for it. The condition is 'service-unavailable', with type 'cancel': XEP-0198 § 4 says to
     * treat unacknowledged stanzas as stanzas sent to an unavailable resource, and for an IQ sent to such a resource a
     * server returns 'service-unavailable' (RFC 6121 § 8.5.3.2.3). 'recipient-unavailable' is not used, as the error is
     * returned whatever the availability of the recipient, and RFC 6120 § 8.3.3.19 requires 'service-unavailable' where
     * 'recipient-unavailable' would reveal that availability to an entity that is not authorized to know it. The error
     * swaps the addresses of the stanza, and has its ID (RFC 6120 § 8.3.1), but does not include its content.
     *
     * No error is returned for:
     * <ul>
     *     <li>a stanza of type 'error', as an error must not be answered with an error (RFC 6120 § 8.3.1);</li>
     *     <li>an IQ result, as it must not be answered with an IQ response (RFC 6120 § 8.2.3);</li>
     *     <li>a presence without a type or of type 'unavailable', which a server "MUST silently ignore" when it is sent
     *     to an unavailable resource (RFC 6121 § 8.5.3.2.2);</li>
     *     <li>a presence of type 'probe', which a server sends on behalf of a user (RFC 6121 § 4.3), so that no client of
     *     that user awaits an answer;</li>
     *     <li>a presence of type 'subscribe', 'subscribed', 'unsubscribe' or 'unsubscribed', which a server stamps with
     *     the bare JID of the user (RFC 6121 § 3), to which Openfire does not route a presence.</li>
     * </ul>
     *
     * @param packet The stanza.
     * @return the error response, or null when no error is to be returned for the stanza.
     */
    @Nullable
    private static Packet createErrorResponse(@Nonnull final Packet packet)
    {
        if ( "error".equals( packet.getElement().attributeValue( "type" ) ) ) {
            return null;
        }
        if ( packet instanceof IQ iq ) {
            if ( !iq.isRequest() ) {
                return null;
            }
            final IQ error = IQ.createResultIQ( iq );
            error.setError( PacketError.Condition.service_unavailable );
            return error;
        }
        if ( packet instanceof Message message ) {
            final Message error = new Message();
            error.setID( message.getID() );
            error.setTo( message.getFrom() );
            error.setFrom( message.getTo() );
            error.setThread( message.getThread() );
            error.setType( Message.Type.error );
            error.setError( PacketError.Condition.service_unavailable );
            return error;
        }
        // OF-2425: A presence of any type is dropped, as RFC 6121 § 8.5.3.2.2 says for one sent to an unavailable resource.
        return null;
    }

    /**
     * Refuses to resume the session, as a stream on which Openfire asks for Stream Management cannot be resumed.
     *
     * @param serverAddress this server's bare-domain address.
     * @param h the number of stanzas that the peer reports to have handled.
     * @throws UnsupportedOperationException always.
     */
    @Override
    public void onResume(JID serverAddress, long h)
    {
        throw new UnsupportedOperationException( "Resumption of a stream on which Openfire asks for Stream Management is not supported (OF-2425). Session: " + session );
    }
}
