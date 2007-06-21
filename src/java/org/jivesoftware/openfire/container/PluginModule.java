/**
 * $Revision:$
 * $Date:$
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.openfire.container;

import com.google.inject.*;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.session.RemoteSessionLocator;
import org.jivesoftware.openfire.vcard.VCardManager;
import org.jivesoftware.openfire.stun.STUNService;
import org.jivesoftware.openfire.mediaproxy.MediaProxyService;
import org.jivesoftware.openfire.filetransfer.proxy.FileTransferProxy;
import org.jivesoftware.openfire.filetransfer.FileTransferManager;
import org.jivesoftware.openfire.commands.AdHocCommandHandler;
import org.jivesoftware.openfire.muc.MultiUserChatServer;
import org.jivesoftware.openfire.disco.IQDiscoItemsHandler;
import org.jivesoftware.openfire.audit.AuditManager;
import org.jivesoftware.openfire.update.UpdateManager;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.pubsub.PubSubModule;
import org.jivesoftware.openfire.handler.IQRegisterHandler;
import org.jivesoftware.openfire.handler.IQAuthHandler;
import org.jivesoftware.openfire.handler.PresenceUpdateHandler;
import org.jivesoftware.openfire.handler.PresenceSubscribeHandler;
import org.jivesoftware.openfire.roster.RosterManager;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;

/**
 *
 */
public class PluginModule extends AbstractModule {

    protected void configure() {
        XMPPServer server = XMPPServer.getInstance();
        bind(XMPPServer.class).toInstance(server);
        bind(ConnectionManager.class).toInstance(server.getConnectionManager());
        bind(RoutingTable.class).toInstance(server.getRoutingTable());
        bind(PacketDeliverer.class).toInstance(server.getPacketDeliverer());
        bind(RosterManager.class).toInstance(server.getRosterManager());
        bind(PresenceManager.class).toInstance(server.getPresenceManager());
        bind(OfflineMessageStore.class).toInstance(server.getOfflineMessageStore());
        bind(OfflineMessageStrategy.class).toInstance(server.getOfflineMessageStrategy());
        bind(PacketRouter.class).toInstance(server.getPacketRouter());
        bind(IQRegisterHandler.class).toInstance(server.getIQRegisterHandler());
        bind(IQAuthHandler.class).toInstance(server.getIQAuthHandler());
        bind(PluginManager.class).toInstance(server.getPluginManager());
        bind(PubSubModule.class).toInstance(server.getPubSubModule());
        bind(SessionManager.class).toInstance(server.getSessionManager());
        bind(PresenceUpdateHandler.class).toInstance(server.getPresenceUpdateHandler());
        bind(PresenceSubscribeHandler.class).toInstance(server.getPresenceSubscribeHandler());
        bind(IQRouter.class).toInstance(server.getIQRouter());
        bind(MessageRouter.class).toInstance(server.getMessageRouter());
        bind(PresenceRouter.class).toInstance(server.getPresenceRouter());
        bind(MulticastRouter.class).toInstance(server.getMulticastRouter());
        bind(UserManager.class).toInstance(server.getUserManager());
        bind(UpdateManager.class).toInstance(server.getUpdateManager());
        bind(AuditManager.class).toInstance(server.getAuditManager());
        bind(IQDiscoItemsHandler.class).toInstance(server.getIQDiscoItemsHandler());
        bind(PrivateStorage.class).toInstance(server.getPrivateStorage());
        bind(MultiUserChatServer.class).toInstance(server.getMultiUserChatServer());
        bind(AdHocCommandHandler.class).toInstance(server.getAdHocCommandHandler());
        bind(FileTransferProxy.class).toInstance(server.getFileTransferProxy());
        bind(FileTransferManager.class).toInstance(server.getFileTransferManager());
        bind(MediaProxyService.class).toInstance(server.getMediaProxyService());
        bind(STUNService.class).toInstance(server.getSTUNService());
        bind(VCardManager.class).toInstance(server.getVCardManager());
        bind(RemoteSessionLocator.class).toProvider(new Provider<RemoteSessionLocator>() {
            private XMPPServer xmppServer;

            @Inject
            public void setXMPPServer(XMPPServer xmppServer) {
                this.xmppServer = xmppServer;
            }

            public RemoteSessionLocator get() {
                return xmppServer.getRemoteSessionLocator();
            }
        });
        bind(ComponentManager.class).toProvider(new Provider<ComponentManager>() {

            public ComponentManager get() {
                return ComponentManagerFactory.getComponentManager();
            }
        });
    }
}
