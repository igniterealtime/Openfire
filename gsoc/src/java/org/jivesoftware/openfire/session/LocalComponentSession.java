/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.session;

import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.component.ExternalComponentManager;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.StreamError;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Represents a session between the server and a component.
 *
 * @author Gaston Dombiak
 */
public class LocalComponentSession extends LocalSession implements ComponentSession {

    private LocalExternalComponent component;
    /**
     * When using XEP-114 (the old spec) components will include in the TO attribute
     * of the intial stream header the domain they would like to have. The requested
     * domain is used only after the authentication was successful so we need keep track
     * of this information until the handshake is done.  
     */
    private String defaultSubdomain;

    /**
     * Returns a newly created session between the server and a component. The session will be
     * created and returned only if all the checkings were correct.<p>
     *
     * A domain will be binded for the new connecting component. This method is following
     * the JEP-114 where the domain to bind is sent in the TO attribute of the stream header.
     *
     * @param serverName the name of the server where the session is connecting to.
     * @param xpp     the parser that is reading the provided XML through the connection.
     * @param connection the connection with the component.
     * @return a newly created session between the server and a component.
     * @throws XmlPullParserException if there was an XML error while creating the session.
     */
    public static LocalComponentSession createSession(String serverName, XmlPullParser xpp, Connection connection)
            throws XmlPullParserException {
        String domain = xpp.getAttributeValue("", "to");
        Boolean allowMultiple = xpp.getAttributeValue("", "allowMultiple") != null;

        Log.debug("LocalComponentSession: [ExComp] Starting registration of new external component for domain: " +
                domain);

        // Default answer header in case of an error
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version='1.0' encoding='");
        sb.append(CHARSET);
        sb.append("'?>");
        sb.append("<stream:stream ");
        sb.append("xmlns:stream=\"http://etherx.jabber.org/streams\" ");
        sb.append("xmlns=\"jabber:component:accept\" from=\"");
        sb.append(domain);
        sb.append("\">");

        // Check that a domain was provided in the stream header
        if (domain == null) {
            Log.debug("LocalComponentSession: [ExComp] Domain not specified in stanza: " + xpp.getText());
            // Include the bad-format in the response
            StreamError error = new StreamError(StreamError.Condition.bad_format);
            sb.append(error.toXML());
            connection.deliverRawText(sb.toString());
            // Close the underlying connection
            connection.close();
            return null;
        }

        // Get the requested subdomain
        String subdomain = domain;
        int index = domain.indexOf(serverName);
        if (index > -1) {
            subdomain = domain.substring(0, index -1);
        }
        domain = subdomain + "." + serverName;
        JID componentJID = new JID(domain);
        // Check that an external component for the specified subdomain may connect to this server
        if (!ExternalComponentManager.canAccess(subdomain)) {
            Log.debug(
                    "LocalComponentSession: [ExComp] Component is not allowed to connect with subdomain: " + subdomain);
            StreamError error = new StreamError(StreamError.Condition.host_unknown);
            sb.append(error.toXML());
            connection.deliverRawText(sb.toString());
            // Close the underlying connection
            connection.close();
            return null;
        }
        // Check that a secret key was configured in the server
        String secretKey = ExternalComponentManager.getSecretForComponent(subdomain);
        if (secretKey == null) {
            Log.debug("LocalComponentSession: [ExComp] A shared secret for the component was not found.");
            // Include the internal-server-error in the response
            StreamError error = new StreamError(StreamError.Condition.internal_server_error);
            sb.append(error.toXML());
            connection.deliverRawText(sb.toString());
            // Close the underlying connection
            connection.close();
            return null;
        }
        // Check that the requested subdomain is not already in use
        if (!allowMultiple && InternalComponentManager.getInstance().hasComponent(componentJID)) {
            Log.debug("LocalComponentSession: [ExComp] Another component is already using domain: " + domain);
            // Domain already occupied so return a conflict error and close the connection
            // Include the conflict error in the response
            StreamError error = new StreamError(StreamError.Condition.conflict);
            sb.append(error.toXML());
            connection.deliverRawText(sb.toString());
            // Close the underlying connection
            connection.close();
            return null;
        }

        // Create a ComponentSession for the external component
        LocalComponentSession session = SessionManager.getInstance().createComponentSession(componentJID, connection);
        session.component = new LocalExternalComponent(session, connection);

        try {
            Log.debug("LocalComponentSession: [ExComp] Send stream header with ID: " + session.getStreamID() +
                    " for component with domain: " +
                    domain);
            // Build the start packet response
            sb = new StringBuilder();
            sb.append("<?xml version='1.0' encoding='");
            sb.append(CHARSET);
            sb.append("'?>");
            sb.append("<stream:stream ");
            sb.append("xmlns:stream=\"http://etherx.jabber.org/streams\" ");
            sb.append("xmlns=\"jabber:component:accept\" from=\"");
            sb.append(domain);
            sb.append("\" id=\"");
            sb.append(session.getStreamID().toString());
            sb.append("\">");
            connection.deliverRawText(sb.toString());

            // Return session although session has not been authentication yet. Until
            // it is authenticated traffic will be rejected except for authentication
            // requests
            session.defaultSubdomain = subdomain;
            return session;
        }
        catch (Exception e) {
            Log.error("An error occured while creating a ComponentSession", e);
            // Close the underlying connection
            connection.close();
            return null;
        }
    }

