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
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.TaskEngine;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;
import org.xmpp.packet.StreamError;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
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
 * When resumption on server connections is enabled ({@link StreamManager#SERVER_RESUME_ENABLED}), Openfire asks the
 * peer for a stream that can be resumed. When the connection of such a stream breaks, its session is kept, and stanzas
 * that are delivered to the session are queued, in order, behind the unacknowledged ones, until the stream is resumed
 * or the session ends. Queued stanzas are not sent, and are therefore not counted or tracked. When the session ends
 * without being resumed, they are routed again, after the unacknowledged stanzas are handled. When more stanzas are
 * queued than {@link #MAX_QUEUED_WHILE_BROKEN} allows, the session is ended.
 *
 * Such a session is resumed on a new connection, of which the session asks the peer to resume the stream (see
 * {@link #requestResume(LocalOutgoingServerSession, Collection)}). When the peer agrees, the connection is attached to
 * the session that is resumed, which then sends the stanzas that the peer did not receive, followed by the queued
 * stanzas (see {@link #onResume(JID, long)}).
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class RequestingStreamManager extends StreamManager
{
    /**
     * The maximum number of stanzas that are queued on a session of which the connection broke while its stream can be
     * resumed. When more are queued, the session is ended, after which the queued stanzas are routed again.
     */
    public static final SystemProperty<Integer> MAX_QUEUED_WHILE_BROKEN = SystemProperty.Builder.ofType( Integer.class )
        .setKey( "stream.management.server.resume.max-queued" )
        .setDefaultValue( 10000 )
        .setMinValue( 0 )
        .setDynamic( true )
        .build();

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
     * The progress of a request to resume a stream that Openfire sent to the peer on a new connection.
     */
    public enum ResumeRequestStatus {
        /**
         * Openfire has not asked the peer to resume a stream on this connection.
         */
        NOT_SENT,

        /**
         * Openfire asked the peer to resume a stream, and has not received an answer yet.
         */
        PENDING,

        /**
         * The peer answered the request to resume a stream, which is processed, whatever the answer.
         */
        ANSWERED,

        /**
         * Openfire gave up on the request to resume a stream before the peer answered it, or before it was sent. An answer
         * that arrives after this is ignored.
         */
        ABANDONED
    }

    /**
     * The progress of a request to enable Stream Management that Openfire sent to the peer. Changed under the lock of
     * this instance.
     */
    private volatile EnableRequestStatus enableRequestStatus = EnableRequestStatus.NOT_SENT;

    /**
     * The progress of a request to resume a stream that Openfire sent to the peer. Changed under the lock of this
     * instance.
     */
    private volatile ResumeRequestStatus resumeRequestStatus = ResumeRequestStatus.NOT_SENT;

    /**
     * The detached session of which Openfire asked the peer to resume the stream on the connection of this session, if
     * any. Guarded by the lock of this instance.
     */
    @Nullable
    private LocalOutgoingServerSession resumeTarget;

    /**
     * The namespace of the request to resume a stream that Openfire sent to the peer, if any. Guarded by the lock of this
     * instance.
     */
    @Nullable
    private String resumeNamespace;

    /**
     * Completes when a request to resume a stream is answered and processed, or when it was not sent as the stream
     * cannot be resumed on this connection.
     */
    private final CompletableFuture<Void> resumeRequestFinished = new CompletableFuture<>();

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
     * Whether the request to enable Stream Management that Openfire sent to the peer asked for a stream that can be
     * resumed. Guarded by the lock of this instance.
     */
    private boolean resumeRequested = false;

    /**
     * The preferred maximum resumption time that the peer reported in the 'max' attribute of {@code <enabled/>}, or
     * null when it reported none, or the stream cannot be resumed.
     */
    @Nullable
    private volatile Duration peerMaximumResumptionTime;

    /**
     * Copies of the stanzas that were delivered to the session while its connection was broken, in the order in which
     * they were delivered. They are not sent, and are therefore not counted or tracked. Guarded by the lock of this
     * instance.
     */
    private final Deque<Packet> queuedStanzas = new ArrayDeque<>();

    /**
     * Whether stanzas that are delivered to the session are queued rather than sent. Once set, this stays set until the
     * session ends, or until the queued stanzas are sent on a resumed stream (see {@link #onResume(JID, long)}). Guarded
     * by the lock of this instance.
     */
    private boolean queueing = false;

    /**
     * Whether more stanzas were queued than {@link #MAX_QUEUED_WHILE_BROKEN} allows. Guarded by the lock of this
     * instance.
     */
    private boolean queueLimitExceeded = false;

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
                processEnableResponse( element );
                break;
            case "failed":
                // OF-2425: On a connection on which Openfire asked to resume a stream, <failed/> answers that request.
                if ( resumeRequestStatus != ResumeRequestStatus.NOT_SENT ) {
                    processResumeResponse( element );
                } else {
                    processEnableResponse( element );
                }
                break;
            case "resumed":
                processResumeResponse( element );
                break;
            default:
                // OF-2425: The fallback of the base class disables Stream Management, after which the stanzas that it tracks are not handled when the session closes.
                Log.debug( "Refusing '{}' from the peer of session {}, as Openfire asks for Stream Management on this stream.", element.getName(), session );
                refuse( element );
        }
        return true;
    }

    /**
     * Asks the peer to enable Stream Management. A stream that can be resumed is requested when
     * {@link StreamManager#SERVER_RESUME_ENABLED} is set. The answer of the peer is processed by
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
        final boolean resume = StreamManager.SERVER_RESUME_ENABLED.getValue();
        synchronized ( this ) {
            if ( enableRequestStatus != EnableRequestStatus.NOT_SENT || isEnabled() ) {
                Log.debug( "Not asking the peer of session {} to enable Stream Management, as that was asked or skipped before (status: {}) or it is enabled (enabled: {}).", session, enableRequestStatus, isEnabled() );
                return;
            }
            requestedNamespace = namespace;
            resumeRequested = resume;
            enableRequestStatus = EnableRequestStatus.PENDING;
            Log.debug( "Asking the peer of session {} to enable Stream Management ({}), requesting resumption: {}.", session, namespace, resume );
            final Element enable = DocumentHelper.createElement( QName.get( "enable", namespace ) );
            if ( resume ) {
                enable.addAttribute( "resume", "true" );
            }
            session.deliverRawText( enable.asXML() );
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
     * Asks the peer to resume the stream of a detached session on the connection of the session of this stream manager,
     * instead of enabling Stream Management on it (XEP-0198 § 5). The request uses the SM-ID of that stream, the number
     * of stanzas that Openfire handled on it, and the version of Stream Management that was used on it, which the peer
     * must offer on this connection. The answer of the peer is processed by {@link #process(Element)}: when the peer
     * agrees, the detached session is resumed with the connection of this session (see
     * {@link LocalOutgoingServerSession#resumeWithConnectionOf(LocalOutgoingServerSession, long)}). When the peer
     * refuses, or its answer is invalid, the connection is closed, and the detached session is ended, as when it is not
     * resumed in time.
     *
     * No request is sent when Stream Management or resumption on server connections is disabled, when the stream of the
     * detached session cannot be resumed, when the peer does not offer its version of Stream Management, or when a
     * request to enable Stream Management or to resume a stream was sent on this connection before. The future returned
     * by {@link #getResumeRequestFinished()} then completes, and the caller is responsible for ending the detached
     * session.
     *
     * @param target The detached session of which the stream is to be resumed.
     * @param offeredNamespaces The namespaces of the versions of Stream Management that the peer offers on this connection.
     */
    public void requestResume(@Nonnull final LocalOutgoingServerSession target, @Nonnull final Collection<String> offeredNamespaces)
    {
        final RequestingStreamManager targetStreamManager = target.getStreamManager();
        final String namespace = targetStreamManager.getNamespace();
        final String previd = targetStreamManager.getSmId();
        final boolean resumable = targetStreamManager.getResume();
        final boolean active = StreamManager.SERVER_ACTIVE.getValue() && StreamManager.SERVER_RESUME_ENABLED.getValue();
        final boolean send;
        synchronized ( this ) {
            send = resumeRequestStatus == ResumeRequestStatus.NOT_SENT && enableRequestStatus == EnableRequestStatus.NOT_SENT && !isEnabled()
                && active && resumable && namespace != null && previd != null && offeredNamespaces.contains( namespace );
            if ( send ) {
                resumeTarget = target;
                resumeNamespace = namespace;
                resumeRequestStatus = ResumeRequestStatus.PENDING;
            }
        }
        if ( !send ) {
            Log.debug( "Not asking the peer of session {} to resume the stream of session {}. Enabled for servers: {}, resumable: {}, version used: {}, versions offered: {}, request status: {}, enable request status: {}.", session, target, active, resumable, namespace, offeredNamespaces, resumeRequestStatus, enableRequestStatus );
            resumeRequestFinished.complete( null );
            return;
        }
        final long h = targetStreamManager.getHandledInboundStanzas();
        Log.debug( "Asking the peer of session {} to resume the stream of session {} ({}, previd: {}, h: {}).", session, target, namespace, previd, h );
        final Element resume = DocumentHelper.createElement( QName.get( "resume", namespace ) );
        resume.addAttribute( "previd", previd );
        resume.addAttribute( "h", Long.toString( h ) );
        session.deliverRawText( resume.asXML() );
    }

    /**
     * Returns the progress of a request to resume a stream that Openfire sent using
     * {@link #requestResume(LocalOutgoingServerSession, Collection)}.
     *
     * @return the progress of the request.
     */
    @Nonnull
    public ResumeRequestStatus getResumeRequestStatus()
    {
        return resumeRequestStatus;
    }

    /**
     * Returns a future that completes when a request to resume a stream is answered and processed, whatever the answer,
     * or when the request was not sent (see {@link #requestResume(LocalOutgoingServerSession, Collection)}). Completing
     * the returned future does not affect this instance.
     *
     * @return a future that completes when the request to resume a stream is finished.
     */
    @Nonnull
    public CompletableFuture<Void> getResumeRequestFinished()
    {
        return resumeRequestFinished.copy();
    }

    /**
     * Gives up on a request to resume a stream that was not answered, for example because the peer did not answer in
     * time, or because it was not sent. An answer that arrives after this is ignored. The caller that gives up is
     * responsible for ending the detached session of which the stream was to be resumed.
     *
     * @return true if the request was given up, false if the peer answered it already, in which case the processing of
     * that answer takes care of the detached session.
     */
    public synchronized boolean abandonResume()
    {
        if ( resumeRequestStatus == ResumeRequestStatus.NOT_SENT || resumeRequestStatus == ResumeRequestStatus.PENDING ) {
            Log.debug( "Giving up on resuming a stream on the connection of session {} (request status: {}).", session, resumeRequestStatus );
            resumeRequestStatus = ResumeRequestStatus.ABANDONED;
            return true;
        }
        return false;
    }

    /**
     * Processes the {@code <resumed/>} or {@code <failed/>} element with which the peer answers a request of Openfire to
     * resume a stream. An answer that arrives after the request was given up is ignored, and one that does not answer a
     * request is refused.
     *
     * When the peer resumed the stream, with the SM-ID of that stream and a valid 'h', the detached session is resumed
     * with the connection of this session. When it cannot be taken from the detached sessions, because it was ended
     * meanwhile, the connection is closed. In all other cases, the connection is closed, with a stream error when the
     * answer is invalid, and the detached session is ended.
     *
     * @param element The answer of the peer.
     */
    private void processResumeResponse(@Nonnull final Element element)
    {
        final LocalOutgoingServerSession target;
        final String requested;
        synchronized ( this ) {
            if ( resumeRequestStatus != ResumeRequestStatus.PENDING ) {
                if ( resumeRequestStatus == ResumeRequestStatus.ABANDONED ) {
                    Log.debug( "Ignoring '{}' from the peer of session {}, as Openfire gave up on resuming a stream on this connection.", element.getName(), session );
                } else {
                    Log.debug( "Refusing '{}' from the peer of session {}, as no request of Openfire to resume a stream awaits an answer (status: {}).", element.getName(), session, resumeRequestStatus );
                    refuse( element );
                }
                return;
            }
            resumeRequestStatus = ResumeRequestStatus.ANSWERED;
            target = resumeTarget;
            requested = resumeNamespace;
        }
        assert target != null && requested != null; // Set together with the status PENDING.

        try {
            if ( "failed".equals( element.getName() ) ) {
                Log.debug( "The peer of session {} did not resume the stream of session {}, reporting: {} (h: {}).", session, target, element.elements().stream().map( Element::getName ).findFirst().orElse( "(no condition)" ), element.attributeValue( "h" ) );
                endFailedResumption( target, null );
                return;
            }

            final RequestingStreamManager targetStreamManager = target.getStreamManager();
            final String previd = element.attributeValue( "previd" );
            final String hValue = element.attributeValue( "h" );
            final Long h = parseH( hValue );
            if ( !requested.equals( element.getNamespaceURI() ) || !Objects.equals( targetStreamManager.getSmId(), previd ) || h == null ) {
                Log.debug( "The peer of session {} answered the request to resume the stream of session {} ({}, SM-ID {}) with an invalid 'resumed' (namespace: {}, previd: {}, h: {}).", session, target, requested, targetStreamManager.getSmId(), element.getNamespaceURI(), previd, hValue );
                endFailedResumption( target, new StreamError( StreamError.Condition.undefined_condition, "The 'resumed' element does not match the request to resume the stream, or has an invalid 'h': namespace " + element.getNamespaceURI() + ", previd " + previd + ", h " + hValue ) );
                return;
            }
            if ( !targetStreamManager.validateOutboundAcknowledgement( h ) ) {
                final long sendCount = targetStreamManager.getLastSentNumber();
                Log.debug( "The peer of session {} resumed the stream of session {}, acknowledging stanzas that were not sent (h: {}, send-count: {}).", session, target, h, sendCount );
                final StreamError error = new StreamError( StreamError.Condition.undefined_condition, "You acknowledged stanzas that we didn't send. Your Ack h: " + h + ", our last unacknowledged stanza: " + targetStreamManager.describeLastUnacknowledged() );
                error.getElement().addElement( QName.get( "handled-count-too-high", requested ) ).addAttribute( "h", String.valueOf( h ) ).addAttribute( "send-count", String.valueOf( sendCount ) );
                endFailedResumption( target, error );
                return;
            }
            if ( !( session instanceof LocalOutgoingServerSession connectionProvider ) ) {
                // Unreachable: StreamManager#forSession creates this class for outgoing server sessions only.
                endFailedResumption( target, new StreamError( StreamError.Condition.internal_server_error ) );
                return;
            }
            Log.debug( "The peer of session {} resumed the stream of session {} (h: {}).", session, target, h );
            if ( !target.resumeWithConnectionOf( connectionProvider, h ) ) {
                // OF-2425: The session was ended meanwhile, by its cleanup task or for exceeding a limit; that handles its stanzas.
                Log.debug( "Closing the connection of session {}, as session {} could not be resumed on it: it is no longer detached.", session, target );
                session.close();
            }
        } finally {
            resumeRequestFinished.complete( null );
        }
    }

    /**
     * Closes the connection on which a stream could not be resumed, and ends the detached session of that stream on
     * another thread, as when it is not resumed in time.
     *
     * @param target The detached session of which the stream was not resumed.
     * @param error The stream error with which to close the connection, or null to close it without one.
     */
    private void endFailedResumption(@Nonnull final LocalOutgoingServerSession target, @Nullable final StreamError error)
    {
        session.close( error );
        TaskEngine.getInstance().submit( () -> XMPPServer.getInstance().getSessionManager().terminateDetached( target ) );
    }

    /**
     * Parses a value of 'h': a number from zero up to and including 2^32-1 (XEP-0198 § 4).
     *
     * @param value The value (possibly null).
     * @return the number, or null when the value is not a valid value of 'h'.
     */
    @Nullable
    private static Long parseH(@Nullable final String value)
    {
        if ( value == null ) {
            return null;
        }
        try {
            final long h = Long.parseLong( value );
            return h < 0 || h > MASK ? null : h;
        } catch ( NumberFormatException e ) {
            return null;
        }
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
        final boolean resumable;
        synchronized ( this ) {
            requested = requestedNamespace;
            answersRequest = enableRequestStatus == EnableRequestStatus.PENDING;
            // A peer that answers in a version other than the one requested is not taken to have enabled that version.
            enabled = answersRequest && "enabled".equals( element.getName() ) && element.getNamespaceURI().equals( requested );
            // XEP-0198 § 5: a peer that allows resumption sets 'resume' to "true" or "1", and includes an 'id'.
            resumable = enabled && resumeRequested && isTrue( element.attributeValue( "resume" ) ) && element.attributeValue( "id" ) != null && !element.attributeValue( "id" ).isBlank();
            if ( enabled ) {
                // OF-2425: The peer counts the stanzas that were sent since the request (XEP-0198 § 4), so those that follow are numbered after them.
                skipOutboundStanzas( sentWhilePending );
                if ( resumable ) {
                    setSmId( element.attributeValue( "id" ) );
                    peerMaximumResumptionTime = parseMaximumResumptionTime( element.attributeValue( "max" ) );
                    setResume( true );
                }
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

        if ( resumable ) {
            Log.debug( "The peer of session {} enabled Stream Management ({}), and allows the stream to be resumed (SM-ID: {}, preferred maximum resumption time: {}). Stanzas sent while the request awaited an answer, which are counted but not tracked: {}.", session, requested, getSmId(), peerMaximumResumptionTime, sentWhilePending );
        } else if ( enabled ) {
            // An offer of resumption that was not requested, or that lacks an 'id', is not used.
            Log.debug( "The peer of session {} enabled Stream Management ({}), without a stream that can be resumed (requested: {}, 'resume': {}, 'id': {}). Stanzas sent while the request awaited an answer, which are counted but not tracked: {}.", session, requested, resumeRequested, element.attributeValue( "resume" ), element.attributeValue( "id" ), sentWhilePending );
        } else {
            Log.debug( "The peer of session {} did not enable Stream Management ({}). It answered with '{}' in namespace '{}', reporting: {}", session, requested, element.getName(), element.getNamespaceURI(), element.elements().stream().map( Element::getName ).findFirst().orElse( "(no condition)" ) );
        }
        negotiationOutcome.complete( enabled ? EnableRequestStatus.ENABLED : EnableRequestStatus.REFUSED );
    }

    /**
     * Checks whether the value of a boolean attribute is true: "true" or "1" (XML Schema boolean).
     *
     * @param value The value of the attribute (possibly null).
     * @return true if the value is true.
     */
    private static boolean isTrue(@Nullable final String value)
    {
        return "true".equals( value ) || "1".equals( value );
    }

    /**
     * Parses the 'max' attribute of {@code <enabled/>}: the preferred maximum resumption time of the peer, in seconds.
     *
     * @param value The value of the attribute (possibly null).
     * @return the preferred maximum resumption time, or null when there is none, or it is not a positive number.
     */
    @Nullable
    private Duration parseMaximumResumptionTime(@Nullable final String value)
    {
        if ( value == null ) {
            return null;
        }
        try {
            final long seconds = Long.parseLong( value.trim() );
            if ( seconds > 0 ) {
                return Duration.ofSeconds( seconds );
            }
        } catch ( NumberFormatException e ) {
            // Handled below.
        }
        Log.debug( "Ignoring the preferred maximum resumption time of the peer of session {}, as it is not a positive number of seconds: '{}'", session, value );
        return null;
    }

    /**
     * Returns the preferred maximum resumption time that the peer reported when it enabled Stream Management with a
     * stream that can be resumed (the 'max' attribute of {@code <enabled/>}, XEP-0198 § 3).
     *
     * @return the preferred maximum resumption time of the peer, or null when it reported none, or the stream cannot be resumed.
     */
    @Nullable
    public Duration getPeerMaximumResumptionTime()
    {
        return peerMaximumResumptionTime;
    }

    /**
     * Records that the connection of the session broke while its stream can be resumed, after which stanzas that are
     * delivered to the session are queued rather than sent (see {@link #queue(Packet, boolean)}). This has no effect
     * when Stream Management is not enabled.
     */
    public synchronized void startQueueing()
    {
        if ( isEnabled() && !queueing ) {
            Log.debug( "Queueing stanzas for session {} from now on, as its connection broke while its stream can be resumed.", session );
            queueing = true;
        }
    }

    /**
     * Queues a stanza that is delivered to the session, if it is not to be sent: after {@link #startQueueing()}, or when
     * the connection of the session is broken while its stream can be resumed and resumption on server connections is
     * enabled. A copy of the stanza is queued, which keeps the marker of a stanza that is routed again (see
     * {@link #copyRoutedAgainMarker(Packet, Packet)}). A queued stanza is not counted or tracked, as it is not sent.
     *
     * When more stanzas are queued than {@link #MAX_QUEUED_WHILE_BROKEN} allows, the stanza is queued, and the session
     * is ended, which routes the queued stanzas again.
     *
     * The caller that holds the lock of this instance while it decides between this and sending a stanza, can rely on
     * a stanza not being sent once stanzas are queued.
     *
     * @param packet The stanza that is delivered to the session.
     * @param connectionBroken Whether the session has no connection, or its connection is closed.
     * @return true if the stanza is queued, false if it is to be handled otherwise.
     */
    public boolean queue(@Nonnull final Packet packet, final boolean connectionBroken)
    {
        final int size;
        final boolean limitExceeded;
        synchronized ( this ) {
            if ( !isEnabled() ) {
                // OF-2425: onClose() handled the queued stanzas already, so a stanza that is queued now would never be handled.
                return false;
            }
            if ( !queueing ) {
                if ( !connectionBroken || !getResume() || !StreamManager.SERVER_RESUME_ENABLED.getValue() ) {
                    return false;
                }
                Log.debug( "Queueing stanzas for session {} from now on, as its connection broke while its stream can be resumed.", session );
                queueing = true;
            }
            final Packet copy = packet.createCopy();
            copyRoutedAgainMarker( packet, copy );
            queuedStanzas.add( copy );
            size = queuedStanzas.size();
            limitExceeded = !queueLimitExceeded && size > MAX_QUEUED_WHILE_BROKEN.getValue();
            if ( limitExceeded ) {
                queueLimitExceeded = true;
            }
        }
        Log.trace( "Queued '{}' stanza of type '{}' with ID '{}' for session {}, of which the connection is broken. Queued stanzas: {}", packet.getElement().getName(), packet.getElement().attributeValue( "type" ), packet.getID(), session, size );
        if ( limitExceeded ) {
            endForExceedingQueueLimit( size );
        }
        return true;
    }

    /**
     * Returns the stanzas that are queued, as an unmodifiable copy that does not reflect later changes.
     *
     * @return the queued stanzas, in the order in which they were queued.
     */
    @Nonnull
    synchronized List<Packet> getQueuedStanzas()
    {
        return List.copyOf( queuedStanzas );
    }

    /**
     * Ends the session after more stanzas were queued than {@link #MAX_QUEUED_WHILE_BROKEN} allows (decision 7 of
     * OF-2425), as when the session is not resumed in time: the stream is made non-resumable, so that a session that is
     * not detached yet is not detached, and a session that is detached is ended. Ending it is done by another thread.
     * A session that is being resumed is no longer detached, and is not ended: its queued stanzas are sent on the resumed
     * stream, which can then not be resumed again.
     *
     * @param size The number of queued stanzas.
     */
    private void endForExceedingQueueLimit(final int size)
    {
        Log.debug( "Ending session {}, as {} stanzas are queued while its connection is broken, while the maximum is {}.", session, size, MAX_QUEUED_WHILE_BROKEN.getValue() );
        formalClose();
        final Connection connection = session.getConnection();
        if ( connection != null ) {
            // OF-2425: Its close listener can have detached the session before resumption was disabled. End it then.
            connection.getCloseFuture().thenRun( () -> {
                if ( session.isDetached() ) {
                    TaskEngine.getInstance().submit( () -> XMPPServer.getInstance().getSessionManager().terminateDetached( session ) );
                }
            } );
        } else {
            TaskEngine.getInstance().submit( () -> XMPPServer.getInstance().getSessionManager().terminateDetached( session ) );
        }
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
     * Stanzas that were queued while the connection of the session was broken (see {@link #queue(Packet, boolean)})
     * were never sent. They are routed again after the unacknowledged stanzas, in the order in which they were queued,
     * without a delay stamp, and keep the marker of a stanza that was routed again before. No stanza is queued after
     * this.
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
        final List<Packet> queued;
        synchronized ( this ) {
            if ( !isEnabled() ) {
                return;
            }
            unacknowledged = getUnacknowledgedEntries();
            queued = new ArrayList<>( queuedStanzas );
            queuedStanzas.clear();
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
        // OF-2425: Queued stanzas were never sent, so they go out over a new connection after the unacknowledged ones (decision 7).
        for ( final Packet packet : queued ) {
            try {
                router.route( packet );
            } catch ( Exception e ) {
                Log.warn( "Unable to route a queued '{}' stanza of type '{}' with ID '{}', of session {} that closed.", packet.getElement().getName(), packet.getElement().attributeValue( "type" ), packet.getID(), session, e );
            }
        }
        if ( !unacknowledged.isEmpty() || !queued.isEmpty() ) {
            Log.debug( "Session {} closed without being resumed, with {} unacknowledged stanza(s): {} routed again, {} returned to the sender as an error, {} dropped as no error can be returned for them. Queued stanzas that were never sent, routed again: {}", session, unacknowledged.size(), sentAgain, returned, dropped, queued.size() );
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
     * Completes the resumption of the session on a new connection, which is attached to the session already, after the
     * peer resumed the stream with {@code <resumed/>}, and sends what the peer did not receive.
     *
     * The stanzas that 'h' acknowledges are removed. The stanzas that remain unacknowledged are sent again, in order,
     * and each is marked as sent again as it is sent: messages and presences get a delay stamp (XEP-0203) with the time
     * at which they were first sent, if they do not have one. A stanza is sent again at most once (decision 4 of OF-2425): a stanza that was
     * sent again before is not sent again, but returned to its sender as an error, or dropped, as when the session closes
     * without being resumed (see {@link #onClose(PacketRouter, JID)}). The stanzas that were queued while the connection
     * was broken are sent after that, in order, and are tracked as they are sent. Then queueing stops.
     *
     * This holds the lock of this instance until queueing stops. A stanza that is delivered to the session meanwhile is
     * queued, under the same lock, so it is sent after the queued stanzas, and no stanza is sent on the new connection
     * before the unacknowledged ones. When the connection closes while the unacknowledged stanzas are sent again, the
     * stanzas that were not sent again stay unacknowledged, without being marked as sent again. When it closes while the
     * queued stanzas are sent, the stanzas that were not sent stay queued, and queueing continues.
     *
     * When the maximum number of unacknowledged stanzas was exceeded while the session was detached, the stream is
     * closed instead, after which the session closes as one that cannot be resumed.
     *
     * The caller validated 'h' (see {@link #validateOutboundAcknowledgement(long)}).
     *
     * @param serverAddress this server's bare-domain address, used to stamp delay information on stanzas sent again.
     * @param h the number of stanzas that the peer reports to have handled.
     */
    @Override
    public void onResume(JID serverAddress, long h)
    {
        final List<UnacknowledgedStanzaQueue.Entry> notSentAgain;
        synchronized ( this ) {
            acknowledgeOutboundStanzas( h );
            if ( hasExceededMaximumUnacknowledgedStanzas() ) {
                // OF-3434: as at the end that answers, the stream is closed under this lock, so that no stanza is sent on it without being tracked.
                closeForExceedingMaximum( getUnacknowledgedEntries().size() );
                return;
            }
            final Connection connection = session.getConnection();
            if ( !isEnabled() || connection == null ) {
                Log.debug( "Not sending stanzas on resumed session {}, as Stream Management is not enabled ({}) or it has no connection ({}).", session, isEnabled(), connection );
                return;
            }

            notSentAgain = removeSentAgainAndRenumber();
            int sentAgain = 0;
            for ( final UnacknowledgedStanzaQueue.Entry entry : getUnacknowledgedEntries() ) {
                if ( connection.isClosed() ) {
                    // OF-2425: The stanzas that are not sent keep their number, and have not used their one retry (decision 4).
                    break;
                }
                final Packet packet = entry.getPacket();
                if ( ( packet instanceof Message || packet instanceof Presence ) && packet.getExtension( "delay", "urn:xmpp:delay" ) == null ) {
                    final Element delayInformation = packet.getElement().addElement( "delay", "urn:xmpp:delay" );
                    delayInformation.addAttribute( "stamp", XMPPDateTimeFormat.format( entry.getTimestamp() ) );
                    delayInformation.addAttribute( "from", serverAddress.toBareJID() );
                }
                deliverOnResumedConnection( connection, packet );
                entry.markSentAgain();
                sentAgain++;
            }

            int sentQueued = 0;
            while ( !queuedStanzas.isEmpty() && !connection.isClosed() ) {
                final Packet packet = queuedStanzas.removeFirst();
                deliverOnResumedConnection( connection, packet );
                // OF-2425: Queued stanzas were never sent, so they are numbered now, after the ones that are sent again.
                sentStanza( packet, isRoutedAgain( packet ) );
                sentQueued++;
            }
            if ( queuedStanzas.isEmpty() ) {
                queueing = false;
            }
            Log.debug( "Resumed session {} (h: {}): sent again {} unacknowledged stanza(s), not sent again {} that were sent again before, sent {} queued stanza(s). Stanzas that remain queued, as the connection closed: {}", session, h, sentAgain, notSentAgain.size(), sentQueued, queuedStanzas.size() );
            if ( sentAgain > 0 || sentQueued > 0 ) {
                requestOutboundAcknowledgement();
            }
        }

        // OF-2425: Routed without holding the lock, as routing can deliver them to another session.
        if ( !notSentAgain.isEmpty() ) {
            final PacketRouter router = XMPPServer.getInstance().getPacketRouter();
            for ( final UnacknowledgedStanzaQueue.Entry entry : notSentAgain ) {
                final Packet packet = entry.getPacket();
                try {
                    final Packet error = createErrorResponse( packet );
                    Log.trace( "Not sending unacknowledged '{}' stanza of type '{}' with ID '{}' of resumed session {} again, as it was sent again before. Returning it to its sender as an error: {}", packet.getElement().getName(), packet.getElement().attributeValue( "type" ), packet.getID(), session, error != null );
                    if ( error != null ) {
                        router.route( error );
                    }
                } catch ( Exception e ) {
                    // The stanza itself is not logged, as it can have private content.
                    Log.warn( "Unable to return an unacknowledged '{}' stanza of type '{}' with ID '{}', of resumed session {}, to its sender.", packet.getElement().getName(), packet.getElement().attributeValue( "type" ), packet.getID(), session, e );
                }
            }
        }
    }

    /**
     * Delivers a stanza on the connection of a resumed session.
     *
     * @param connection The connection.
     * @param packet The stanza.
     */
    private void deliverOnResumedConnection(@Nonnull final Connection connection, @Nonnull final Packet packet)
    {
        try {
            connection.deliver( packet );
        } catch ( UnauthorizedException e ) {
            Log.warn( "Unable to deliver a '{}' stanza of type '{}' with ID '{}' on the connection of resumed session {}.", packet.getElement().getName(), packet.getElement().attributeValue( "type" ), packet.getID(), session, e );
        }
    }
}
