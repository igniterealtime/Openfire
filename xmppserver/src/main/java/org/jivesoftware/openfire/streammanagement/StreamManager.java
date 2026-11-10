/*
 * Copyright (C) 2017-2026 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.net.Bind2Request;
import org.jivesoftware.openfire.session.*;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.TaskEngine;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.*;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * XEP-0198 Stream Manager.
 * Handles the acknowledgement of stanzas on a stream: inbound stanzas are those that Openfire receives from the peer,
 * outbound stanzas are those that Openfire sends to the peer.
 *
 * This class holds what both ends of a stream share. What the end that answers requests of the peer does is implemented
 * by {@link AnsweringStreamManager}, and what the end that asks for Stream Management does by
 * {@link RequestingStreamManager}. {@link #forSession(LocalSession)} creates the stream manager for a session.
 *
 * @author jonnyheavey
 */
public abstract class StreamManager {

    public static SystemProperty<Boolean> LOCATION_ENABLED = SystemProperty.Builder.ofType( Boolean.class )
        .setKey("stream.management.location.enabled")
        .setDefaultValue(true)
        .setDynamic(true)
        .build();

    public static SystemProperty<Boolean> LOCATION_TERMINATE_OTHERS_ENABLED = SystemProperty.Builder.ofType( Boolean.class )
        .setKey("stream.management.location.terminate-others.enabled")
        .setDefaultValue(true)
        .setDynamic(true)
        .build();

    public static SystemProperty<Boolean> MAX_SERVER_ENABLED = SystemProperty.Builder.ofType( Boolean.class )
        .setKey("stream.management.max-server.enabled")
        .setDefaultValue(true)
        .setDynamic(true)
        .build();

    public static SystemProperty<Boolean> ACTIVE = SystemProperty.Builder.ofType( Boolean.class )
        .setKey("stream.management.active")
        .setDefaultValue(true)
        .setDynamic(true)
        .build();

    /**
     * Whether Stream Management is used on connections with other servers: offered to servers that connect to
     * Openfire, and enabled on connections that Openfire opens to other servers. This is separate from
     * {@link #ACTIVE}, which applies to clients.
     */
    public static SystemProperty<Boolean> SERVER_ACTIVE = SystemProperty.Builder.ofType( Boolean.class )
        .setKey("stream.management.server.active")
        .setDefaultValue(false)
        .setDynamic(true)
        .build();

    final Logger Log;

    /**
     * Whether the stream can be resumed. It is read by the thread of the connection that resumes the stream.
     */
    private volatile boolean resume = false;

    /**
     * An outbound stanza that the peer has not acknowledged yet.
     *
     * @deprecated Stream Management does not use this class, which has no replacement.
     */
    @Deprecated(forRemoval = true, since = "5.2.0") // Remove in or after Openfire 5.3.0
    public static class UnackedPacket {
        public final long x;
        public final Date timestamp = new Date();
        public final Packet packet;

        public UnackedPacket(long x, Packet p) {
            this.x = x;
            packet = p;
        }
    }

    public static boolean isStreamManagementActive() {
        return ACTIVE.getValue();
    }

    /**
     * Stanza namespaces
     */
    public static final String NAMESPACE_V2 = "urn:xmpp:sm:2";
    public static final String NAMESPACE_V3 = "urn:xmpp:sm:3";

    /**
     * Session (stream) to the peer.
     */
    final LocalSession session;

    /**
     * Namespace to be used in stanzas sent to the peer (depending on XEP-0198 version used by the peer). It is read by
     * the threads that deliver stanzas to the session, and by the thread of the connection that resumes the stream.
     */
    private volatile String namespace;

    /**
     * Count of how many inbound stanzas/packets Openfire has handled
     */
    private AtomicLong handledInboundStanzas = new AtomicLong( 0 );

    /**
     * Whether the number of unacknowledged stanzas exceeded the maximum, which causes the session to be closed.
     */
    private boolean exceededMaximumUnacknowledgedStanzas = false;

    /**
     * The value (2^32)-1, used to emulate roll-over and the largest legal value for the 'h' attribute (XEP-0198 § 4).
     */
    static final long MASK = new BigInteger( "2" ).pow( 32 ).longValue() - 1;

