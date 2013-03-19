/**
 * $RCSfile$
 * $Revision: 1722 $
 * $Date: 2005-07-28 19:19:16 -0300 (Thu, 28 Jul 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.plugin;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jivesoftware.openfire.PresenceManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * Plugin that includes a servlet that provides information about users' and components'
 * presence in the server. For security reasons, the XMPP spec does not allow anyone to see
 * the presence of any user. Only the users that are subscribed to the presence of other
 * users may see their presences.<p/>
 *
 * However, in order to make the servlet more useful it is possible to configure this plugin
 * so that anyone or only the users that are subscribed to a user presence may see the presence
 * of other users.<p/>
 *
 * Currently, the servlet provides presence information in two formats: 1) In XML format
 * and 2) using images.<p>
 *
 * The presence plugin is also a component so that it can probe presences of other components.
 * The new component will use <tt>presence</tt> as the subdomain subdomain.
 *
 * @author Gaston Dombiak
 */
public class PresencePlugin implements Plugin, Component {

	private static final Logger Log = LoggerFactory.getLogger(PresencePlugin.class);
	
    private static final String subdomain = "presence";

    private UserManager userManager;
    private PresenceManager presenceManager;
    private PluginManager pluginManager;
    private ComponentManager componentManager;
    private String hostname;
    private Map<String, Presence> probedPresence;
    private JID componentJID;

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        pluginManager = manager;
        XMPPServer server = XMPPServer.getInstance();
        userManager = server.getUserManager();
        presenceManager = server.getPresenceManager();
        hostname = server.getServerInfo().getXMPPDomain();
        probedPresence = new ConcurrentHashMap<String, Presence>();
        componentJID = new JID(subdomain + "." + hostname);
        // Register new component
        componentManager = ComponentManagerFactory.getComponentManager();
        try {
            componentManager.addComponent(subdomain, this);
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }

    public void destroyPlugin() {
        userManager = null;
        presenceManager = null;
        // Remove presence plugin component
        try {
            componentManager.removeComponent(subdomain);
            componentManager = null;
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }

    public String getName() {
        return pluginManager.getName(this);
    }

    public String getDescription() {
        return pluginManager.getDescription(this);
    }

    public void initialize(JID jid, ComponentManager componentManager) {
    }

    public void start() {
    }

    public void shutdown() {
    }

    public void processPacket(Packet packet) {
        // Check that we are getting an answer to a presence probe
        if (packet instanceof Presence) {
            Presence presence = (Presence) packet;
            if (presence.isAvailable() || presence.getType() == Presence.Type.unavailable ||
                    presence.getType() == Presence.Type.error) {
                // Store answer of presence probes
                probedPresence.put(presence.getFrom().toString(), presence);
            }
        }
    }

    /**
     * Returns true if anyone is allowed to see the presence of other users. False means that
     * only the users that are subscribed to a user presence will be able to get information
     * about the user. By default, presence information is not publicly available.
     *
     * @return true if anyone is allowed to see the presence of other users.
     */
    public boolean isPresencePublic() {
        return JiveGlobals.getBooleanProperty("plugin.presence.public", false);
    }

    /**
     * Sets if anyone is allowed to see the presence of other users. A false value means that
     * only the users that are subscribed to a user presence will be able to get information
     * about the user. By default, presence information is not publicly available.
     *
     * @param presencesPublic if anyone is allowed to see the presence of other users.
     */
    public void setPresencePublic(boolean presencesPublic) {
        JiveGlobals.setProperty("plugin.presence.public", presencesPublic ? "true" : "false");
    }

    /**
     * Returns the status message for the unavailable presence. This setting allows
     * a different string to be used for the status on this presence which is
     * "Unavailable" by default.
     *
     * @return the status message for the unavailable presence.
     */
    public String getUnavailableStatus() {
        return JiveGlobals.getProperty("plugin.presence.unavailable.status", "Unavailable");
    }

    /**
     * Sets the status message for the unavailable presence. This setting allows
     * a different string to be used for the status on this presence which is
     * "Unavailable" by default.
     *
     * @param statusMessage the status message for the unavailable presence.
     */
    public void setUnavailableStatus(String statusMessage) {
        JiveGlobals.setProperty("plugin.presence.unavailable.status", statusMessage);
    }

    /**
     * Returns the presence of the requested user or <tt>null</tt> if the user is offline. If
     * presences are not public then the user presence will be returned if and only if the sender
     * of the request is subscribed to the user presence.
     *
     * @param sender the bare JID of the user making the request.
     * @param jid the bare JID of the entity whose presence is being probed.
     * @return the presence of the requested user.
     * @throws UserNotFoundException If presences are not public and the sender is null or the
     *         sender cannot probe the presence of the requested user. Or if the requested user
     *         does not exist in the local server.
     */
    public Presence getPresence(String sender, String jid) throws UserNotFoundException {
        if (jid == null) {
            throw new UserNotFoundException("Target JID not found in request");
        }
        JID targetJID = new JID(jid);

        // Check that the sender is not requesting information of a remote server entity
        if (targetJID.getDomain() == null || XMPPServer.getInstance().isRemote(targetJID)) {
            throw new UserNotFoundException("Domain does not matches local server domain");
        }
        if (!hostname.equals(targetJID.getDomain())) {
            // Sender is requesting information about component presence, so we send a 
            // presence probe to the component.
            presenceManager.probePresence(componentJID, targetJID);

            // Wait 30 seconds until we get the probe presence result
            int count = 0;
            Presence presence = probedPresence.get(jid);
            while (presence == null) {
                if (count > 300) {
                    // After 30 seconds, timeout
                    throw new UserNotFoundException(
                            "Request for component presence has timed-out.");
                }
                try {
                    Thread.sleep(100);
                }
                catch (InterruptedException e) {
                    // don't care!
                }
                presence = probedPresence.get(jid);

                count++;
            }
            // Clean-up probe presence result
            probedPresence.remove(jid);
            // Return component presence
            return presence;
        }
        if (targetJID.getNode() == null ||
                !UserManager.getInstance().isRegisteredUser(targetJID.getNode())) {
            // Sender is requesting presence information of an anonymous user
            throw new UserNotFoundException("Username is null");
        }
        if (!isPresencePublic()) {
            if (sender == null) {
                throw new UserNotFoundException("Sender is null");
            }
            else {
                //  If sender is same as target, then we can see ourselves
                JID senderJID = new JID(sender);
                if (!senderJID.getNode().equals(targetJID.getNode()) &&
                    !presenceManager.canProbePresence(new JID(sender), targetJID.getNode())) {
                    throw new UserNotFoundException("Sender is not allowed to probe this user");
                }
            }
        }
        User user = userManager.getUser(targetJID.getNode());
        return presenceManager.getPresence(user);
    }
}
