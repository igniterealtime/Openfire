/**
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.plugin.spark;

import org.jivesoftware.openfire.plugin.spark.manager.SparkVersionManager;
import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.event.SessionEventDispatcher;
import org.jivesoftware.openfire.event.SessionEventListener;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.stats.StatisticsManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.*;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles querying and notifications of enabled client features within the server, as well
 * as track related statistics, such as invalid client connections and number of Spark connections.
 *
 * @author Derek DeMoro
 */
public class SparkManager implements Component {

    private static final String INVALID_DISCONNECTS_KEY = "disconnects";
    private static final String SPARK_CLIENTS_KEY = "spark";

    private ComponentManager componentManager;
    private SessionManager sessionManager;
    private SparkSessionListener sessionEventListener;

    /**
     * Tracks number of disconnected clients.
     */
    private AtomicInteger disconnects;

    private StatisticsManager statisticsManager;

    private TaskEngine taskEngine;

    /**
     * Defined Service Name for Component.
     */
    private String serviceName = "manager";

    /**
     * Creates a new instance of the SparkManager to allow for listening and responding to
     * newly created client sessions.
     *
     * @param taskEngine the taskEngine.
     */
    public SparkManager(TaskEngine taskEngine) {
        this.taskEngine = taskEngine;

        sessionManager = SessionManager.getInstance();
        sessionEventListener = new SparkSessionListener();

        statisticsManager = StatisticsManager.getInstance();

        componentManager = ComponentManagerFactory.getComponentManager();

        // Register the SparkManager Component with the component manager
        // using the defined service name. This component is cluster-safe.
        try {
            componentManager.addComponent(serviceName, this);
        }
        catch (Exception e) {
            componentManager.getLog().error(e);
        }

        // Add VersionManager. This component is cluster-safe.
        try {
            componentManager.addComponent(SparkVersionManager.SERVICE_NAME, new SparkVersionManager());
        }
        catch (Exception e) {
            componentManager.getLog().error(e);
        }

        // Add SessionListener
        SessionEventDispatcher.addListener(sessionEventListener);

        disconnects = new AtomicInteger(0);
    }


    public String getName() {
        return "Features Component";
    }

    public String getDescription() {
        return "Allows for discovery of certain features.";
    }

    /**
     * Client features are detected using Service Discovery, allowing
     * for ease of use within the client.  When a client "discovers" the
     * manager, they can query for related features within that discovered item.
     *
     * @param packet the packet
     */
    public void processPacket(Packet packet) {
        if (packet instanceof IQ) {
            IQ iqPacket = (IQ)packet;

            Element childElement = (iqPacket).getChildElement();
            String namespace = null;
            if (childElement != null) {
                namespace = childElement.getNamespaceURI();
            }

            if (IQ.Type.get == iqPacket.getType()) {
                // Handle any disco info requests.
                if ("http://jabber.org/protocol/disco#info".equals(namespace)) {
                    handleDiscoInfo(iqPacket);
                }

                // Handle any disco item requests.
                else if ("http://jabber.org/protocol/disco#items".equals(namespace)) {
                    handleDiscoItems(iqPacket);
                }
                else if ("jabber:iq:version".equals(namespace)) {
                    IQ reply = IQ.createResultIQ(iqPacket);
                    Element version = reply.setChildElement("query", "jabber:iq:version");
                    version.addElement("name").setText("Enterprise Manager");
                    version.addElement("version").setText("3.2");
                    sendPacket(reply);
                }
                else {
                    // Return error since this is an unknown service request
                    IQ reply = IQ.createResultIQ(iqPacket);
                    reply.setError(PacketError.Condition.service_unavailable);
                    sendPacket(reply);
                }
            }
            else if (IQ.Type.error == iqPacket.getType() || IQ.Type.result == iqPacket.getType()) {
                if ("jabber:iq:version".equals(namespace)) {
                    handleClientVersion(iqPacket);
                }
            }
            else {
                // Return error since this is an unknown service request
                IQ reply = IQ.createResultIQ(iqPacket);
                reply.setError(PacketError.Condition.service_unavailable);
                sendPacket(reply);
            }
        }
    }