    /**
     * Outbound stanzas/packets that haven't been acknowledged, and the count of those that have been.
     */
    private final UnacknowledgedStanzaQueue unacknowledgedOutboundStanzas;

    /**
     * Delegates that can determine if a detached session can be terminated.
     */
    private final Set<TerminationDelegate> terminationDelegates = new HashSet<>();

    /**
     * The SM-ID of the stream, which is set when resumption is enabled (XEP-0198 § 5). It is read by the thread of the
     * connection that resumes the stream.
     */
    private volatile String smId;

    /**
     * Creates the stream manager for a session.
     *
     * @param session The session (cannot be null).
     */
    protected StreamManager(LocalSession session) {
        String address;
        try {
            final Connection connection = session.getConnection();
            if (connection != null) {
                address = connection.getHostAddress();
            } else {
                // This smells: Why would a stream manager be created for a session without a connection (which typically is a session that is already detached)?
                LoggerFactory.getLogger(StreamManager.class).warn("Connection is null for session: {}", session.getAddress());
                address = null;
            }
        }
        catch ( UnknownHostException e )
        {
            address = null;
        }

        this.Log = LoggerFactory.getLogger(StreamManager.class + "["+ (address == null ? "(unknown address)" : address) +"]" );
        this.unacknowledgedOutboundStanzas = new UnacknowledgedStanzaQueue( Log, this::getMaximumUnacknowledgedStanzas );
        this.session = session;
    }

    /**
     * Creates the stream manager that matches the end of the stream that Openfire is on in a session: on a connection
     * that Openfire opened to another server, Openfire asks for Stream Management; on all other sessions, it answers
     * requests of the peer.
     *
     * @param session The session (cannot be null).
     * @return the stream manager for the session (never null).
     */
    @Nonnull
    public static StreamManager forSession(@Nonnull final LocalSession session) {
        if (session instanceof LocalOutgoingServerSession) {
            return new RequestingStreamManager(session);
        }
        return new AnsweringStreamManager(session);
    }

    /**
     * Returns true if a stream is resumable.
     *
     * @return True if a stream is resumable.
     */
    public boolean getResume() {
        return resume;
    }

    /**
     * Sets whether the stream can be resumed.
     *
     * @param resume true if the stream can be resumed.
     */
    void setResume(final boolean resume) {
        this.resume = resume;
    }

    /**
     * Returns the namespace of the version of Stream Management used on the stream, or null if Stream Management is
     * not enabled.
     *
     * @return the namespace (possibly null).
     */
    String getNamespace() {
        return namespace;
    }

    /**
     * Sets the namespace of the version of Stream Management used on the stream. Setting null disables Stream
     * Management.
     *
     * @param namespace the namespace (possibly null).
     */
    void setNamespace(final String namespace) {
        this.namespace = namespace;
    }

    /**
     * Returns the SM-ID of the stream, which is set when resumption is enabled.
     *
     * @return the SM-ID (possibly null).
     */
    String getSmId() {
        return smId;
    }

    /**
     * Sets the SM-ID of the stream.
     *
     * @param smId the SM-ID.
     */
    void setSmId(final String smId) {
        this.smId = smId;
    }

    /**
     * Returns the number of inbound stanzas that Openfire has handled since Stream Management was enabled, as it is
     * reported to the peer (rolled over at 2^32).
     *
     * @return the number of handled inbound stanzas.
     */
    long getHandledInboundStanzas() {
        return handledInboundStanzas.get() & MASK;
    }

    /**
     * Processes a stream management element.
     *
     * Inline resume requests, nested in a SASL2 <authenticate/> element, are not processed here; see AnsweringStreamManager#processSasl2Resume(ResumeRequest)
     *
     * @param element The stream management element to be processed.
     */
    public void process( Element element )
    {
        switch(element.getName()) {
            case "r":
                acknowledgeInboundStanzas();
                break;
            case "a":
                processOutboundAcknowledgement(element);
                break;
            default:
                if ( !processNegotiation( element ) ) {
                    sendUnexpectedError();
                }
        }
    }

    /**
     * Processes a Stream Management element that is part of the negotiation at this end of the stream, such as a
     * request to enable Stream Management or to resume a stream.
     *
     * @param element The element received from the peer (cannot be null).
     * @return true if the element was processed, false if it is not part of the negotiation at this end of the stream.
     */
    abstract boolean processNegotiation( @Nonnull Element element );

