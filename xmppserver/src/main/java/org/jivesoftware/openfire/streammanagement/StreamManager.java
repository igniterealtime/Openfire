/*
 * Copyright (C) 2017-2025 Ignite Realtime Foundation. All rights reserved.
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
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.dom.DOMElement;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.session.*;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.*;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * XEP-0198 Stream Manager.
 * Handles client/server messages acknowledgement.
 *
 * @author jonnyheavey
 */
public class StreamManager {

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

    private final Logger Log;
    private boolean resume = false;

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
     * Returns an XML element advertising XEP-0198 stream management as an inline feature for use
     * in SASL2 (XEP-0388) inline feature advertisement.
     *
     * @return an {@code <sm/>} element in the {@link #NAMESPACE_V3} namespace
     */
    public static Element featureElement() {
        return DocumentHelper.createElement(QName.get("sm", NAMESPACE_V3));
    }

    /**
     * Session (stream) to client.
     */
    private final LocalSession session;

    /**
     * Namespace to be used in stanzas sent to client (depending on XEP-0198 version used by client)
     */
    private String namespace;

    /**
     * Count of how many stanzas/packets
     * sent from the client that the server has processed
     */
    private AtomicLong serverProcessedStanzas = new AtomicLong( 0 );

    /**
     * Count of how many stanzas/packets
     * sent from the server that the client has processed
     */
    private AtomicLong clientProcessedStanzas = new AtomicLong( 0 );

    /**
     * The value (2^32)-1, used to emulate roll-over
     */
    private static final long MASK = new BigInteger( "2" ).pow( 32 ).longValue() - 1;

    /**
     * Collection of stanzas/packets sent to client that haven't been acknowledged.
     */
    private Deque<UnackedPacket> unacknowledgedServerStanzas = new LinkedList<>();

    /**
     * Delegates that can determine if a detached session can be terminated.
     */
    private final Set<TerminationDelegate> terminationDelegates = new HashSet<>();

    /**
     * Set to {@code true} when a SASL2-inline stream resumption has been processed but unacknowledged
     * stanzas have not yet been redelivered. The redelivery must happen after stream features are sent
     * following the SASL2 {@code <success/>}, so {@link StanzaHandler} calls
     * {@link #redeliverIfPendingSasl2(JID)} at that point.
     */
    private volatile boolean pendingSasl2Redelivery = false;

    public StreamManager(LocalSession session) {
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
        this.session = session;
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
     * Processes a stream management element.
     *
     * @param element The stream management element to be processed.
     */
    public void process( Element element )
    {
        switch(element.getName()) {
            case "enable":
                String resumeString = element.attributeValue("resume");
                boolean resume = false;
                if (resumeString != null) {
                    if (resumeString.equalsIgnoreCase("true") || resumeString.equalsIgnoreCase("yes") || resumeString.equals("1")) {
                        resume = true;
                    }
                }
                enable( element.getNamespace().getStringValue(), resume );
                break;
            case "resume":
                final String hValue = element.attributeValue("h");
                final long h;
                try {
                    h = Long.parseLong(hValue);
                } catch (NumberFormatException e) {
                    Log.warn( "Closing client session. Client sends non-numeric value for SM 'h': {}, affected session: {}", hValue, session );
                    final StreamError error = new StreamError( StreamError.Condition.undefined_condition, "You acknowledged stanzas using a 'h' value that is not a number (which is illegal). Your Ack h: " + hValue + ", our last unacknowledged stanza: " + (unacknowledgedServerStanzas.isEmpty() ? "(none)" : unacknowledgedServerStanzas.getLast().x) );
                    session.deliverRawText( error.toXML() );
                    session.close();
                    return;
                }
                if (h < 0) {
                    Log.warn( "Closing client session. Client sends negative value for SM 'h': {}, affected session: {}", h, session );
                    final StreamError error = new StreamError( StreamError.Condition.undefined_condition, "You acknowledged stanzas using a negative value (which is illegal). Your Ack h: " + h + ", our last unacknowledged stanza: " + (unacknowledgedServerStanzas.isEmpty() ? "(none)" : unacknowledgedServerStanzas.getLast().x) );
                    session.deliverRawText( error.toXML() );
                    session.close();
                    return;
                }
                String previd = element.attributeValue("previd");
                startResume( element.getNamespaceURI(), previd, h);

                break;
            case "r":
                sendServerAcknowledgement();
                break;
            case "a":
                processClientAcknowledgement(element);
                break;
            default:
                sendUnexpectedError();
        }
    }

    /**
     * Should this session be allowed to resume?
     * This is used while processed <enable/> and <resume/>
     *
     * @return True if the session is allowed to resume.
     */
    private boolean allowResume() {
        boolean allow = false;
        // Ensure that resource binding has occurred.
        if (session instanceof ClientSession) {
            AuthToken authToken = ((LocalClientSession)session).getAuthToken();
            if (authToken != null) {
                if (!authToken.isAnonymous()) {
                    allow = true;
                }
            }
        }
        return allow;
    }

    /**
     * Attempts to enable Stream Management for the entity identified by the provided JID.
     *
     * @param namespace The namespace that defines what version of SM is to be enabled.
     * @param resume Whether the client is requesting a resumable session.
     */
    private void enable( String namespace, boolean resume )
    {
        final Element enabled = enableInternal(namespace, resume);
        if (enabled != null) {
            session.deliverRawText(enabled.asXML());
        }
    }

    /**
     * Enables stream management and returns the {@code <enabled/>} element without sending it.
     * This allows callers (e.g. the SASL2 Bind2 handler) to embed the element in another stanza.
     *
     * <p>Returns {@code null} if enabling failed (an error stanza will have been sent already).</p>
     *
     * @param namespace the SM namespace to use
     * @param resume    whether the client requests a resumable session
     * @return the {@code <enabled/>} element, or {@code null} on failure
     */
    public Element enableAndBuildElement( String namespace, boolean resume )
    {
        return enableInternal(namespace, resume);
    }

    private Element enableInternal( String namespace, boolean resume )
    {
        boolean offerResume = allowResume();
        // Ensure that resource binding has occurred.
        if (!session.isAuthenticated()) {
            this.namespace = namespace;
            sendUnexpectedError();
            return null;
        }

        String smId = null;

        synchronized ( this )
        {
            // Do nothing if already enabled
            if ( isEnabled() )
            {
                sendUnexpectedError();
                return null;
            }
            this.namespace = namespace;

            this.resume = resume && offerResume;
            if ( this.resume ) {
                // Create SM-ID.
                smId = Base64.getEncoder().encodeToString((session.getAddress().getResource() + "\0" + session.getStreamID().getID()).getBytes(StandardCharsets.UTF_8));
            }
        }

        // Build confirmation element.
        Element enabled = new DOMElement(QName.get("enabled", namespace));
        if (this.resume) {
            enabled.addAttribute("resume", "true");
            enabled.addAttribute("id", smId);
            if ( !namespace.equals(NAMESPACE_V2) && LOCATION_ENABLED.getValue() ) {
                // OF-1925: Hint clients to do resumes at the same cluster node.
                enabled.addAttribute("location", XMPPServer.getInstance().getServerInfo().getHostname());
            }

            // OF-1926: Tell clients how long they can be detached.
            if ( MAX_SERVER_ENABLED.getValue() ) {
                final int sessionDetachTime = XMPPServer.getInstance().getSessionManager().getSessionDetachTime();
                if ( sessionDetachTime > 0 ) {
                    enabled.addAttribute("max", String.valueOf(sessionDetachTime/1000));
                }
            }
        }
        return enabled;
    }

    private void startResume(String namespace, String previd, long h) {
        Log.debug("Attempting resumption for {}, h={}", previd, h);
        this.namespace = namespace;
        final ResumeValidationResult validation = validateResumeRequest(namespace, previd, h);
        if (!validation.isValid()) {
            sendError(new PacketError(validation.getFailureCondition()));
            return;
        }

        final LocalClientSession otherSession = validation.getSession();
        final JID fullJid = validation.getFullJid();
        if (!otherSession.isDetached()) {
            Log.debug("Existing session {} of '{}' is not detached; detaching.", otherSession.getStreamID(), fullJid);
            Connection oldConnection = otherSession.getConnection();
            otherSession.setDetached();
            assert oldConnection != null; // If the other session is not detached, the connection can't be null.
            oldConnection.close(new StreamError(StreamError.Condition.conflict, "The stream previously served over this connection is resumed on a new connection."));
        }
        Log.debug("Attaching to other session '{}' of '{}'.", otherSession.getStreamID(), fullJid);
        // If we're all happy, re-attach the connection from the pre-existing session to the new session, discarding the old session.
        otherSession.reattach(session, h);
        Log.debug("Perform resumption of session {} for '{}', using connection from session {}", otherSession.getStreamID(), fullJid, session.getStreamID());
    }

    /**
     * Processes a SASL2-inline XEP-0198 {@code <resume/>} element. Unlike the standard
     * {@link #process(Element)} path, this method does <em>not</em> send the {@code <resumed/>}
     * element directly; instead it returns it so the caller can embed it in the SASL2
     * {@code <success/>} element.
     *
     * @param resumeElement the {@code <resume/>} element from the SASL2 {@code <authenticate/>}
     * @return the SM result to embed in {@code <success/>}, including the session that owns the
     *         connection when resumption succeeds
     */
    public Sasl2ResumeResult processSasl2Resume(Element resumeElement) {
        final String namespace = resumeElement.getNamespaceURI();

        final String hValue = resumeElement.attributeValue("h");
        final long h;
        try {
            h = Long.parseLong(hValue);
        } catch (NumberFormatException e) {
            Log.warn("Client sends non-numeric value for SM 'h' in SASL2 resume: {}, session: {}", hValue, session);
            return Sasl2ResumeResult.failed(namespace, PacketError.Condition.bad_request);
        }
        if (h < 0 || h > MASK) {
            Log.warn("Client sends out-of-range value for SM 'h' in SASL2 resume: {}, session: {}", h, session);
            return Sasl2ResumeResult.failed(namespace, PacketError.Condition.bad_request);
        }

        final String previd = resumeElement.attributeValue("previd");
        if (previd == null || previd.isEmpty()) {
            return Sasl2ResumeResult.failed(namespace, PacketError.Condition.bad_request);
        }

        final ResumeValidationResult validation = validateResumeRequest(namespace, previd, h);
        if (!validation.isValid()) {
            return Sasl2ResumeResult.failed(namespace, validation.getFailureCondition());
        }

        final LocalClientSession otherSession = validation.getSession();
        final JID fullJid = validation.getFullJid();
        if (!otherSession.isDetached()) {
            Log.debug("Existing session {} of '{}' is not detached (SASL2 resume); detaching.", otherSession.getStreamID(), fullJid);
            Connection oldConnection = otherSession.getConnection();
            otherSession.setDetached();
            assert oldConnection != null;
            oldConnection.close(new StreamError(StreamError.Condition.conflict, "The stream previously served over this connection is resumed on a new connection."));
        }
        Log.debug("Attaching (SASL2) to other session '{}' of '{}'.", otherSession.getStreamID(), fullJid);
        final Element resumed = otherSession.reattachForSasl2(session, h);
        Log.debug("Perform SASL2 resumption of session {} for '{}', using connection from session {}", otherSession.getStreamID(), fullJid, session.getStreamID());
        return Sasl2ResumeResult.resumed(resumed, otherSession);
    }

    private ResumeValidationResult validateResumeRequest(String namespace, String previd, long h) {
        if (!allowResume() || session.isAuthenticated()) {
            Log.debug("Unable to process session resumption attempt, as session {} is in a state where resumption is not allowed.", session);
            return ResumeValidationResult.failed(PacketError.Condition.unexpected_request);
        }

        final AuthToken authToken = session instanceof LocalClientSession ? ((LocalClientSession) session).getAuthToken() : null;
        if (authToken == null) {
            Log.debug("Unable to process session resumption attempt, as session {} does not provide any auth context.", session);
            return ResumeValidationResult.failed(PacketError.Condition.unexpected_request);
        }

        final String resource;
        final String streamId;
        try {
            final StringTokenizer tokens = new StringTokenizer(new String(Base64.getDecoder().decode(previd), StandardCharsets.UTF_8), "\0");
            resource = tokens.nextToken();
            streamId = tokens.nextToken();
        } catch (Exception e) {
            Log.debug("Unable to decode SM previd for session {}.", session, e);
            return ResumeValidationResult.failed(PacketError.Condition.bad_request);
        }

        final JID fullJid = authToken.isAnonymous()
            ? new JID(resource, session.getServerName(), resource, true)
            : new JID(authToken.getUsername(), session.getServerName(), resource, true);
        final ClientSession route = XMPPServer.getInstance().getRoutingTable().getClientRoute(fullJid);
        if (!(route instanceof LocalClientSession)) {
            Log.debug("Unable to resume '{}' on this cluster node because no local session was found.", fullJid);
            if (LOCATION_TERMINATE_OTHERS_ENABLED.getValue()) {
                CacheFactory.doClusterTask(new ClientSessionTask(fullJid, RemoteSessionTask.Operation.removeDetached));
            }
            return ResumeValidationResult.failed(PacketError.Condition.item_not_found);
        }

        final LocalClientSession resumableSession = (LocalClientSession) route;
        if (!resumableSession.getStreamID().getID().equals(streamId)) {
            return ResumeValidationResult.failed(PacketError.Condition.item_not_found);
        }
        if (resumableSession.isClosed()
            || !resumableSession.getStreamManager().resume
            || resumableSession.getStreamManager().namespace == null
            || !resumableSession.getStreamManager().namespace.equals(namespace)) {
            return ResumeValidationResult.failed(PacketError.Condition.unexpected_request);
        }
        if (!resumableSession.getStreamManager().validateClientAcknowledgement(h)) {
            return ResumeValidationResult.failed(PacketError.Condition.undefined_condition);
        }
        return ResumeValidationResult.valid(resumableSession, fullJid);
    }

    private static final class ResumeValidationResult {
        private final LocalClientSession session;
        private final JID fullJid;
        private final PacketError.Condition failureCondition;

        private ResumeValidationResult(LocalClientSession session, JID fullJid, PacketError.Condition failureCondition) {
            this.session = session;
            this.fullJid = fullJid;
            this.failureCondition = failureCondition;
        }

        static ResumeValidationResult valid(LocalClientSession session, JID fullJid) {
            return new ResumeValidationResult(session, fullJid, null);
        }

        static ResumeValidationResult failed(PacketError.Condition condition) {
            return new ResumeValidationResult(null, null, condition);
        }

        boolean isValid() {
            return session != null;
        }

        LocalClientSession getSession() {
            return session;
        }

        JID getFullJid() {
            return fullJid;
        }

        PacketError.Condition getFailureCondition() {
            return failureCondition;
        }
    }

    public static final class Sasl2ResumeResult {
        private final Element response;
        private final LocalClientSession resumedSession;

        private Sasl2ResumeResult(Element response, LocalClientSession resumedSession) {
            this.response = response;
            this.resumedSession = resumedSession;
        }

        public static Sasl2ResumeResult resumed(Element response, LocalClientSession resumedSession) {
            return new Sasl2ResumeResult(response, resumedSession);
        }

        public static Sasl2ResumeResult failed(String namespace, PacketError.Condition condition) {
            final Element failed = DocumentHelper.createElement(QName.get("failed", namespace));
            failed.addElement(QName.get(condition.toXMPP(), "urn:ietf:params:xml:ns:xmpp-stanzas"));
            return new Sasl2ResumeResult(failed, null);
        }

        public Element getResponse() {
            return response;
        }

        public LocalClientSession getResumedSession() {
            return resumedSession;
        }

        public boolean isResumed() {
            return resumedSession != null;
        }
    }

    /**
     * Called when a session receives a closing stream tag, this prevents the
     * session from being detached.
     */
    public void formalClose() {
        this.resume = false;
    }

    /**
     * Sends XEP-0198 acknowledgement &lt;a /&gt; to client from server
     */
    public void sendServerAcknowledgement() {
        if(isEnabled()) {
            if (session.isDetached()) {
                Log.debug("Session is detached, won't request an ack.");
                return;
            }
            String ack = String.format("<a xmlns='%s' h='%s' />", namespace, serverProcessedStanzas.get() & MASK );
            session.deliverRawText( ack );
        }
    }

    /**
     * Sends XEP-0198 request <r /> to client from server
     */
    private void sendServerRequest() {
        if(isEnabled()) {
            if (session.isDetached()) {
                Log.debug("Session is detached, won't request an ack.");
                return;
            }
            String request = String.format("<r xmlns='%s' />", namespace);
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
    private void sendError(PacketError error) {
        final Element failed = DocumentHelper.createElement(QName.get("failed", namespace));
        failed.addElement(QName.get(error.getCondition().toXMPP(), "urn:ietf:params:xml:ns:xmpp-stanzas"));
        session.deliverRawText(failed.asXML());
        this.namespace = null; // isEnabled() is testing this.
    }

    /**
     * Checks if the amount of stanzas that the client acknowledges is equal to or less than the amount of stanzas that
     * we've sent to the client.
     *
     * @param h Then number of stanzas that the client acknowledges it has received from us.
     * @return false if we sent fewer stanzas to the client than the number it is acknowledging.
     */
    private synchronized boolean validateClientAcknowledgement(long h) {
        if (h < 0) {
            throw new IllegalArgumentException("Argument 'h' cannot be negative, but was: " + h);
        }
        if (h > MASK) {
            throw new IllegalArgumentException("Argument 'h' cannot be larger than 2^32 -1, but was: " + h);
        }
        final long oldH = clientProcessedStanzas.get();
        final Long lastUnackedX = unacknowledgedServerStanzas.isEmpty() ? null : unacknowledgedServerStanzas.getLast().x;
        return validateClientAcknowledgement(h, oldH, lastUnackedX);
    }

    @VisibleForTesting
    static boolean validateClientAcknowledgement(final long h, final long oldH, final Long lastUnackedX) {
        if (lastUnackedX == null) {
            // No unacked stanzas.
            return h == oldH;
        }

        return h <= lastUnackedX;
    }

    /**
     * Process client acknowledgements for a given value of h.
     *
     * @param h Last handled stanza to be acknowledged.
     */
    private void processClientAcknowledgement(long h) {
        synchronized (this) {

            if ( !validateClientAcknowledgement(h) ) {
                // All paths leading up to here should have checked for this. Race condition?
                throw new IllegalStateException( "Client acknowledges stanzas that we didn't send! Client Ack h: "+h+", our last unacknowledged stanza: " + (unacknowledgedServerStanzas.isEmpty() ? "(none)" : unacknowledgedServerStanzas.getLast().x) );
            }

            clientProcessedStanzas.set( h );

            // Remove stanzas from temporary storage as now acknowledged
            Log.trace( "Before processing client Ack (h={}): {} unacknowledged stanzas.", h, unacknowledgedServerStanzas.size() );

            // Pop all acknowledged stanzas.
            while( !unacknowledgedServerStanzas.isEmpty() && unacknowledgedServerStanzas.getFirst().x <= h )
            {
                unacknowledgedServerStanzas.removeFirst();
            }

            // Ensure that unacknowledged stanzas are purged after the client rolled over 'h' which occurs at h= (2^32)-1
            final int maxUnacked = getMaximumUnacknowledgedStanzas();
            final boolean clientHadRollOver = h < maxUnacked && !unacknowledgedServerStanzas.isEmpty() && unacknowledgedServerStanzas.getLast().x > MASK - maxUnacked;
            if ( clientHadRollOver )
            {
                Log.info( "Client rolled over 'h'. Purging high-numbered unacknowledged stanzas." );
                while ( !unacknowledgedServerStanzas.isEmpty() && unacknowledgedServerStanzas.getLast().x > MASK - maxUnacked)
                {
                    unacknowledgedServerStanzas.removeLast();
                }
            }

            Log.trace( "After processing client Ack (h={}): {} unacknowledged stanzas.", h, unacknowledgedServerStanzas.size());
        }
    }

    /**
     * Receive and process acknowledgement packet from client
     * @param ack XEP-0198 acknowledgement <a /> stanza to process
     */
    private void processClientAcknowledgement(Element ack) {
        if(isEnabled()) {
            if (ack.attribute("h") != null) {
                final String hValue = ack.attributeValue("h");
                final long h;
                try {
                    h = Long.parseLong(hValue);
                } catch (NumberFormatException e) {
                    Log.warn( "Closing client session. Client sends non-numeric value for SM 'h': {}, affected session: {}", hValue, session );
                    final StreamError error = new StreamError( StreamError.Condition.undefined_condition, "You acknowledged stanzas using a 'h' value that is not a number (which is illegal). Your Ack h: " + hValue + ", our last unacknowledged stanza: " + (unacknowledgedServerStanzas.isEmpty() ? "(none)" : unacknowledgedServerStanzas.getLast().x) );
                    session.deliverRawText( error.toXML() );
                    session.close();
                    return;
                }
                if (h < 0) {
                    Log.warn( "Closing client session. Client sends negative value for SM 'h': {}, affected session: {}", h, session );
                    final StreamError error = new StreamError( StreamError.Condition.undefined_condition, "You acknowledged stanzas using a negative value (which is illegal). Your Ack h: " + h + ", our last unacknowledged stanza: " + (unacknowledgedServerStanzas.isEmpty() ? "(none)" : unacknowledgedServerStanzas.getLast().x) );
                    session.deliverRawText( error.toXML() );
                    session.close();
                    return;
                }

                Log.debug( "Received acknowledgement from client: h={}", h );

                synchronized ( this ) {
                    if (!validateClientAcknowledgement(h)) {
                        Log.warn( "Closing client session. Client acknowledges stanzas that we didn't send! Client Ack h: {}, our last unacknowledged stanza: {}, affected session: {}", h, unacknowledgedServerStanzas.isEmpty() ? "(none)" : unacknowledgedServerStanzas.getLast().x, session );
                        final StreamError error = new StreamError( StreamError.Condition.undefined_condition, "You acknowledged stanzas that we didn't send. Your Ack h: " + h + ", our last unacknowledged stanza: " + (unacknowledgedServerStanzas.isEmpty() ? "(none)" : unacknowledgedServerStanzas.getLast().x) );
                        session.deliverRawText( error.toXML() );
                        session.close();
                        return;
                    }

                    processClientAcknowledgement(h);
                }
            }
        }
    }

    /**
     * Registers that Openfire sends a stanza to the client (which is expected to be acknowledged later).
     * @param packet The stanza that is sent.
     */
    public void sentStanza(Packet packet) {

        if(isEnabled()) {
            final long requestFrequency = JiveGlobals.getLongProperty( "stream.management.requestFrequency", 5 );
            final int size;

            synchronized (this)
            {
                // The next ID is one higher than the last stanza that was sent (which might be unacknowledged!)
                final long x = 1 + ( unacknowledgedServerStanzas.isEmpty() ? clientProcessedStanzas.get() : unacknowledgedServerStanzas.getLast().x );
                unacknowledgedServerStanzas.addLast( new StreamManager.UnackedPacket( x, packet.createCopy() ) );

                size = unacknowledgedServerStanzas.size();

                Log.trace( "Added stanza of type '{}' to collection of unacknowledged stanzas (x={}). Collection size is now {}.", packet.getElement().getName(), x, size );

                // Prevent keeping to many stanzas in memory.
                if ( size > getMaximumUnacknowledgedStanzas() )
                {
                    Log.warn( "To many stanzas go unacknowledged for this connection. Clearing queue and disabling functionality." );
                    namespace = null;
                    unacknowledgedServerStanzas.clear();
                    return;
                }
            }

            // When we have a sizable amount of unacknowledged stanzas, request acknowledgement.
            if ( size % requestFrequency == 0 ) {
                Log.debug( "Requesting acknowledgement from peer, as we have {} or more unacknowledged stanzas.", requestFrequency );
                sendServerRequest();
            }
        }

    }

    public void onClose(PacketRouter router, JID serverAddress) {
        // Re-deliver unacknowledged stanzas from broken stream (XEP-0198)
        synchronized (this) {
            if(isEnabled()) {
                namespace = null; // disable stream management.
                for (StreamManager.UnackedPacket unacked : unacknowledgedServerStanzas) {
                    if (unacked.packet instanceof Message) {
                        Message m = (Message) unacked.packet;
                        if (m.getExtension("delay", "urn:xmpp:delay") == null) {
                            Element delayInformation = m.addChildElement("delay", "urn:xmpp:delay");
                            delayInformation.addAttribute("stamp", XMPPDateTimeFormat.format(unacked.timestamp));
                            delayInformation.addAttribute("from", serverAddress.toBareJID());
                        }
                        router.route(unacked.packet);
                    }
                }
            }
        }

    }

    /**
     * Builds the XEP-0198 {@code <resumed/>} element for this session without sending it.
     * This is used when the element needs to be embedded in another stanza (e.g. a SASL2
     * {@code <success/>} element) rather than sent standalone.
     *
     * @return the {@code <resumed/>} element
     */
    public Element buildResumedElement() {
        Element resumed = new DOMElement(QName.get("resumed", namespace));
        resumed.addAttribute("previd", Base64.getEncoder().encodeToString((session.getAddress().getResource() + "\0" + session.getStreamID().getID()).getBytes(StandardCharsets.UTF_8)));
        resumed.addAttribute("h", Long.toString(serverProcessedStanzas.get()));
        return resumed;
    }

    public void onResume(JID serverAddress, long h) {
        Log.debug("Agreeing to resume");
        final Element resumed = buildResumedElement();
        final Connection connection = session.getConnection();
        assert connection != null; // While the client is resuming a session, the connection on which the session is resumed can't be null.
        connection.deliverRawText(resumed.asXML());
        Log.debug("Resuming session: Ack for {}", h);
        processClientAcknowledgement(h);
        redeliverUnackedStanzas(serverAddress);
    }

    /**
     * Processes the client's acknowledgement counter as part of a SASL2-based stream resumption.
     * This is a package-accessible wrapper around the private {@link #processClientAcknowledgement(long)}
     * for use by {@link org.jivesoftware.openfire.session.LocalSession#reattachForSasl2}.
     *
     * @param h the client's acknowledgement counter
     */
    public void processClientAcknowledgementPublic(long h) {
        processClientAcknowledgement(h);
    }

    /**
     * Sets the pending SASL2 redelivery flag. When {@code true}, {@link #redeliverIfPendingSasl2(JID)}
     * will redeliver unacknowledged stanzas. Called by
     * {@link org.jivesoftware.openfire.session.LocalSession#reattachForSasl2} to defer redelivery
     * until after stream features have been sent.
     *
     * @param pending whether redelivery is pending
     */
    public void setPendingSasl2Redelivery(boolean pending) {
        this.pendingSasl2Redelivery = pending;
    }

    /**
     * If a SASL2-inline stream resumption is pending redelivery, redelivers unacknowledged stanzas
     * now. This must be called by {@link org.jivesoftware.openfire.net.StanzaHandler} after stream
     * features have been sent following the SASL2 {@code <success/>}.
     *
     * @param serverAddress the server's JID, used to stamp delayed stanzas
     */
    public void redeliverIfPendingSasl2(JID serverAddress) {
        if (pendingSasl2Redelivery) {
            pendingSasl2Redelivery = false;
            redeliverUnackedStanzas(serverAddress);
        }
    }

    /**
     * Re-delivers unacknowledged stanzas after a stream resumption and sends a server request for
     * acknowledgement. Called by both the standard and SASL2 resume paths.
     *
     * @param serverAddress the server's JID, used to stamp delayed stanzas
     */
    public void redeliverUnackedStanzas(JID serverAddress) {
        Log.debug("Processing remaining unacked stanzas");
        final Connection connection = session.getConnection();
        assert connection != null;
        // Re-deliver unacknowledged stanzas from broken stream (XEP-0198)
        synchronized (this) {
            if(isEnabled()) {
                for (StreamManager.UnackedPacket unacked : unacknowledgedServerStanzas) {
                    try {
                        if (unacked.packet instanceof Message) {
                            Message m = (Message) unacked.packet;
                            if (m.getExtension("delay", "urn:xmpp:delay") == null) {
                                Element delayInformation = m.addChildElement("delay", "urn:xmpp:delay");
                                delayInformation.addAttribute("stamp", XMPPDateTimeFormat.format(unacked.timestamp));
                                delayInformation.addAttribute("from", serverAddress.toBareJID());
                            }
                            connection.deliver(m);
                        } else if (unacked.packet instanceof Presence) {
                            Presence p = (Presence) unacked.packet;
                            if (p.getExtension("delay", "urn:xmpp:delay") == null) {
                                Element delayInformation = p.addChildElement("delay", "urn:xmpp:delay");
                                delayInformation.addAttribute("stamp", XMPPDateTimeFormat.format(unacked.timestamp));
                                delayInformation.addAttribute("from", serverAddress.toBareJID());
                            }
                            connection.deliver(p);
                        } else {
                            connection.deliver(unacked.packet);
                        }
                    } catch (UnauthorizedException e) {
                        Log.warn("Caught unauthorized exception, which seems worrying: ", e);
                    }
                }

                sendServerRequest();
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
     * Increments the count of stanzas processed by the server since
     * Stream Management was enabled.
     */
    public void incrementServerProcessedStanzas() {
        if(isEnabled()) {
            this.serverProcessedStanzas.incrementAndGet();
        }
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
}