    /**
     * Handles the IQ version reply. If only a given list of clients are allowed to connect
     * then the reply will be analyzed. If the client is not present in the list, no name
     * was responsed or an IQ error was returned (e.g. IQ version not supported) then
     * the client session will be terminated.
     *
     * @param iq the IQ version reply sent by the client.
     */
    private void handleClientVersion(IQ iq) {
        final String clientsAllowed = JiveGlobals.getProperty("clients.allowed", "all");
        final boolean disconnectIfNoMatch = !"all".equals(clientsAllowed);
        if ("all".equals(clientsAllowed) || !disconnectIfNoMatch) {
            // There is nothing to do here. Just return.
            return;
        }

        // Get the client session of the user that sent the IQ version response
        ClientSession session = sessionManager.getSession(iq.getFrom());
        if (session == null) {
            // Do nothing if the session no longer exists
            return;
        }

        if (IQ.Type.result == iq.getType()) {
            // Get list of allowed clients to connect
            final List<String> clients = new ArrayList<String>();

            StringTokenizer clientTokens = new StringTokenizer(clientsAllowed, ",");
            while (clientTokens.hasMoreTokens()) {
                clients.add(clientTokens.nextToken().toLowerCase());
            }

            final String otherClientsAllowed = JiveGlobals.getProperty("other.clients.allowed", "");
            clientTokens = new StringTokenizer(otherClientsAllowed, ",");
            while (clientTokens.hasMoreTokens()) {
                clients.add(clientTokens.nextToken().toLowerCase().trim());
            }

            Element child = iq.getChildElement();
            String clientName = child.elementTextTrim("name");
            boolean disconnect = true;
            if (clientName != null) {
                // Check if the client should be disconnected
                for(String c : clients){
                    if(clientName.toLowerCase().contains(c)){
                        disconnect = false;
                        break;
                    }
                }
            }
            else {
                // Always disconnect clients that didn't provide their name
                disconnect = true;
            }
            if (disconnect) {
                closeSession(session, clientName != null ? clientName : "Unknown");
            }
        }
        else {
            // If the session is invalid. Close the connection.
            closeSession(session, "Unknown");
        }
    }

    public void initialize(JID jid, ComponentManager componentManager) throws ComponentException {
        // Do nothing.
    }

    public void start() {
        // Do nothing.
    }

    /**
     * Unload the Component.
     */
    public void stop() {
        // Unregister components
        try {
            componentManager.removeComponent(SparkVersionManager.SERVICE_NAME);
            // Finally remove this service (this will set null to componentManager)
            componentManager.removeComponent(serviceName);
        }
        catch (ComponentException e) {
            componentManager.getLog().error(e);
        }

        taskEngine = null;
    }

    /**
     * Remove any resources SparkManager was using. This will allow
     * for a clean reload.
     */
    public void shutdown() {
        // Cleanup
        SessionEventDispatcher.removeListener(sessionEventListener);

        if (statisticsManager != null) {
            statisticsManager.removeStatistic(SPARK_CLIENTS_KEY);
            statisticsManager.removeStatistic(INVALID_DISCONNECTS_KEY);
        }

        componentManager = null;
        sessionManager = null;
        sessionEventListener = null;
        statisticsManager = null;
    }


    /**
     * Sends a reply for a ServiceDiscovery.
     *
     * @param packet the packet.
     */
    private void handleDiscoItems(IQ packet) {
        IQ replyPacket = IQ.createResultIQ(packet);
        replyPacket.setChildElement("query", "http://jabber.org/protocol/disco#items");
        sendPacket(replyPacket);
    }

    /**
     * Send a reply back to the client to inform the client that this server has
     * a Spark Manager.
     *
     * @param packet the IQ packet.
     */
    private void handleDiscoInfo(IQ packet) {
        IQ replyPacket = IQ.createResultIQ(packet);
        Element responseElement =
                replyPacket.setChildElement("query", "http://jabber.org/protocol/disco#info");
        Element identity = responseElement.addElement("identity");
        identity.addAttribute("category", "manager");
        identity.addAttribute("type", "text");
        identity.addAttribute("name", "Enterprise Manager");
        // Add features set
        buildFeatureSet(responseElement);
        // Send reply
        sendPacket(replyPacket);
    }

    private void sendPacket(Packet packet) {
        try {
            componentManager.sendPacket(this, packet);
        }
        catch (ComponentException e) {
            componentManager.getLog().error(e);
        }
    }