    /**
     * Enables stream management, returning the {@code <enabled/>} element rather than sending it.
     *
     * A stream manager that does not answer requests of the peer refuses this.
     *
     * @param namespace the SM namespace to use
     * @param resume    whether the peer requests a resumable session
     * @return the {@code <enabled/>} element
     * @throws StreamManagementException when stream management could not be enabled, carrying the condition to report
     * @deprecated Use {@link AnsweringStreamManager#enableAndBuildElement(String, boolean)}, on the stream manager of a
     * session that answers requests of the peer.
     */
    @Deprecated(forRemoval = true, since = "5.2.0") // Remove in or after Openfire 5.3.0
    @Nonnull
    public Element enableAndBuildElement( String namespace, boolean resume ) throws StreamManagementException
    {
        throw new StreamManagementException(PacketError.Condition.unexpected_request,
            "Stream management is not enabled at the request of the peer on this session.");
    }

    /**
     * Attempts to process an inline SASL2 resume request.
     *
     * A stream manager that does not answer requests of the peer refuses this.
     *
     * @param request the parsed inline resume request (cannot be null).
     * @return the outcome of the resume attempt.
     * @deprecated Use {@link AnsweringStreamManager#processSasl2Resume(ResumeRequest)}, on the stream manager of a
     * session that answers requests of the peer.
     */
    @Deprecated(forRemoval = true, since = "5.2.0") // Remove in or after Openfire 5.3.0
    @Nonnull
    public Sasl2ResumeResult processSasl2Resume(@Nonnull final ResumeRequest request)
    {
        Log.debug("Refusing inline resumption of {}, as Openfire does not answer requests of the peer on session {}.", request.getPrevId(), session);
        return Sasl2ResumeResult.failure(buildFailedElement(request.getNamespace(), PacketError.Condition.unexpected_request));
    }

    /**
     * Called when a session receives a closing stream tag, this prevents the
     * session from being detached.
     */
    public void formalClose() {
        Log.debug("formalClose() invoked for session with address {} and streamID {}; resumption is now permanently disabled for this stream.", session.getAddress(), session.getStreamID());
        this.resume = false;
    }

    /**
     * Sends XEP-0198 acknowledgement &lt;a /&gt; of inbound stanzas to the peer
     */
    public void acknowledgeInboundStanzas() {
        // Read once, as another thread can disable Stream Management at any time.
        final String currentNamespace = namespace;
        if(currentNamespace != null) {
            if (session.isDetached()) {
                Log.debug("Session is detached, won't request an ack.");
                return;
            }
            String ack = String.format("<a xmlns='%s' h='%s' />", currentNamespace, handledInboundStanzas.get() & MASK );
            session.deliverRawText( ack );
        }
    }

    /**
     * Sends XEP-0198 acknowledgement &lt;a /&gt; of inbound stanzas to the peer
     *
     * @deprecated Use {@link #acknowledgeInboundStanzas()} instead.
     */
    @Deprecated(forRemoval = true, since = "5.2.0") // Remove in or after Openfire 5.3.0
    public void sendServerAcknowledgement() {
        acknowledgeInboundStanzas();
    }

    /**
     * Sends XEP-0198 request <r /> to the peer, for an acknowledgement of outbound stanzas
     */
    private void requestOutboundAcknowledgement() {
        // Read once, as another thread can disable Stream Management at any time.
        final String currentNamespace = namespace;
        if(currentNamespace != null) {
            if (session.isDetached()) {
                Log.debug("Session is detached, won't request an ack.");
                return;
            }
            String request = String.format("<r xmlns='%s' />", currentNamespace);
            session.deliverRawText( request );
        }
    }

    /**
     * Send an error if a XEP-0198 stanza is received at an unexpected time.
     * e.g. before resource-binding has completed.
     */
    private void sendUnexpectedError() {
        sendError(new PacketError( PacketError.Condition.unexpected_request ));
    }

    /**
     * Send a generic failed error.
     *
     * @param error PacketError describing the failure.
     */
    void sendError(PacketError error) {
        final Element failed = buildFailedElement(namespace, error.getCondition());
        session.deliverRawText(failed.asXML());
        this.namespace = null; // isEnabled() is testing this.
    }

