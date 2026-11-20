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

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.jivesoftware.util.TaskEngine;
import org.xmpp.packet.StreamError;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A request that Openfire sends to the peer on a new connection, to resume the stream of a detached outgoing server
 * session on that connection (XEP-0198 § 5), and the processing of the answer of the peer.
 *
 * Each {@link RequestingStreamManager} has one, which is used only when its connection is used to resume the stream of
 * another session. On such a connection, Openfire either asks to resume a stream or asks to enable Stream Management,
 * never both. The state of the request is therefore guarded by the lock of that stream manager, under which both are
 * decided.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public final class OutboundResumeRequest
{
    /**
     * The progress of a request to resume a stream that Openfire sent to the peer on a new connection.
     */
    public enum Status {
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
     * The stream manager of the connection on which the request is sent, of which the lock guards the state of this
     * request.
     */
    @Nonnull
    private final RequestingStreamManager owner;

    /**
     * The progress of the request. Changed under the lock of {@link #owner}.
     */
    private volatile Status status = Status.NOT_SENT;

    /**
     * The detached session of which Openfire asked the peer to resume the stream, if any. Guarded by the lock of
     * {@link #owner}.
     */
    @Nullable
    private LocalOutgoingServerSession target;

    /**
     * The namespace of the request, if any. Guarded by the lock of {@link #owner}.
     */
    @Nullable
    private String namespace;

    /**
     * Completes when the request is answered and processed, or when it was not sent as the stream cannot be resumed on
     * the connection.
     */
    private final CompletableFuture<Void> finished = new CompletableFuture<>();

    OutboundResumeRequest(@Nonnull final RequestingStreamManager owner)
    {
        this.owner = owner;
    }

    /**
     * Asks the peer to resume the stream of a detached session on the connection of the session of the stream manager
     * of this request, instead of enabling Stream Management on it (XEP-0198 § 5). The request uses the SM-ID of that
     * stream, the number of stanzas that Openfire handled on it, and the version of Stream Management that was used on
     * it, which the peer must offer on this connection. The answer of the peer is processed by
     * {@link RequestingStreamManager#process(Element)}: when the peer agrees, the detached session is resumed with the
     * connection of that session (see
     * {@link LocalOutgoingServerSession#resumeWithConnectionOf(LocalOutgoingServerSession, long)}). When the peer
     * refuses, or its answer is invalid, the connection is closed, and the detached session is ended, as when it is not
     * resumed in time.
     *
     * No request is sent when Stream Management or resumption on server connections is disabled, when the stream of the
     * detached session cannot be resumed, when the peer does not offer its version of Stream Management, or when a
     * request to enable Stream Management was sent on this connection before. The future returned by
     * {@link #getFinished()} then completes, and the caller is responsible for ending the detached session.
     *
     * Whether to ask is decided once per connection. A later invocation, for example after the peer sent its stream
     * features again, has no effect: a request that was sent keeps waiting for its answer.
     *
     * @param target The detached session of which the stream is to be resumed.
     * @param offeredNamespaces The namespaces of the versions of Stream Management that the peer offers on this connection.
     */
    public void send(@Nonnull final LocalOutgoingServerSession target, @Nonnull final Collection<String> offeredNamespaces)
    {
        final RequestingStreamManager targetStreamManager = target.getStreamManager();
        final String targetNamespace = targetStreamManager.getNamespace();
        final String previd = targetStreamManager.getSmId();
        final boolean resumable = targetStreamManager.getResume();
        final boolean active = StreamManager.isServerResumptionEnabled();
        final boolean send;
        synchronized ( owner ) {
            if ( status != Status.NOT_SENT || finished.isDone() ) {
                // A repeated call, for example after the peer sent its stream features again, leaves the request that was decided on before as it is.
                owner.Log.debug( "Not asking the peer of session {} again to resume the stream of session {}, as that was decided on before on this connection (request status: {}).", owner.session, target, status );
                return;
            }
            send = owner.getEnableRequestStatus() == RequestingStreamManager.EnableRequestStatus.NOT_SENT && !owner.isEnabled()
                && active && resumable && targetNamespace != null && previd != null && offeredNamespaces.contains( targetNamespace );
            if ( send ) {
                this.target = target;
                this.namespace = targetNamespace;
                status = Status.PENDING;
            }
        }
        if ( !send ) {
            owner.Log.debug( "Not asking the peer of session {} to resume the stream of session {}. Enabled for servers: {}, resumable: {}, version used: {}, versions offered: {}, request status: {}, enable request status: {}.", owner.session, target, active, resumable, targetNamespace, offeredNamespaces, status, owner.getEnableRequestStatus() );
            finished.complete( null );
            return;
        }
        final long h = targetStreamManager.getHandledInboundStanzas();
        owner.Log.debug( "Asking the peer of session {} to resume the stream of session {} ({}, previd: {}, h: {}).", owner.session, target, targetNamespace, previd, h );
        final Element resume = DocumentHelper.createElement( QName.get( "resume", targetNamespace ) );
        resume.addAttribute( "previd", previd );
        resume.addAttribute( "h", Long.toString( h ) );
        owner.session.deliverRawText( resume.asXML() );
    }

    /**
     * Returns the progress of the request.
     *
     * @return the progress of the request.
     */
    @Nonnull
    public Status getStatus()
    {
        return status;
    }

    /**
     * Returns a future that completes when the request is answered and processed, whatever the answer, or when it was
     * not sent (see {@link #send(LocalOutgoingServerSession, Collection)}). Completing the returned future does not affect
     * this instance.
     *
     * @return a future that completes when the request is finished.
     */
    @Nonnull
    public CompletableFuture<Void> getFinished()
    {
        return finished.copy();
    }

    /**
     * Gives up on the request when it was not answered, for example because the peer did not answer in time, or because
     * it was not sent. An answer that arrives after this is ignored. The caller that gives up is responsible for ending
     * the detached session of which the stream was to be resumed.
     *
     * @return true if the request was given up, false if the peer answered it already, in which case the processing of
     * that answer takes care of the detached session.
     */
    public boolean abandon()
    {
        synchronized ( owner ) {
            if ( status == Status.NOT_SENT || status == Status.PENDING ) {
                owner.Log.debug( "Giving up on resuming a stream on the connection of session {} (request status: {}).", owner.session, status );
                status = Status.ABANDONED;
                return true;
            }
            return false;
        }
    }

    /**
     * Processes the {@code <resumed/>} or {@code <failed/>} element with which the peer answers the request. An answer
     * that arrives after the request was given up is ignored, and one that does not answer a request is refused.
     *
     * When the peer resumed the stream, with the SM-ID of that stream and a valid 'h', the detached session is resumed
     * with the connection of the session of {@link #owner}. When it cannot be taken from the detached sessions, because
     * it was ended meanwhile, the connection is closed. In all other cases, the connection is closed, with a stream
     * error when the answer is invalid, and the detached session is ended. Before that, the stanzas that a valid 'h' of
     * a {@code <failed/>} in the namespace of the request acknowledges are removed from the detached session (see
     * {@link #acknowledgeOnFailedResumption(LocalOutgoingServerSession, String)}).
     *
     * @param element The answer of the peer.
     */
    void processResponse(@Nonnull final Element element)
    {
        final LocalOutgoingServerSession resumeTarget;
        final String requested;
        synchronized ( owner ) {
            if ( status != Status.PENDING ) {
                if ( status == Status.ABANDONED ) {
                    owner.Log.debug( "Ignoring '{}' from the peer of session {}, as Openfire gave up on resuming a stream on this connection.", element.getName(), owner.session );
                } else {
                    owner.Log.debug( "Refusing '{}' from the peer of session {}, as no request of Openfire to resume a stream awaits an answer (status: {}).", element.getName(), owner.session, status );
                    owner.refuse( element );
                }
                return;
            }
            status = Status.ANSWERED;
            resumeTarget = target;
            requested = namespace;
        }
        assert resumeTarget != null && requested != null; // Set together with the status PENDING.

        try {
            if ( "failed".equals( element.getName() ) ) {
                owner.Log.debug( "The peer of session {} did not resume the stream of session {}, reporting: {} (h: {}).", owner.session, resumeTarget, element.elements().stream().map( Element::getName ).findFirst().orElse( "(no condition)" ), element.attributeValue( "h" ) );
                // OF-2425: As for 'resumed', an answer in another namespace than that of the request is not trusted to count stanzas as that request does.
                if ( requested.equals( element.getNamespaceURI() ) ) {
                    acknowledgeOnFailedResumption( resumeTarget, element.attributeValue( "h" ) );
                } else {
                    owner.Log.debug( "Ignoring the 'h' of the 'failed' with which the peer of session {} answered, as its namespace ({}) is not that of the request to resume the stream of session {} ({}).", owner.session, element.getNamespaceURI(), resumeTarget, requested );
                }
                endFailedResumption( resumeTarget, null );
                return;
            }

            final RequestingStreamManager targetStreamManager = resumeTarget.getStreamManager();
            final String previd = element.attributeValue( "previd" );
            final String hValue = element.attributeValue( "h" );
            final Long h = parseH( hValue );
            if ( !requested.equals( element.getNamespaceURI() ) || !Objects.equals( targetStreamManager.getSmId(), previd ) || h == null ) {
                owner.Log.debug( "The peer of session {} answered the request to resume the stream of session {} ({}, SM-ID {}) with an invalid 'resumed' (namespace: {}, previd: {}, h: {}).", owner.session, resumeTarget, requested, targetStreamManager.getSmId(), element.getNamespaceURI(), previd, hValue );
                endFailedResumption( resumeTarget, new StreamError( StreamError.Condition.undefined_condition, "The 'resumed' element does not match the request to resume the stream, or has an invalid 'h': namespace " + element.getNamespaceURI() + ", previd " + previd + ", h " + hValue ) );
                return;
            }
            if ( !targetStreamManager.validateOutboundAcknowledgement( h ) ) {
                final long sendCount = targetStreamManager.getLastSentNumber();
                owner.Log.debug( "The peer of session {} resumed the stream of session {}, acknowledging stanzas that were not sent (h: {}, send-count: {}).", owner.session, resumeTarget, h, sendCount );
                final StreamError error = new StreamError( StreamError.Condition.undefined_condition, "You acknowledged stanzas that we didn't send. Your Ack h: " + h + ", our last unacknowledged stanza: " + targetStreamManager.describeLastUnacknowledged() );
                error.getElement().addElement( QName.get( "handled-count-too-high", requested ) ).addAttribute( "h", String.valueOf( h ) ).addAttribute( "send-count", String.valueOf( sendCount ) );
                endFailedResumption( resumeTarget, error );
                return;
            }
            if ( !targetStreamManager.isOutboundAcknowledgementNotLowerThanBefore( h ) ) {
                // OF-2425: Stanzas sent while <enable/> awaited its answer were counted as acknowledged without being tracked. When the peer did not receive them all, the stanzas that follow cannot be numbered as it counts them.
                owner.Log.debug( "The peer of session {} resumed the stream of session {} with an 'h' that is lower than the number of stanzas that were acknowledged, or counted as such, before (h: {}, send-count: {}). Not resuming the stream.", owner.session, resumeTarget, h, targetStreamManager.getLastSentNumber() );
                endFailedResumption( resumeTarget, new StreamError( StreamError.Condition.undefined_condition, "Your 'h' (" + h + ") is lower than the number of stanzas that this server counts as acknowledged. The stream cannot be resumed." ) );
                return;
            }
            if ( !( owner.session instanceof LocalOutgoingServerSession connectionProvider ) ) {
                // Unreachable: StreamManager#forSession creates RequestingStreamManager for outgoing server sessions only.
                endFailedResumption( resumeTarget, new StreamError( StreamError.Condition.internal_server_error ) );
                return;
            }
            owner.Log.debug( "The peer of session {} resumed the stream of session {} (h: {}).", owner.session, resumeTarget, h );
            if ( !resumeTarget.resumeWithConnectionOf( connectionProvider, h ) ) {
                // OF-2425: The session was ended meanwhile, by its cleanup task or for exceeding a limit; that handles its stanzas.
                owner.Log.debug( "Closing the connection of session {}, as session {} could not be resumed on it: it is no longer detached.", owner.session, resumeTarget );
                owner.session.close();
            }
        } finally {
            finished.complete( null );
        }
    }

    /**
     * Removes the stanzas of a detached session that the peer reports to have received in the 'h' attribute of the
     * {@code <failed/>} with which it refused to resume the stream of that session (XEP-0198 § 5), so that they are not
     * sent again or returned to their sender as an error when the session is ended. A value of 'h' that is missing, is
     * not a valid value, or acknowledges stanzas that were not sent, is ignored.
     *
     * @param resumeTarget The detached session of which the stream was not resumed.
     * @param hValue The value of the 'h' attribute of the {@code <failed/>} (possibly null).
     */
    private void acknowledgeOnFailedResumption(@Nonnull final LocalOutgoingServerSession resumeTarget, @Nullable final String hValue)
    {
        if ( hValue == null ) {
            return;
        }
        final RequestingStreamManager targetStreamManager = resumeTarget.getStreamManager();
        final Long h = parseH( hValue );
        synchronized ( targetStreamManager ) {
            if ( h == null || !targetStreamManager.validateOutboundAcknowledgement( h ) ) {
                owner.Log.debug( "Ignoring the 'h' of the 'failed' with which the peer refused to resume the stream of session {}, as it is not valid: {} (send-count: {}).", resumeTarget, hValue, targetStreamManager.getLastSentNumber() );
                return;
            }
            targetStreamManager.acknowledgeOutboundStanzas( h );
        }
        owner.Log.debug( "The peer refused to resume the stream of session {}, reporting that it received stanzas up to h: {}. Those are not handled again when the session is ended.", resumeTarget, h );
    }

    /**
     * Closes the connection on which a stream could not be resumed, and ends the detached session of that stream on
     * another thread, as when it is not resumed in time.
     *
     * @param resumeTarget The detached session of which the stream was not resumed.
     * @param error The stream error with which to close the connection, or null to close it without one.
     */
    private void endFailedResumption(@Nonnull final LocalOutgoingServerSession resumeTarget, @Nullable final StreamError error)
    {
        owner.session.close( error );
        TaskEngine.getInstance().submit( () -> XMPPServer.getInstance().getSessionManager().terminateDetached( resumeTarget ) );
    }

    /**
     * Parses a value of 'h': a number from zero up to and including 2^32-1 (XEP-0198 § 4).
     *
     * @param value The value (possibly null).
     * @return the number, or null when the value is not a valid value of 'h'.
     */
    @Nullable
    static Long parseH(@Nullable final String value)
    {
        if ( value == null ) {
            return null;
        }
        try {
            final long h = Long.parseLong( value );
            return h < 0 || h > StreamManager.MASK ? null : h;
        } catch ( NumberFormatException e ) {
            return null;
        }
    }
}