    /**
     * Builds an element list of all features enabled.
     *
     * @param responseElement the feature response element.
     */
    private void buildFeatureSet(Element responseElement) {
        // Check for broadcast service.
        boolean broadcastEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("broadcast.enabled", "true"));
        if (broadcastEnabled) {
            responseElement.addElement("feature").addAttribute("var", "broadcast");
        }

        boolean fileTransferEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("transfer.enabled", "true"));
        if (fileTransferEnabled) {
            responseElement.addElement("feature").addAttribute("var", "file-transfer");
        }

        boolean mucEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("muc.enabled", "true"));
        if (mucEnabled) {
            responseElement.addElement("feature").addAttribute("var", "muc");
        }

        boolean vcardEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("vcard.enabled", "true"));
        if (vcardEnabled) {
            responseElement.addElement("feature").addAttribute("var", "vcard");
        }
    }

    /**
     * Notify all users who have requested disco information from this component that settings have been changed.
     * Clients should perform a new service discovery to see what has changed.
     */
    public void notifyDiscoInfoChanged() {
        final Message message = new Message();
        message.setFrom(serviceName + "." + componentManager.getServerName());
        Element child = message.addChildElement("event", "http://jabber.org/protocol/disco#info");
        buildFeatureSet(child);
        sessionManager.broadcast(message);
    }

    /**
     * Listener to check all new client connections to validate against
     * the server side client validate scheme.
     */
    private class SparkSessionListener implements SessionEventListener {

        /**
         * A new session was created.
         *
         * @param session the newly created session.
         */
        public void sessionCreated(final Session session) {
            // Check to see if Spark is required.
            String clientsAllowed = JiveGlobals.getProperty("clients.allowed", "all");
            final boolean disconnectIfNoMatch = !"all".equals(clientsAllowed);

            if (disconnectIfNoMatch) {
                // TODO: A future version may want to close sessions of users that never
                // TODO: responded the IQ version request.
                taskEngine.schedule(new TimerTask() {
                    public void run() {
                        requestSoftwareVersion(session);
                    }
                }, 5000);
            }
        }

        /**
         * A session was destroyed.
         *
         * @param session the session destroyed.
         */
        public void sessionDestroyed(Session session) {

        }

        public void resourceBound(Session session) {
            // Do nothing.
        }

        public void anonymousSessionCreated(Session session) {
            // Ignore.
        }

        public void anonymousSessionDestroyed(Session session) {
            // Ignore.
        }
    }

    /**
     * Make a version request (JEP-0092) of the client the specified session is using. If the response is NOT the Spark IM
     * client, send a StreamError notification and disconnect the user.
     *
     * @param session             the users session.
     */
    private void requestSoftwareVersion(final Session session) {

        // Send IQ get to check client version.
        final IQ clientPacket = new IQ(IQ.Type.get);
        clientPacket.setTo(session.getAddress());
        clientPacket.setFrom(serviceName + "." + componentManager.getServerName());

        clientPacket.setChildElement("query", "jabber:iq:version");

        sendPacket(clientPacket);
    }

    /**
     * Sends an unsupported version error and name of client the user attempted to connect with.
     *
     * @param session    the users current session.
     * @param clientName the name of the client they were connecting with.
     */
    private void closeSession(final Session session, String clientName) {
        // Increase the number of logins not allowed by 1.
        disconnects.incrementAndGet();

        Log.debug("Closed connection to client attempting to connect from " + clientName);

        // Send message information user.
        final Message message = new Message();
        message.setFrom(serviceName + "." + componentManager.getServerName());
        message.setTo(session.getAddress());

        message.setBody("You are using an invalid client, and therefore will be disconnected. "
                + "Please ask your system administrator for client choices.");

        // Send Message
        sendPacket(message);

        // Disconnect user after 5 seconds.
        taskEngine.schedule(new TimerTask() {
            public void run() {
                // Include the not-authorized error in the response
                StreamError error = new StreamError(StreamError.Condition.policy_violation);
                session.deliverRawText(error.toXML());
                // Close the underlying connection
                session.close();
            }
        }, 5000);


    }

    /**
     * Returns the number of logins which were not valid due to Spark Manager restrictions.
     *
     * @return the number of logins not allowed.
     */
    public int getNumberOfLoginsNotAllowed() {
        return disconnects.getAndSet(0);
    }
}