    /**
     * Constructs an XML element representing a failed stream management negotiation.
     *
     * @param namespace The namespace indicating the version of stream management being used. Must not be null.
     * @param condition The specific error condition that caused the failure. Must not be null.
     * @return An XML {@code <failed>} element containing information about the failure.
     */
    static Element buildFailedElement(@Nonnull final String namespace, @Nonnull final PacketError.Condition condition) {
        final Element failed = DocumentHelper.createElement(QName.get("failed", namespace));
        failed.addElement(QName.get(condition.toXMPP(), "urn:ietf:params:xml:ns:xmpp-stanzas"));
        return failed;
    }

    /**
     * Checks if the amount of stanzas that the peer acknowledges is not higher than the amount of stanzas that we've
     * sent to the peer.
     *
     * This holds the lock of this instance, so that the check does not see the queue of unacknowledged stanzas halfway
     * through a sequence of changes that this instance makes under that lock. It is safe to invoke on the stream
     * manager of another session.
     *
     * @param h Then number of stanzas that the peer acknowledges it has received from us.
     * @return false if we sent fewer stanzas to the peer than the number it is acknowledging.
     */
    synchronized boolean validateOutboundAcknowledgement(long h) {
        return unacknowledgedOutboundStanzas.isValidAcknowledgement(h);
    }

    /**
     * Describes the last stanza that the peer has not acknowledged, for use in error messages.
     *
     * @return a description of the last unacknowledged stanza.
     */
    String describeLastUnacknowledged() {
        return unacknowledgedOutboundStanzas.describeLast();
    }

    /**
     * Records that a number of outbound stanzas were sent that the peer counts, but that are not tracked, so that the
     * stanzas that are tracked after them are numbered as the peer counts them. This must only be invoked while no
     * stanzas are unacknowledged.
     *
     * @param count the number of outbound stanzas that were sent without being tracked.
     */
    synchronized void skipOutboundStanzas(final long count) {
        unacknowledgedOutboundStanzas.skip(count);
    }

    /**
     * Returns the stanzas that the peer has not acknowledged, as an unmodifiable copy that does not reflect later
     * changes.
     *
     * @return the unacknowledged stanzas, in the order in which they were sent.
     */
    @Nonnull
    Collection<UnacknowledgedStanzaQueue.Entry> getUnacknowledgedEntries() {
        return unacknowledgedOutboundStanzas.getEntries();
    }

    /**
     * Handles the stanzas that the peer did not acknowledge, for a session that closed without being resumed, and
     * disables Stream Management for the session. How these stanzas are handled depends on the end of the stream.
     *
     * This must be invoked after the session is removed.
     *
     * @param router the router used to route the stanzas.
     * @param serverAddress this server's bare-domain address, used to stamp delay information on messages.
     */
    public abstract void onClose(PacketRouter router, JID serverAddress);

    /**
     * Completes the resumption of the session of this stream manager on a new connection, which is attached to the
     * session already, and sends the stanzas again that the peer reports it did not receive. What else is sent
     * depends on the end of the stream.
     *
     * @param serverAddress this server's bare-domain address, used to stamp delay information on stanzas sent again.
     * @param h the number of stanzas that the peer reports to have handled.
     */
    public abstract void onResume(JID serverAddress, long h);

