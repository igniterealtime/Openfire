/**
 * $Revision:$
 * $Date:$
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.openfire.plugin;

import com.google.inject.*;
import org.jivesoftware.util.JiveProperties;
import org.jivesoftware.util.Logger;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.session.RemoteSessionLocator;
import org.jivesoftware.openfire.group.GroupManager;
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
import org.jivesoftware.openfire.container.plugin.ServerName;
import org.jivesoftware.openfire.container.PluginManager;
import org.xmpp.component.ComponentManager;
import org.jmock.Mockery;

/**
 *
 */
public class AbstractPluginSupport {

    private AbstractPluginSupport() {
    }

    public static Injector createInjector(Mockery context) {
        return createInjector(context, new Module() {
            public void configure(Binder binder) {
            }
        });
    }

    public static Injector createInjector(Mockery context, Module pluginModule) {
        return Guice.createInjector(Stage.DEVELOPMENT, new MockPluginModule(context), pluginModule);
    }

    public static class MockPluginModule extends AbstractModule {
        private final Mockery context;

        public MockPluginModule(Mockery context) {
            this.context = context;
        }

        protected void configure() {
            bind(JiveProperties.class).toInstance(context.mock(JiveProperties.class));
            bind(Logger.class).toInstance(context.mock(Logger.class));

            bind(String.class).annotatedWith(ServerName.class).toProvider(new Provider<String>() {
                public String get() {
                    return "example.com";
                }
            });
            bind(XMPPServer.class).toInstance(context.mock(XMPPServer.class));
            bind(ConnectionManager.class).toInstance(context.mock(ConnectionManager.class));
            bind(RoutingTable.class).toInstance(context.mock(RoutingTable.class));
            bind(PacketDeliverer.class).toInstance(context.mock(PacketDeliverer.class));
            bind(RosterManager.class).toInstance(context.mock(RosterManager.class));
            bind(PresenceManager.class).toInstance(context.mock(PresenceManager.class));
            bind(OfflineMessageStore.class).toInstance(context.mock(OfflineMessageStore.class));
            bind(OfflineMessageStrategy.class).toInstance(
                    context.mock(OfflineMessageStrategy.class));
            bind(PacketRouter.class).toInstance(context.mock(PacketRouter.class));
            bind(IQRegisterHandler.class).toInstance(context.mock(IQRegisterHandler.class));
            bind(IQAuthHandler.class).toInstance(context.mock(IQAuthHandler.class));
            bind(PluginManager.class).toInstance(context.mock(PluginManager.class));
            bind(PubSubModule.class).toInstance(context.mock(PubSubModule.class));
            bind(SessionManager.class).toInstance(context.mock(SessionManager.class));
            bind(PresenceUpdateHandler.class).toInstance(context.mock(PresenceUpdateHandler.class));
            bind(PresenceSubscribeHandler.class).toInstance(
                    context.mock(PresenceSubscribeHandler.class));
            bind(IQRouter.class).toInstance(context.mock(IQRouter.class));
            bind(MessageRouter.class).toInstance(context.mock(MessageRouter.class));
            bind(PresenceRouter.class).toInstance(context.mock(PresenceRouter.class));
            bind(MulticastRouter.class).toInstance(context.mock(MulticastRouter.class));
            bind(UserManager.class).toInstance(context.mock(UserManager.class));
            bind(UpdateManager.class).toInstance(context.mock(UpdateManager.class));
            bind(AuditManager.class).toInstance(context.mock(AuditManager.class));
            bind(IQDiscoItemsHandler.class).toInstance(context.mock(IQDiscoItemsHandler.class));
            bind(PrivateStorage.class).toInstance(context.mock(PrivateStorage.class));
            bind(MultiUserChatServer.class).toInstance(context.mock(MultiUserChatServer.class));
            bind(AdHocCommandHandler.class).toInstance(context.mock(AdHocCommandHandler.class));
            bind(FileTransferProxy.class).toInstance(context.mock(FileTransferProxy.class));
            bind(FileTransferManager.class).toInstance(context.mock(FileTransferManager.class));
            bind(MediaProxyService.class).toInstance(context.mock(MediaProxyService.class));
            bind(STUNService.class).toInstance(context.mock(STUNService.class));
            bind(VCardManager.class).toInstance(context.mock(VCardManager.class));
            bind(GroupManager.class).toInstance(context.mock(GroupManager.class));
            bind(RemoteSessionLocator.class).toInstance(context.mock(RemoteSessionLocator.class));
            bind(ComponentManager.class).toInstance(context.mock(ComponentManager.class));
        }
    }
}
