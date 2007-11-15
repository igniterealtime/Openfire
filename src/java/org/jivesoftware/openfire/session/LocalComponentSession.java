/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.openfire.session;

import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.component.ExternalComponentManager;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.net.SocketConnection;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.component.ComponentManager;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.StreamError;

import java.io.IOException;
import java.io.Writer;
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
     * Returns a newly created session between the server and a component. The session will be
     * created and returned only if all the checkings were correct.<p>
     *
     * A domain will be binded for the new connecting component. This method is following
     * the JEP-114 where the domain to bind is sent in the TO attribute of the stream header.
     *
     * @param serverName the name of the server where the session is connecting to.
     * @param reader     the reader that is reading the provided XML through the connection.
     * @param connection the connection with the component.
     * @return a newly created session between the server and a component.
     */
    public static LocalComponentSession createSession(String serverName, XMPPPacketReader reader,
            SocketConnection connection) throws UnauthorizedException, IOException,
            XmlPullParserException
    {
        XmlPullParser xpp = reader.getXPPParser();
        String domain = xpp.getAttributeValue("", "to");

        Log.debug("LocalComponentSession: [ExComp] Starting registration of new external component for domain: " + domain);

        Writer writer = connection.getWriter();
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
            writer.write(sb.toString());
            writer.flush();
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
            Log.debug("LocalComponentSession: [ExComp] Component is not allowed to connect with subdomain: " + subdomain);
            StreamError error = new StreamError(StreamError.Condition.host_unknown);
            sb.append(error.toXML());
            writer.write(sb.toString());
            writer.flush();
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
            writer.write(sb.toString());
            writer.flush();
            // Close the underlying connection
            connection.close();
            return null;
        }
        // Check that the requested subdomain is not already in use
        if (InternalComponentManager.getInstance().hasComponent(componentJID)) {
            Log.debug("LocalComponentSession: [ExComp] Another component is already using domain: " + domain);
            // Domain already occupied so return a conflict error and close the connection
            // Include the conflict error in the response
            StreamError error = new StreamError(StreamError.Condition.conflict);
            sb.append(error.toXML());
            writer.write(sb.toString());
            writer.flush();
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
            writer.write(sb.toString());
            writer.flush();

            // Perform authentication. Wait for the handshake (with the secret key)
            Element doc = reader.parseDocument().getRootElement();
            String digest = "handshake".equals(doc.getName()) ? doc.getStringValue() : "";
            String anticipatedDigest = AuthFactory.createDigest(session.getStreamID().getID(), secretKey);
            // Check that the provided handshake (secret key + sessionID) is correct
            if (!anticipatedDigest.equalsIgnoreCase(digest)) {
                Log.debug("LocalComponentSession: [ExComp] Incorrect handshake for component with domain: " + domain);
                //  The credentials supplied by the initiator are not valid (answer an error
                // and close the connection)
                writer.write(new StreamError(StreamError.Condition.not_authorized).toXML());
                writer.flush();
                // Close the underlying connection
                connection.close();
                return null;
            }
            else {
                // Component has authenticated fine
                session.setStatus(STATUS_AUTHENTICATED);
                // Send empty handshake element to acknowledge success
                writer.write("<handshake></handshake>");
                writer.flush();
                // Bind the domain to this component
                ExternalComponent component = session.getExternalComponent();
                InternalComponentManager.getInstance().addComponent(subdomain, component);
                Log.debug("LocalComponentSession: [ExComp] External component was registered SUCCESSFULLY with domain: " + domain);
                return session;
            }
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