    /**
     * Receive and process acknowledgement packet of outbound stanzas from the peer
     * @param ack XEP-0198 acknowledgement <a /> stanza to process
     */
    private void processOutboundAcknowledgement(Element ack) {
        if(isEnabled()) {
            if (ack.attribute("h") != null) {
                final String hValue = ack.attributeValue("h");
                final long h;
                try {
                    h = Long.parseLong(hValue);
                } catch (NumberFormatException e) {
                    Log.warn( "Closing session. Peer sends non-numeric value for SM 'h': {}, affected session: {}", hValue, session );
                    final StreamError error = new StreamError( StreamError.Condition.undefined_condition, "You acknowledged stanzas using a 'h' value that is not a number (which is illegal). Your Ack h: " + hValue + ", our last unacknowledged stanza: " + unacknowledgedOutboundStanzas.describeLast() );
                    // OF-3443: Unlike close(), this also makes the stream non-resumable (OF-2751).
                    session.close( error );
                    return;
                }
                if (h < 0) {
                    Log.warn( "Closing session. Peer sends negative value for SM 'h': {}, affected session: {}", h, session );
                    final StreamError error = new StreamError( StreamError.Condition.undefined_condition, "You acknowledged stanzas using a negative value (which is illegal). Your Ack h: " + h + ", our last unacknowledged stanza: " + unacknowledgedOutboundStanzas.describeLast() );
                    // OF-3443: Unlike close(), this also makes the stream non-resumable (OF-2751).
                    session.close( error );
                    return;
                }
                if (h > MASK) {
                    Log.warn( "Closing session. Peer sends value for SM 'h' that is larger than 2^32-1: {}, affected session: {}", h, session );
                    final StreamError error = new StreamError( StreamError.Condition.undefined_condition, "You acknowledged stanzas using a value larger than 2^32-1 (which is illegal). Your Ack h: " + h + ", our last unacknowledged stanza: " + unacknowledgedOutboundStanzas.describeLast() );
                    // OF-3443: Unlike close(), this also makes the stream non-resumable (OF-2751).
                    session.close( error );
                    return;
                }

                Log.debug( "Received acknowledgement from peer: h={}", h );

                synchronized ( this ) {
                    if (!validateOutboundAcknowledgement(h)) {
                        Log.warn( "Closing session. Peer acknowledges stanzas that we didn't send! Peer Ack h: {}, our last unacknowledged stanza: {}, affected session: {}", h, unacknowledgedOutboundStanzas.describeLast(), session );
                        final StreamError error = new StreamError( StreamError.Condition.undefined_condition, "You acknowledged stanzas that we didn't send. Your Ack h: " + h + ", our last unacknowledged stanza: " + unacknowledgedOutboundStanzas.describeLast() );
                        final long sendCount = unacknowledgedOutboundStanzas.getLastSentNumber();
                        error.getElement().addElement( QName.get( "handled-count-too-high", namespace ) ).addAttribute( "h", String.valueOf( h ) ).addAttribute( "send-count", String.valueOf( sendCount ) );
                        // OF-3443: Unlike close(), this also makes the stream non-resumable (OF-2751).
                        session.close( error );
                        return;
                    }

                    unacknowledgedOutboundStanzas.acknowledge( h );
                }
            }
        }
    }

    /**
     * Registers that Openfire sends a stanza to the peer (which is expected to be acknowledged later).
     * @param packet The stanza that is sent.
     */
    public void sentStanza(Packet packet) {
        sentStanza(packet, false);
    }

    /**
     * Registers that Openfire sends a stanza to the peer (which is expected to be acknowledged later).
     *
     * @param packet The stanza that is sent.
     * @param sentAgain whether the stanza was sent before, on a connection that broke before the stanza was
     *                  acknowledged.
     */
    void sentStanza(@Nonnull final Packet packet, final boolean sentAgain) {

        if(isEnabled()) {
            final long requestFrequency = JiveGlobals.getLongProperty( "stream.management.requestFrequency", 5 );
            final int size;
            final boolean closeForExceedingMaximum;

            synchronized (this)
            {
                final Connection connection = session.getConnection();
                if ( exceededMaximumUnacknowledgedStanzas && packet instanceof Message && connection != null && connection.isClosed() && connection.getPacketDeliverer() != null ) {
                    // OF-3434: The fallback of the closed connection stores this message offline. Tracking it as well would deliver it twice.
                    // The fallback drops other stanzas, so they remain tracked, to be handled when the session closes.
                    return;
                }

                final long x = unacknowledgedOutboundStanzas.add( packet.createCopy(), sentAgain );

                size = unacknowledgedOutboundStanzas.size();

                Log.trace( "Added stanza of type '{}' to collection of unacknowledged stanzas (x={}, sent again: {}). Collection size is now {}.", packet.getElement().getName(), x, sentAgain, size );

                // Prevent keeping to many stanzas in memory. OF-3434: the stanzas are kept, to be handled when the session closes.
                closeForExceedingMaximum = unacknowledgedOutboundStanzas.exceedsMaximum() && !exceededMaximumUnacknowledgedStanzas;
                if ( closeForExceedingMaximum ) {
                    exceededMaximumUnacknowledgedStanzas = true;
                }
            }

            if ( closeForExceedingMaximum ) {
                closeForExceedingMaximum( size );
                return;
            }

            // When we have a sizable amount of unacknowledged stanzas, request acknowledgement.
            if ( size % requestFrequency == 0 ) {
                Log.debug( "Requesting acknowledgement from peer, as we have {} or more unacknowledged stanzas.", requestFrequency );
                requestOutboundAcknowledgement();
            }
        }

    }

