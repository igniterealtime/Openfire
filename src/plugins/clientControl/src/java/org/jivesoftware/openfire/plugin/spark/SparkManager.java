/*
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.plugin.spark;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.event.SessionEventDispatcher;
import org.jivesoftware.openfire.event.SessionEventListener;
import org.jivesoftware.openfire.plugin.spark.manager.SparkVersionManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.stats.StatisticsManager;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.StreamError;

/**
 * Handles querying and notifications of enabled client features within the server, as well
 * as track related statistics, such as invalid client connections and number of Spark connections.
 *
 * @author Derek DeMoro
 */
public class SparkManager implements Component {

    private static final Logger Log = LoggerFactory.getLogger(SparkManager.class);
    
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
            Log.error(e.getMessage(), e);
        }

        // Add VersionManager. This component is cluster-safe.
        try {
            componentManager.addComponent(SparkVersionManager.SERVICE_NAME, new SparkVersionManager());
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
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
                    version.addElement("name").setText("Client Control Manager");
                    version.addElement("version").setText("3.5");
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
            Log.error(e.getMessage(), e);
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
        identity.addAttribute("name", "Client Control Manager");
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
            Log.error(e.getMessage(), e);
        }
    }

    /**
     * Builds an element list of all features enabled.
     *
     * @param responseElement the feature response element.
     */
    private void buildFeatureSet(Element responseElement) {
        // Check for ACCOUNT REGISTRATION feature
        boolean accountsEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("accounts.enabled", "true"));
        if (accountsEnabled) {
            responseElement.addElement("feature").addAttribute("var", "accounts-reg");
        }

        // Check for ADD CONTACTS feature
        boolean addcontactsEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("addcontacts.enabled", "true"));
        if (addcontactsEnabled) {
            responseElement.addElement("feature").addAttribute("var", "add-contacts");
        }

        // Check for ADD GROUPS feature
        boolean addgroupsEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("addgroups.enabled", "true"));
        if (addgroupsEnabled) {
            responseElement.addElement("feature").addAttribute("var", "add-groups");
        }

        // Check for ADVANCED CONFIGURATION feature
        boolean advancedEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("advanced.enabled", "true"));
        if (advancedEnabled) {
            responseElement.addElement("feature").addAttribute("var", "advanced-config");
        }

        // Check for AVATARS feature
        boolean avatarsEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("avatars.enabled", "true"));
        if (avatarsEnabled) {
            responseElement.addElement("feature").addAttribute("var", "avatar-tab");
        }

        // Check for BROADCASTING feature
        boolean broadcastEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("broadcast.enabled", "true"));
        if (broadcastEnabled) {
            responseElement.addElement("feature").addAttribute("var", "broadcast");
        }

        // Check for CONTACT & GROUP REMOVALS feature
        boolean removalsEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("removals.enabled", "true"));
        if (removalsEnabled) {
            responseElement.addElement("feature").addAttribute("var", "removals");
        }

        // Check for CONTACT & GROUP RENAMES feature
        boolean renamesEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("renames.enabled", "true"));
        if (renamesEnabled) {
            responseElement.addElement("feature").addAttribute("var", "renames");
        }

        // Check for FILE TRANSFER feature
        boolean fileTransferEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("transfer.enabled", "true"));
        if (fileTransferEnabled) {
            responseElement.addElement("feature").addAttribute("var", "file-transfer");
        }

        // Check for HELP FORUMS feature
        boolean helpforumsEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("helpforums.enabled", "true"));
        if (helpforumsEnabled) {
            responseElement.addElement("feature").addAttribute("var", "help-forums");
        }

        // Check for HELP USER GUIDE feature
        boolean helpuserguideEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("helpuserguide.enabled", "true"));
        if (helpuserguideEnabled) {
            responseElement.addElement("feature").addAttribute("var", "help-userguide");
        }

        // Check for HISTORY SETTINGS feature
        boolean historysettingsEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("historysettings.enabled", "true"));
        if (historysettingsEnabled) {
            responseElement.addElement("feature").addAttribute("var", "history-settings");
        }

        // Check for HISTORY TRANSCRIPTS feature
        boolean historytranscriptsEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("historytranscripts.enabled", "true"));
        if (historytranscriptsEnabled) {
            responseElement.addElement("feature").addAttribute("var", "history-transcripts");
        }

        // Check for HOST NAME CHANGE feature
        boolean hostnameEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("hostname.enabled", "true"));
        if (hostnameEnabled) {
            responseElement.addElement("feature").addAttribute("var", "host-name");
        }

        // Check for LOGIN AS INVISIBLE feature
        boolean invisibleloginEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("invisiblelogin.enabled", "true"));
        if (invisibleloginEnabled) {
            responseElement.addElement("feature").addAttribute("var", "invisible-login");
        }
        
        // Check for LOGIN ANONYMOUSLY feature        
        boolean anonymousloginEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("anonymouslogin.enabled", "true"));
        if (anonymousloginEnabled) {
            responseElement.addElement("feature").addAttribute("var", "anonymous-login");
        }

        // Check for LOGOUT & EXIT feature
        boolean logoutexitEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("logoutexit.enabled", "true"));
        if (logoutexitEnabled) {
            responseElement.addElement("feature").addAttribute("var", "logout-exit");
        }

        // Check for MOVE & COPY CONTACTS feature
        boolean movecopyEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("movecopy.enabled", "true"));
        if (movecopyEnabled) {
            responseElement.addElement("feature").addAttribute("var", "move-copy");
        }

        // Check for MUC feature
        boolean mucEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("muc.enabled", "true"));
        if (mucEnabled) {
            responseElement.addElement("feature").addAttribute("var", "muc");
        }

        // Check for PASSWORD CHANGE feature
        boolean passwordchangeEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("passwordchange.enabled", "true"));
        if (passwordchangeEnabled) {
            responseElement.addElement("feature").addAttribute("var", "password-change");
        }

        // Check for PERSON SEARCH FIELD feature
        boolean personsearchEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("personsearch.enabled", "true"));
        if (personsearchEnabled) {
            responseElement.addElement("feature").addAttribute("var", "person-search");
        }

        // Check for PLUGINS MENU feature
        boolean pluginsEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("plugins.enabled", "true"));
        if (pluginsEnabled) {
            responseElement.addElement("feature").addAttribute("var", "plugins-menu");
        }

        // Check for PREFERENCES MENU feature        
        boolean preferencesEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("preferences.enabled", "true"));
        if (preferencesEnabled) {
            responseElement.addElement("feature").addAttribute("var", "preferences-menu");
        }

        // Check for PRESENCE STATUS CHANGE feature        
        boolean presenceEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("presence.enabled", "true"));
        if (presenceEnabled) {
            responseElement.addElement("feature").addAttribute("var", "presence-status");
        }

        // Check for PROFILE & AVATAR EDITING feature
        boolean vcardEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("vcard.enabled", "true"));
        if (vcardEnabled) {
            responseElement.addElement("feature").addAttribute("var", "vcard");
        }

        // Check for SAVE PASSWORD & AUTOLOGIN feature
        boolean savepassandautologinEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("savepassandautologin.enabled", "true"));
        if (savepassandautologinEnabled) {
            responseElement.addElement("feature").addAttribute("var", "save-password");
        }

        // Check for UPDATES feature
        boolean updatesEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("updates.enabled", "true"));
        if (updatesEnabled) {
            responseElement.addElement("feature").addAttribute("var", "updates");
        }

        // Check for VIEW NOTES feature        
        boolean viewnotesEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("viewnotes.enabled", "true"));
        if (viewnotesEnabled) {
            responseElement.addElement("feature").addAttribute("var", "view-notes");
        }

        // Check for VIEW TASK LIST feature        
        boolean viewtasklistEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("viewtasklist.enabled", "true"));
        if (viewtasklistEnabled) {
            responseElement.addElement("feature").addAttribute("var", "view-tasks");
        }
        
        // Check for START A CHAT feature        
        boolean startachatEnabled = Boolean.parseBoolean(JiveGlobals.getProperty("startachat.enabled", "true"));
        if (startachatEnabled) {
            responseElement.addElement("feature").addAttribute("var", "start-a-chat");
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
                    @Override
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
            @Override
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
