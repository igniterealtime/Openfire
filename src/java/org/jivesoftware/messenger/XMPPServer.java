/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger;

import org.xmpp.packet.JID;
import org.jivesoftware.messenger.user.RosterManager;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.handler.IQRegisterHandler;
import org.jivesoftware.messenger.handler.PresenceUpdateHandler;
import org.jivesoftware.messenger.handler.PresenceSubscribeHandler;
import org.jivesoftware.messenger.handler.IQHandler;
import org.jivesoftware.messenger.transport.TransportHandler;
import org.jivesoftware.messenger.audit.AuditManager;
import org.jivesoftware.messenger.disco.ServerFeaturesProvider;
import org.jivesoftware.messenger.disco.ServerItemsProvider;
import org.jivesoftware.messenger.disco.IQDiscoInfoHandler;

import java.util.List;

/**
 * The XMPP server definition. An interface allows us to implement the
 * server's backend by plugging in different classes for the various
 * data managers. The class is designed so that you can host multiple server
 * instances in the same JVM.
 * <p/>
 * The server instance is typically obtained by using the getServer() method
 * on the Session object. This gives you the server within the proper security and
 * resource/QoS context of the session. Obtaining a server reference from any
 * other source is typically only needed by internal server components.
 * </p>
 *
 * @author Iain Shigeoka
 */
public interface XMPPServer {

    /**
     * Obtain a snapshot of the server's status.
     *
     * @return the server information current at the time of the method call.
     */
    public XMPPServerInfo getServerInfo();

    /**
     * Determines if the given address is local to the server (managed by this server domain).
     *
     * @return true if the address is a local address to this server.
     */
    public boolean isLocal(JID jid);

    /**
     * Creates an XMPPAddress local to this server.
     *
     * @param username the user name portion of the id or null to indicate none is needed.
     * @param resource the resource portion of the id or null to indicate none is needed.
     * @return an XMPPAddress for the server.
     */
    public JID createJID(String username, String resource);

    public ConnectionManager getConnectionManager();

    public RoutingTable getRoutingTable();

    public PacketDeliverer getPacketDeliverer();

    public RosterManager getRosterManager();

    public PresenceManager getPresenceManager();

    public OfflineMessageStore getOfflineMessageStore();

    public OfflineMessageStrategy getOfflineMessageStrategy();

    public PacketRouter getPacketRouter();

    public IQRegisterHandler getIQRegisterHandler();

    public List<IQHandler> getIQHandlers();

    public SessionManager getSessionManager();

    public TransportHandler getTransportHandler();

    public PresenceUpdateHandler getPresenceUpdateHandler();

    public PresenceSubscribeHandler getPresenceSubscribeHandler();

    public IQRouter getIQRouter();

    public MessageRouter getMessageRouter();

    public PresenceRouter getPresenceRouter();

    public UserManager getUserManager();

    public AuditManager getAuditManager();

    public List<ServerFeaturesProvider> getServerFeaturesProviders();

    public List<ServerItemsProvider> getServerItemsProviders();

    public IQDiscoInfoHandler getIQDiscoInfoHandler();

    public PrivateStorage getPrivateStorage();
}
