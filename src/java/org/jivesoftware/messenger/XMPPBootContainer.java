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

import org.jivesoftware.messenger.container.spi.BootstrapContainer;
import org.jivesoftware.messenger.disco.IQDiscoInfoHandler;
import org.jivesoftware.messenger.disco.IQDiscoItemsHandler;
import org.jivesoftware.messenger.muc.spi.MultiUserChatServerImpl;
import org.jivesoftware.messenger.audit.spi.AuditManagerImpl;
import org.jivesoftware.messenger.auth.spi.GroupManagerImpl;
import org.jivesoftware.messenger.chatbot.spi.ChatbotManagerImpl;
import org.jivesoftware.messenger.handler.*;
import org.jivesoftware.messenger.spi.*;
import org.jivesoftware.messenger.transport.TransportHandler;
import org.jivesoftware.messenger.user.spi.*;

/**
 * <p>A bootstrap container to launch the Messenger XMPP server.</p>
 * <p/>
 * <p>This container knows what classes must be loaded to create a
 * functional Messenger deployment.</p>
 *
 * @author Iain Shigeoka
 */
public class XMPPBootContainer extends BootstrapContainer {

    protected String[] getSetupModuleNames() {
        return new String[]{BasicServer.class.getName()};
    }

    protected String[] getBootModuleNames() {
        return new String[]{
            BasicServer.class.getName(),
            RoutingTableImpl.class.getName(),
            NameIDManagerImpl.class.getName(),
            AuditManagerImpl.class.getName(),
            ChatbotManagerImpl.class.getName(),
            UserManagerImpl.class.getName(),
            RosterManagerImpl.class.getName(),
            DbPrivateStore.class.getName()};
    }

    protected String[] getCoreModuleNames() {
        return new String[]{
            GroupManagerImpl.class.getName(),
            ConnectionManagerImpl.class.getName(),
            PresenceManagerImpl.class.getName(),
            SessionManagerImpl.class.getName(),
            PacketRouterImpl.class.getName(),

            IQRouterImpl.class.getName(),
            MessageRouterImpl.class.getName(),
            PresenceRouterImpl.class.getName(),
            PacketFactoryImpl.class.getName(),

            PacketTransporterImpl.class.getName(),
            PacketDelivererImpl.class.getName(),
            TransportHandler.class.getName(),
            OfflineMessageStrategyImpl.class.getName(),
            DbOfflineMessageStore.class.getName()};
    }

    protected String[] getStandardModuleNames() {
        return new String[]{
            IQAuthHandler.class.getName(),
            IQPrivateHandler.class.getName(),
            IQRegisterHandler.class.getName(),
            IQRosterHandler.class.getName(),
            IQTimeHandler.class.getName(),
            IQvCardHandler.class.getName(),
            IQVersionHandler.class.getName(),
            PresenceSubscribeHandler.class.getName(),
            PresenceUpdateHandler.class.getName(),

            IQDiscoInfoHandler.class.getName(),
            IQDiscoItemsHandler.class.getName(),
            MultiUserChatServerImpl.class.getName()};
    }
}