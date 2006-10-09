/**
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.wildfire.PresenceRouter;
import org.jivesoftware.wildfire.Session;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.container.Plugin;
import org.jivesoftware.wildfire.container.PluginManager;
import org.jivesoftware.wildfire.interceptor.InterceptorManager;
import org.jivesoftware.wildfire.interceptor.PacketInterceptor;
import org.jivesoftware.wildfire.interceptor.PacketRejectedException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;
import org.xmpp.packet.Presence.Type;

/**
 * This plugin can be configuured to automatically accept or reject subscription
 * requests. When set to accept subscription requests users will be able to add
 * someone to their roster without having to wait for a manual subscription
 * acceptance from the other person. Conversely, when the plugin is set to
 * reject subscription requests users will not be able to add people to their
 * roster.
 * 
 * @author <a href="mailto:ryan@version2software.com">Ryan Graham</a>
 */
public class SubscriptionPlugin implements Plugin {
    public static final String DISABLED = "disabled";
    public static final String ACCEPT = "accept";
    public static final String REJECT = "reject";
    public static final String LOCAL = "local";
    public static final String ALL = "all";

    private static final String SUBSCRIPTION_TYPE = "plugin.subscription.type";
    private static final String SUBSCRIPTION_LEVEL = "plugin.subscription.level";
    private static final String WHITE_LIST = "plugin.subscription.whiteList";
    
    private List<String> whiteList = new ArrayList<String>();

    private SuscriptionPacketInterceptor interceptor = new SuscriptionPacketInterceptor();

    private PresenceRouter router;
    private String serverName;
    
    public SubscriptionPlugin() {
        XMPPServer server = XMPPServer.getInstance();
        router = server.getPresenceRouter();
        serverName = server.getServerInfo().getName();

        String list = JiveGlobals.getProperty(WHITE_LIST);
        if (list != null) {
            whiteList.addAll(csvToList(list));
        }
    }

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        InterceptorManager.getInstance().addInterceptor(interceptor);
    }

    public void destroyPlugin() {
        InterceptorManager.getInstance().removeInterceptor(interceptor);
        interceptor = null;

        router = null;
        serverName = null;

        whiteList = null;
    }

    public void setSubscriptionType(String type) {
        JiveGlobals.setProperty(SUBSCRIPTION_TYPE, type);
    }

    public String getSubscriptionType() {
        return JiveGlobals.getProperty(SUBSCRIPTION_TYPE, DISABLED);
    }

    public void setSubscriptionLevel(String level) {
        JiveGlobals.setProperty(SUBSCRIPTION_LEVEL, level);
    }

    public String getSubscriptionLevel() {
        return JiveGlobals.getProperty(SUBSCRIPTION_LEVEL, LOCAL);
    }

    public Collection<String> getWhiteListUsers() {
        Collections.sort(whiteList);
        return whiteList;
    }

    public void addWhiteListUser(String user) {
        if (!whiteList.contains(user.trim().toLowerCase())) {
            whiteList.add(user.trim().toLowerCase());
            JiveGlobals.setProperty(WHITE_LIST, listToCSV(whiteList));
        }
    }

    public void removeWhiteListUser(String user) {
        whiteList.remove(user.trim().toLowerCase());
        if (whiteList.size() == 0) {
            JiveGlobals.deleteProperty(WHITE_LIST);
        }
        else {
            JiveGlobals.setProperty(WHITE_LIST, listToCSV(whiteList));
        }
    }

    private String listToCSV(List<String> list) {
        StringBuilder sb = new StringBuilder();

        Iterator<String> iter = list.iterator();
        while (iter.hasNext()) {
            String s = iter.next();
            sb.append(s);
            if (iter.hasNext()) {
                sb.append(",");
            }
        }

        return sb.toString();
    }

    private List<String> csvToList(String csv) {
        List<String> list = new ArrayList<String>();

        for (String s : csv.split(",")) {
            list.add(s);
        }

        return list;
    }

    private class SuscriptionPacketInterceptor implements PacketInterceptor {
        public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed) throws PacketRejectedException {
            String type = getSubscriptionType();

            if (type.equals(DISABLED)) {
                return;
            }

            if ((packet instanceof Presence) && !processed) {
                Presence presencePacket = (Presence) packet;

                Type presenceType = presencePacket.getType();
                if (presenceType != null && presenceType.equals(Presence.Type.subscribe)) {
                    JID toJID = presencePacket.getTo();
                    JID fromJID = presencePacket.getFrom();

                    String toNode = toJID.getNode();
                    if (whiteList.contains(toNode)) {
                        return;
                    }

                    if (type.equals(ACCEPT)) {
                        acceptSubscription(toJID, fromJID);
                    }

                    if (type.equals(REJECT)) {
                        rejectSubscription(toJID, fromJID);
                    }
                }
            }
        }

        private void acceptSubscription(JID toJID, JID fromJID) throws PacketRejectedException {
            if (getSubscriptionLevel().equals(LOCAL)) {
                String toDomain = toJID.getDomain();
                String fromDomain = fromJID.getDomain();

                if (!toDomain.equals(serverName) || !fromDomain.equals(serverName)) {
                    return;
                }
            }

            Presence presence = new Presence();
            presence.setType(Presence.Type.subscribed);

            presence.setTo(toJID);
            presence.setFrom(fromJID);
            router.route(presence);

            presence.setTo(fromJID);
            presence.setFrom(toJID);
            router.route(presence);

            throw new PacketRejectedException();
        }

        private void rejectSubscription(JID toJID, JID fromJID) throws PacketRejectedException {
            if (getSubscriptionLevel().equals(LOCAL)) {
                String toDomain = toJID.getDomain();
                String fromDomain = fromJID.getDomain();

                if (toDomain.equals(serverName) && fromDomain.equals(serverName)) {
                    return;
                }
            }

            Presence presence = new Presence();
            presence.setType(Presence.Type.unsubscribe);

            // This is to get around an issue in Spark
            // (http://www.jivesoftware.org/issues/browse/SPARK-300).
            // Unfortunately, this is a bit of a hack and can easily be defeated
            // if a user changes their resource when using Spark.
            if (JiveGlobals.getBooleanProperty("plugin.subscription.sparkCheck", false)) {
                String resource = fromJID.getResource();
                if (resource != null && resource.equalsIgnoreCase("Spark")) {
                    presence.setType(Presence.Type.unsubscribed);
                }
            }

            presence.setTo(fromJID);
            presence.setFrom(toJID);
            router.route(presence);

            throw new PacketRejectedException();
        }
    }
}
