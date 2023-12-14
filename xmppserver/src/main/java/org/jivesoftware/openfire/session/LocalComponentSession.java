/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.session;

import org.dom4j.*;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.component.ExternalComponentManager;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.StreamError;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Represents a session between the server and a component.
 *
 * @author Gaston Dombiak
 */
// TODO implement TLS and observe org.jivesoftware.openfire.session.ConnectionSettings.Component.TLS_POLICY
public class LocalComponentSession extends LocalSession implements ComponentSession {

    private static final Logger Log = LoggerFactory.getLogger(LocalComponentSession.class);

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
        boolean allowMultiple = xpp.getAttributeValue("", "allowMultiple") != null;

        Log.debug("LocalComponentSession: [ExComp] Starting registration of new external component for domain: {}", domain);

        // Retrieve list of namespaces declared in current element (OF-2556)
        connection.setAdditionalNamespaces(XMPPPacketReader.getPrefixedNamespacesOnCurrentElement(xpp));

        final Element stream = DocumentHelper.createElement(QName.get("stream", "stream", "http://etherx.jabber.org/streams"));
        final Document document = DocumentHelper.createDocument(stream);
        document.setXMLEncoding(StandardCharsets.UTF_8.toString());
        stream.add(Namespace.get("jabber:component:accept"));