    /**
     * Ends the session after the number of unacknowledged stanzas exceeded the maximum.
     *
     * XEP-0198 defines no such maximum, and no way to stop tracking stanzas on an open stream. Instead, the stream is
     * closed with a stream error, and resumption is disabled. The session then closes, which handles its unacknowledged
     * stanzas as for any other session that closes without being resumed (XEP-0198 § 4). This also applies to a detached
     * session that is resumed before it could be ended.
     *
     * @param size The number of unacknowledged stanzas.
     */
    private void closeForExceedingMaximum( final int size )
    {
        Log.warn( "Closing session, as too many stanzas go unacknowledged: {}, the maximum is {}. Affected session: {}", size, getMaximumUnacknowledgedStanzas(), session );
        formalClose();
        final Connection connection = session.getConnection();
        if ( connection != null ) {
            connection.close( new StreamError( StreamError.Condition.policy_violation, "Too many stanzas went unacknowledged: " + size + ", while the maximum is " + getMaximumUnacknowledgedStanzas() + "." ) );
            // OF-3434: If the connection was already closing, its close listener can have detached the session before resumption was disabled. End it then.
            connection.getCloseFuture().thenRun( () -> {
                if ( session.isDetached() ) {
                    TaskEngine.getInstance().submit( () -> XMPPServer.getInstance().getSessionManager().terminateDetached( session ) );
                }
            } );
        } else {
            // OF-3434: A detached session has no connection to close. End it now, rather than after its detach timeout.
            TaskEngine.getInstance().submit( () -> XMPPServer.getInstance().getSessionManager().terminateDetached( session ) );
        }
    }

    /**
     * Processes the client's acknowledgement of 'h' as reported in its (traditional or inline SASL2) resume request,
     * and retransmits any stanzas that remain unacknowledged after that.
     *
     * This is the second half of what {@link #onResume(JID, long)} does for the traditional resume flow. It is
     * split out so that the inline SASL2 resume flow can defer this until after it has delivered its own response
     * (typically, the SASL2 {@code <success/>} that embeds the {@code <resumed/>} element built by
     * {@link AnsweringStreamManager#buildResumedElement()}), handled by {@link LocalSession#completeSasl2Resume(long)}.
     *
     * @param serverAddress this server's bare-domain address, used to stamp delay information on redelivered stanzas.
     * @param h the sequence number of the last handled stanza, as reported by the client that is resuming.
     */
    public void redeliverUnackedStanzas(@Nonnull final JID serverAddress, final long h) {
        Log.debug("Resuming session: Ack for {}", h);
        synchronized (this) {
            unacknowledgedOutboundStanzas.acknowledge( h );
        }
        synchronized (this) {
            if (exceededMaximumUnacknowledgedStanzas) {
                // OF-3434: The maximum was exceeded while detached. Closing under this lock keeps a stanza that LocalClientSession#pushPackets() delivers from going untracked.
                closeForExceedingMaximum(unacknowledgedOutboundStanzas.size());
                return;
            }
            // OF-3434: Re-delivering under the same lock keeps a stanza that exceeds the maximum from closing the stream first, which would route the re-delivered stanzas twice.
            Log.debug("Processing remaining unacked stanzas");
            final Connection connection = session.getConnection();
            assert connection != null; // While the client is resuming a session, the connection on which the session is resumed can't be null.
            // Re-deliver unacknowledged stanzas from broken stream (XEP-0198)
            if(isEnabled()) {
                for (final UnacknowledgedStanzaQueue.Entry unacked : unacknowledgedOutboundStanzas.getEntries()) {
                    try {
                        if (unacked.getPacket() instanceof Message) {
                            Message m = (Message) unacked.getPacket();
                            if (m.getExtension("delay", "urn:xmpp:delay") == null) {
                                Element delayInformation = m.addChildElement("delay", "urn:xmpp:delay");
                                delayInformation.addAttribute("stamp", XMPPDateTimeFormat.format(unacked.getTimestamp()));
                                delayInformation.addAttribute("from", serverAddress.toBareJID());
                            }
                            connection.deliver(m);
                        } else if (unacked.getPacket() instanceof Presence) {
                            Presence p = (Presence) unacked.getPacket();
                            if (p.getExtension("delay", "urn:xmpp:delay") == null) {
                                Element delayInformation = p.addChildElement("delay", "urn:xmpp:delay");
                                delayInformation.addAttribute("stamp", XMPPDateTimeFormat.format(unacked.getTimestamp()));
                                delayInformation.addAttribute("from", serverAddress.toBareJID());
                            }
                            connection.deliver(p);
                        } else {
                            connection.deliver(unacked.getPacket());
                        }
                    } catch (UnauthorizedException e) {
                        Log.warn("Caught unauthorized exception, which seems worrying: ", e);
                    }
                }

                requestOutboundAcknowledgement();
            }
        }
    }

