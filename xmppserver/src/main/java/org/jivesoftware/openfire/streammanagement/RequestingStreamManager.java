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
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.session.LocalSession;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * XEP-0198 Stream Management at the end of a stream that asks for it, and that the peer answers, such as a connection
 * that Openfire opened to another server.
 *
 * Only Openfire asks on such a stream. Every Stream Management element of the peer that this end does not process,
 * including a request to enable Stream Management or to resume a stream, is answered with {@code <failed/>}, without
 * changing the state of Stream Management.
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
            Log.debug( "The peer of session {} enabled Stream Management ({}). Offered resumption, which is not used: {}", session, requested, element.attributeValue( "resume" ) );
        } else {
            Log.debug( "The peer of session {} did not enable Stream Management ({}). It answered with '{}' in namespace '{}', reporting: {}", session, requested, element.getName(), element.getNamespaceURI(), element.elements().stream().map( Element::getName ).findFirst().orElse( "(no condition)" ) );
        }
        negotiationOutcome.complete( enabled ? EnableRequestStatus.ENABLED : EnableRequestStatus.REFUSED );
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
     * Disables Stream Management for the session. The stanzas that the peer did not acknowledge are not handled.
     *
     * @param router the router used to route the stanzas.
     * @param serverAddress this server's bare-domain address, used to stamp delay information on messages.
     */
    @Override
    public void onClose(PacketRouter router, JID serverAddress)
    {
        synchronized ( this ) {
            // OF-2425: Unacknowledged stanzas on connections that Openfire opened are not handled yet.
            final int unacknowledged = getUnacknowledgedEntries().size();
            if ( isEnabled() && unacknowledged > 0 ) {
                Log.debug( "Not handling {} unacknowledged stanza(s) of session {} that closed.", unacknowledged, session );
            }
            setNamespace( null );
        }
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