    public LocalComponentSession(String serverName, Connection conn, StreamID id) {
        super(serverName, conn, id);
    }

    public String getAvailableStreamFeatures() {
        // Nothing special to add
        return null;
    }

    boolean canProcess(Packet packet) {
        return true;
    }

    void deliver(Packet packet) throws PacketException {
        component.deliver(packet);
    }

    public ExternalComponent getExternalComponent() {
        return component;
    }

    /**
     * Authenticate the external component using a digest method. The digest includes the
     * stream ID and the secret key of the main domain of the external component. A component
     * needs to authenticate just once but it may bind several domains.
     *
     * @param digest the digest sent in the handshake.
     * @return true if the authentication was successful.
     */
    public boolean authenticate(String digest) {
        // Perform authentication. Wait for the handshake (with the secret key)
        String secretKey = ExternalComponentManager.getSecretForComponent(defaultSubdomain);
        String anticipatedDigest = AuthFactory.createDigest(getStreamID().getID(), secretKey);
        // Check that the provided handshake (secret key + sessionID) is correct
        if (!anticipatedDigest.equalsIgnoreCase(digest)) {
            Log.debug("LocalComponentSession: [ExComp] Incorrect handshake for component with domain: " +
                    defaultSubdomain);
            //  The credentials supplied by the initiator are not valid (answer an error
            // and close the connection)
            conn.deliverRawText(new StreamError(StreamError.Condition.not_authorized).toXML());
            // Close the underlying connection
            conn.close();
            return false;
        }
        else {
            // Component has authenticated fine
            setStatus(STATUS_AUTHENTICATED);
            // Send empty handshake element to acknowledge success
            conn.deliverRawText("<handshake></handshake>");
            // Bind the domain to this component
            ExternalComponent component = getExternalComponent();
            try {
                InternalComponentManager.getInstance().addComponent(defaultSubdomain, component);
                Log.debug(
                        "LocalComponentSession: [ExComp] External component was registered SUCCESSFULLY with domain: " +
                                defaultSubdomain);
                return true;
            }
            catch (ComponentException e) {
                Log.debug("LocalComponentSession: [ExComp] Another component is already using domain: " +
                        defaultSubdomain);
                //  The credentials supplied by the initiator are not valid (answer an error
                // and close the connection)
                conn.deliverRawText(new StreamError(StreamError.Condition.conflict).toXML());
                // Close the underlying connection
                conn.close();
                return false;
            }
        }
    }

    /**
     * The ExternalComponent acts as a proxy of the remote connected component. Any Packet that is
     * sent to this component will be delivered to the real component on the other side of the
     * connection.<p>
     *
     * An ExternalComponent will be added as a route in the RoutingTable for each connected
     * external component. This implies that when the server receives a packet whose domain matches
     * the external component services address then a route to the external component will be used
     * and the packet will be forwarded to the component on the other side of the connection.
     *
     * @author Gaston Dombiak
     */
    public static class LocalExternalComponent implements ComponentSession.ExternalComponent {
        private LocalComponentSession session;
        private Connection connection;
        private String name = "";
        private String type = "";
        private String category = "";
        /**
         * List of subdomains that were binded for this component. The list will include
         * the initial subdomain.
         */
        private List<String> subdomains = new ArrayList<String>();


        public LocalExternalComponent(LocalComponentSession session, Connection connection) {
            this.session = session;
            this.connection = connection;
        }

        public void processPacket(Packet packet) {
            // Ask the session to process the outgoing packet. This will
            // give us the chance to apply PacketInterceptors
            session.process(packet);
        }

        /**
         * Delivers the packet to the external component.
         *
         * @param packet the packet to deliver.
         */
        void deliver(Packet packet) {
            if (connection != null && !connection.isClosed()) {
                try {
                    connection.deliver(packet);
                }
                catch (Exception e) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                    connection.close();
                }
            }
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return category + " - " + type;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getInitialSubdomain() {
            if (subdomains.isEmpty()) {
                return null;
            }
            return subdomains.get(0);
        }

        private void addSubdomain(String subdomain) {
            subdomains.add(subdomain);
        }

        public Collection<String> getSubdomains() {
            return subdomains;
        }

        public void initialize(JID jid, ComponentManager componentManager) {
            addSubdomain(jid.toString());
        }

        public void start() {
        }

        public void shutdown() {
        }

        public String toString() {
            return super.toString() + " - subdomains: " + subdomains;
        }
    }
}
