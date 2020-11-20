package org.jivesoftware.openfire.streammanagement;

import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.dom.DOMElement;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.*;

import java.math.BigInteger;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedList;
import java.util.StringTokenizer;
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

    public StreamManager(LocalSession session) {
        String address;
        try {
            address = session.getConnection().getHostAddress();
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
                long h = new Long(element.attributeValue("h"));
                String previd = element.attributeValue("previd");
                startResume( element.getNamespaceURI(), previd, h);
                break;
            case "r":
                sendServerAcknowledgement();
                break;
            case "a":
                processClientAcknowledgement( element);
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
            Object ws = session.getSessionData("ws");
            if (ws != null && (Boolean) ws) {
                Log.debug( "Websockets resume is not yet implemented: {}", session );
                return false;
            }
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


        boolean offerResume = allowResume();
        // Ensure that resource binding has occurred.
        if (session.getStatus() != Session.STATUS_AUTHENTICATED) {
            this.namespace = namespace;
            sendUnexpectedError();
            return;
        }

        String smId = null;

        synchronized ( this )
        {
            // Do nothing if already enabled
            if ( isEnabled() )
            {
                sendUnexpectedError();
                return;
            }
            this.namespace = namespace;

            this.resume = resume && offerResume;
            if ( this.resume ) {
                // Create SM-ID.
                smId = StringUtils.encodeBase64( session.getAddress().getResource() + "\0" + session.getStreamID().getID());
            }
        }

        // Send confirmation to the requestee.
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
        session.deliverRawText(enabled.asXML());
    }

    private void startResume(String namespace, String previd, long h) {
        Log.debug("Attempting resumption for {}, h={}", previd, h);
        this.namespace = namespace;
        // Ensure that resource binding has NOT occurred.
        if (!allowResume() ) {
            Log.debug("Unable to process session resumption attempt, as session {} is in a state where session resumption is not allowed.", session);
            sendUnexpectedError();
            return;
        }
        if (session.getStatus() == Session.STATUS_AUTHENTICATED) {
            Log.debug("Unable to process session resumption attempt, as session {} is not authenticated.", session);
            sendUnexpectedError();
            return;
        }
        AuthToken authToken = null;
        // Ensure that resource binding has occurred.
        if (session instanceof ClientSession) {
            authToken = ((LocalClientSession) session).getAuthToken();
        }
        if (authToken == null) {
            Log.debug("Unable to process session resumption attempt, as session {} does not provide any auth context.", session);
            sendUnexpectedError();
            return;
        }
        // Decode previd.
        String resource;
        String streamId;
        try {
            StringTokenizer toks = new StringTokenizer(new String(StringUtils.decodeBase64(previd), StandardCharsets.UTF_8), "\0");
            resource = toks.nextToken();
            streamId = toks.nextToken();
        } catch (Exception e) {
            Log.debug("Exception from previd decode:", e);
            sendUnexpectedError();
            return;
        }
        final JID fullJid;
        if ( authToken.isAnonymous() ){
            fullJid = new JID(resource, session.getServerName(), resource, true);
        } else {
            fullJid = new JID(authToken.getUsername(), session.getServerName(), resource, true);
        }
        Log.debug("Resuming session for '{}'. Current session: {}", fullJid, session.getStreamID());

        // Locate existing session.
        final ClientSession route = XMPPServer.getInstance().getRoutingTable().getClientRoute(fullJid);
        if (route == null) {
            sendError(new PacketError(PacketError.Condition.item_not_found));
            return;
        }

        if (!(route instanceof LocalClientSession)) {
            Log.debug("Not allowing a client of '{}' to resume a session on this cluster node. The session can only be resumed on the Openfire cluster node where the original session was connected.", fullJid);
            sendError(new PacketError(PacketError.Condition.unexpected_request));
            return;
        }

        final LocalClientSession otherSession = (LocalClientSession) route;
        if (!otherSession.getStreamID().getID().equals(streamId)) {
            sendError(new PacketError(PacketError.Condition.item_not_found));
            return;
        }
        Log.debug("Found existing session for '{}', checking status", fullJid);
        // Previd identifies proper session. Now check SM status
        if (!otherSession.getStreamManager().resume) {
            Log.debug("Not allowing a client of '{}' to resume a session, the session to be resumed does not have the stream management resumption feature enabled.", fullJid);
            sendError(new PacketError(PacketError.Condition.unexpected_request));
            return;
        }
        if (otherSession.getStreamManager().namespace == null) {
            Log.debug("Not allowing a client of '{}' to resume a session, the session to be resumed disabled SM functionality as a response to an earlier error.", fullJid);
            sendError(new PacketError(PacketError.Condition.unexpected_request));
            return;
        }
        if (!otherSession.getStreamManager().namespace.equals(namespace)) {
            Log.debug("Not allowing a client of '{}' to resume a session, the session to be resumed used a different version ({}) of the session management resumption feature as compared to the version that's requested now: {}.", fullJid, otherSession.getStreamManager().namespace, namespace);
            sendError(new PacketError(PacketError.Condition.unexpected_request));
            return;
        }
        if (!otherSession.getStreamManager().validateClientAcknowledgement(h)) {
            Log.debug("Not allowing a client of '{}' to resume a session, as it reports it received more stanzas from us than that we've send it.", fullJid);
            sendError(new PacketError(PacketError.Condition.unexpected_request));
            return;
        }
        if (!otherSession.isDetached()) {
            Log.debug("Existing session {} of '{}' is not detached; detaching.", otherSession.getStreamID(), fullJid);
            Connection oldConnection = otherSession.getConnection();
            otherSession.setDetached();
            oldConnection.close();
        }
        Log.debug("Attaching to other session '{}' of '{}'.", otherSession.getStreamID(), fullJid);
        // If we're all happy, re-attach the connection from the pre-existing session to the new session, discarding the old session.
        otherSession.reattach(session, h);
        Log.debug("Perform resumption of session {} for '{}', using connection from session {}", otherSession.getStreamID(), fullJid, session.getStreamID());
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
        session.deliverRawText(
            String.format("<failed xmlns='%s'>", namespace)
                + String.format("<%s xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>", error.getCondition().toXMPP())
                + "</failed>"
        );
        this.namespace = null; // isEnabled() is testing this.
    }

    /**
     * Checks if the amount of stanzas that the client acknowledges is equal to or less than the amount of stanzas that
     * we've sent to the client.
     *
     * @param h Then number of stanzas that the client acknowledges it has received from us.
     * @return false if we sent less stanzas to the client than the number it is acknowledging.
     */
    private synchronized boolean validateClientAcknowledgement(long h) {
        return h <= ( unacknowledgedServerStanzas.isEmpty() ? clientProcessedStanzas.get() : unacknowledgedServerStanzas.getLast().x );
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
                throw new IllegalStateException( "Client acknowledges stanzas that we didn't send! Client Ack h: "+h+", our last stanza: " + unacknowledgedServerStanzas.getLast().x );
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
                final long h = Long.valueOf(ack.attributeValue("h"));

                Log.debug( "Received acknowledgement from client: h={}", h );

                synchronized ( this ) {
                    if (!validateClientAcknowledgement(h)) {
                        Log.warn( "Closing client session. Client acknowledges stanzas that we didn't send! Client Ack h: {}, our last stanza: {}, affected session: {}", h, unacknowledgedServerStanzas.getLast().x, session );
                        final StreamError error = new StreamError( StreamError.Condition.undefined_condition, "You acknowledged stanzas that we didn't send. Your Ack h: " + h + ", our last stanza: " + unacknowledgedServerStanzas.getLast().x );
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

    public void onResume(JID serverAddress, long h) {
        Log.debug("Agreeing to resume");
        Element resumed = new DOMElement(QName.get("resumed", namespace));
        resumed.addAttribute("previd", StringUtils.encodeBase64( session.getAddress().getResource() + "\0" + session.getStreamID().getID()));
        resumed.addAttribute("h", Long.toString(serverProcessedStanzas.get()));
        session.getConnection().deliverRawText(resumed.asXML());
        Log.debug("Resuming session: Ack for {}", h);
        processClientAcknowledgement(h);
        Log.debug("Processing remaining unacked stanzas");
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
                            session.getConnection().deliver(m);
                        } else if (unacked.packet instanceof Presence) {
                            Presence p = (Presence) unacked.packet;
                            if (p.getExtension("delay", "urn:xmpp:delay") == null) {
                                Element delayInformation = p.addChildElement("delay", "urn:xmpp:delay");
                                delayInformation.addAttribute("stamp", XMPPDateTimeFormat.format(unacked.timestamp));
                                delayInformation.addAttribute("from", serverAddress.toBareJID());
                            }
                            session.getConnection().deliver(p);
                        } else {
                            session.getConnection().deliver(unacked.packet);
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
}