        // Check that a domain was provided in the stream header
        if (domain == null) {
            Log.debug("LocalComponentSession: [ExComp] Domain not specified in stanza: {}", xpp.getText());
            // Include the bad-format in the response and close the underlying connection.
            connection.deliverRawText(StringUtils.asUnclosedStream(document));
            connection.close(new StreamError(StreamError.Condition.bad_format, "Domain not specified in 'to' attribute."));
            return null;
        }
        stream.addAttribute("from", domain);

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
            Log.debug("LocalComponentSession: [ExComp] Component is not allowed to connect with subdomain: {}", subdomain);
            // Close the underlying connection
            connection.deliverRawText(StringUtils.asUnclosedStream(document));
            connection.close(new StreamError(StreamError.Condition.host_unknown, "Component is not allowed to connect with the requested subdomain."));
            return null;
        }
        // Check that a secret key was configured in the server
        String secretKey = ExternalComponentManager.getSecretForComponent(subdomain);
        if (secretKey == null) {
            Log.debug("LocalComponentSession: [ExComp] A shared secret for the component was not found.");
            // Include the internal-server-error in the response and close the underlying connection.
            connection.deliverRawText(StringUtils.asUnclosedStream(document));
            connection.close(new StreamError(StreamError.Condition.internal_server_error));
            return null;
        }
        // Check that the requested subdomain is not already in use
        if (!allowMultiple && InternalComponentManager.getInstance().hasComponent(componentJID)) {
            Log.debug("LocalComponentSession: [ExComp] Another component is already using domain: {}", domain);
            // Domain already occupied so return a conflict error and close the connection
            connection.deliverRawText(StringUtils.asUnclosedStream(document));
            connection.close(new StreamError(StreamError.Condition.conflict, "The requested domain is already being used by another component."));
            return null;
        }

        // Create a ComponentSession for the external component
        LocalComponentSession session = SessionManager.getInstance().createComponentSession(componentJID, connection);
        connection.registerCloseListener( handback -> SessionManager.getInstance().removeComponentSession( (LocalComponentSession) handback ), session );

        session.component = new LocalExternalComponent(session, connection);

        try {
            Log.debug("LocalComponentSession: [ExComp] Send stream header with ID: {} for component with domain: {}", session.getStreamID(), domain);
            stream.addAttribute("id", session.getStreamID().toString());

            connection.deliverRawText(StringUtils.asUnclosedStream(document));

            // Return session although session has not been authentication yet. Until it is authenticated traffic will
            // be rejected except for authentication requests
            session.defaultSubdomain = subdomain;
            return session;
        }
        catch (Exception e) {
            Log.error("An error occurred while creating a ComponentSession", e);
            // Close the underlying connection
            connection.close(new StreamError(StreamError.Condition.internal_server_error));
            return null;
        }
    }

    public LocalComponentSession(String serverName, Connection conn, StreamID id) {
        super(serverName, conn, id, Locale.getDefault());
    }

    @Override
    public List<Element> getAvailableStreamFeatures() {
        // Nothing special to add
        return Collections.emptyList();
    }

    @Override
    public void setDetached() {
        throw new UnsupportedOperationException("Stream management is not supported for components.");
    }

    @Override
    public void reattach(LocalSession connectionProvider, long h) {
        throw new UnsupportedOperationException("Stream management is not supported for components.");
    }

    /**
     * Returns the connection associated with this Session.
     *
     * @return The connection for this session
     */
    @Nonnull
    @Override
    public Connection getConnection() {
        final Connection connection = super.getConnection();
        assert connection != null; // Openfire does not implement stream management for external component sessions. Therefor, the connection cannot be null.
        return connection;
    }

    @Override
    boolean canProcess(Packet packet) {
        return true;
    }

    @Override
    void deliver(Packet packet) throws PacketException {
        component.deliver(packet);
    }

    @Override
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
            Log.debug("LocalComponentSession: [ExComp] Incorrect handshake for component with domain: {}", defaultSubdomain);
            // The credentials supplied by the initiator are not valid (answer an error and close the connection).
            conn.close(new StreamError(StreamError.Condition.not_authorized));
            return false;
        }
        else {
            // Component has authenticated fine
            setStatus(Session.Status.AUTHENTICATED);
            // Send empty handshake element to acknowledge success
            conn.deliverRawText("<handshake></handshake>");
            // Bind the domain to this component
            ExternalComponent component = getExternalComponent();
            try {
                InternalComponentManager.getInstance().addComponent(defaultSubdomain, component);
                conn.registerCloseListener( handback -> InternalComponentManager.getInstance().removeComponent( defaultSubdomain, (ExternalComponent) handback ), component );
                Log.debug("LocalComponentSession: [ExComp] External component was registered SUCCESSFULLY with domain: {}", defaultSubdomain);
                return true;
            }
            catch (ComponentException e) {
                Log.debug("LocalComponentSession: [ExComp] Another component is already using domain: {}", defaultSubdomain, e);
                // The credentials supplied by the initiator are not valid (answer an error and close the connection).
                conn.close(new StreamError(StreamError.Condition.conflict, "Another component seems to be using this domain."));
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
        /**
         * Keeps track of the IQ (get/set) packets that were sent from a given component's connection. This
         * information will be used to ensure that the IQ reply will be sent to the same component's connection.
         */
        private static final Map<String, LocalExternalComponent> iqs = new HashMap<>();

        private LocalComponentSession session;
        private Connection connection;
        private String name = "";
        private String type = "";
        private String category = "";
        /**
         * List of subdomains that were binded for this component. The list will include
         * the initial subdomain.
         */
        private List<String> subdomains = new ArrayList<>();


        public LocalExternalComponent(LocalComponentSession session, Connection connection) {
            this.session = session;
            this.connection = connection;
        }

        @Override
        public void processPacket(Packet packet) {
            if (packet instanceof IQ) {
                IQ iq = (IQ) packet;
                if (iq.getType() == IQ.Type.result || iq.getType() == IQ.Type.error) {
                    // Check if this IQ reply belongs to a specific component and route
                    // reply to that specific component (if it exists)
                    LocalExternalComponent targetComponent;
                    synchronized (iqs) {
                        targetComponent = iqs.remove(packet.getID());
                    }
                    if (targetComponent != null) {
                        targetComponent.processPacket(packet);
                        return;
                    }
                }
            }
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
                    connection.close(new StreamError(StreamError.Condition.internal_server_error));
                }
            }
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return category + " - " + type;
        }

        @Override
        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public void setType(String type) {
            this.type = type;
        }

        @Override
        public String getCategory() {
            return category;
        }

        @Override
        public void setCategory(String category) {
            this.category = category;
        }

        @Override
        public String getInitialSubdomain() {
            if (subdomains.isEmpty()) {
                return null;
            }
            return subdomains.get(0);
        }

        private void addSubdomain(String subdomain) {
            subdomains.add(subdomain);
        }

        @Override
        public Collection<String> getSubdomains() {
            return subdomains;
        }

        @Override
        public void initialize(JID jid, ComponentManager componentManager) {
            addSubdomain(jid.toString());
        }

        @Override
        public void start() {
        }

        @Override
        public void shutdown() {
            // Remove tracking of IQ packets sent from this component
            synchronized (iqs) {
                List<String> toRemove = new ArrayList<>();
                for (Map.Entry<String,LocalExternalComponent> entry : iqs.entrySet()) {
                    if (entry.getValue() == this) {
                        toRemove.add(entry.getKey());
                    }
                }
                // Remove keys pointing to component being removed
                for (String key : toRemove) {
                    iqs.remove(key);
                }
            }
        }

        @Override
        public String toString() {
            return super.toString() + " - subdomains: " + subdomains;
        }

        public void track(IQ iq) {
            synchronized (iqs) {
                iqs.put(iq.getID(), this);
            }
        }
        /**
         * @return the session
         */
        public LocalComponentSession getSession() {
            return session;
        }
    }

    @Override
    public String toString()
    {
        return this.getClass().getSimpleName() +"{" +
            "address=" + address +
            ", streamID=" + streamID +
            ", status=" + status +
            ", isEncrypted=" + isEncrypted() +
            ", isDetached=" + isDetached() +
            ", serverName='" + serverName + '\'' +
            ", defaultSubdomain='" + defaultSubdomain + '\'' +
            '}';
    }
}