    /**
     * Determines whether Stream Management enabled for session this
     * manager belongs to.
     * @return true when stream management is enabled, otherwise false.
     */
    public boolean isEnabled() {
        return namespace != null;
    }

    /**
     * Increments the count of inbound stanzas that Openfire has handled since
     * Stream Management was enabled.
     *
     * This is part of the processing of every inbound stanza. An invocation for a stanza that was not handled makes the
     * count that is reported to the peer incorrect.
     */
    public void incrementHandledInboundStanzas() {
        if(isEnabled()) {
            this.handledInboundStanzas.incrementAndGet();
        }
    }

    /**
     * Increments the count of inbound stanzas that Openfire has handled since
     * Stream Management was enabled.
     *
     * This is part of the processing of every inbound stanza. An invocation for a stanza that was not handled makes the
     * count that is reported to the peer incorrect.
     *
     * @deprecated Use {@link #incrementHandledInboundStanzas()} instead.
     */
    @Deprecated(forRemoval = true, since = "5.2.0") // Remove in or after Openfire 5.3.0
    public void incrementServerProcessedStanzas() {
        incrementHandledInboundStanzas();
    }

    /**
     * The maximum amount of stanzas we keep, waiting for ack.
     * @return The maximum number of stanzas.
     */
    private int getMaximumUnacknowledgedStanzas()
    {
        return JiveGlobals.getIntProperty( "stream.management.max-unacked", 10000 );
    }

    /**
     * Returns a defensive copy of all delegates that can determine if a detached session can be terminated.
     *
     * @return all delegates that can determine if a detached session can be terminated.
     */
    public Set<TerminationDelegate> getTerminationDelegates()
    {
        return new HashSet<>(terminationDelegates);
    }

    /**
     * Adds a new delegate that can determine if a detached session can be terminated. When no such delegate is
     * registered for a session, the server default behavior will determine if a detached session can be terminated.
     *
     * This method will add delegates, unless the new delegate is equal to a previously registered delegate. In such
     * case, this method will silently ignore the invocation.
     *
     * @param delegate the delegate to register with the session
     */
    public void addTerminationDelegate(@Nonnull final TerminationDelegate delegate)
    {
        terminationDelegates.add(delegate);
    }

    /**
     * Removes a delegate that can determine if a detached session can be terminated. When no such delegate is
     * registered for a session, the server default behavior will determine if a detached session can be terminated.
     *
     * This method will silently ignore an invocation to remove a delegate that was not registered with the session.
     *
     * @param delegate the delegate to register with the session
     */
    public void removeTerminationDelegate(@Nonnull final TerminationDelegate delegate)
    {
        terminationDelegates.remove(delegate);
    }

    /**
     * Returns the element that advertises support for inline stream resumption in the {@code <inline/>} element of
     * the SASL2 stream feature, as defined in XEP-0198 § 9.2.
     *
     * Note that this is distinct from the Bind2 inline feature (XEP-0198 § 9.1) that allows a client to enable
     * stream management as part of a resource bind: that one is advertised through {@link Bind2Request#featureElement()}.
     *
     * @return the {@code <sm/>} feature element.
     */
    @Nonnull
    public static Element sasl2InlineFeatureElement() {
        return DocumentHelper.createElement(QName.get("sm", NAMESPACE_V3));
    }
}
