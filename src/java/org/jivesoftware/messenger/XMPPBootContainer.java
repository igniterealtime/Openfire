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

import org.jivesoftware.messenger.audit.spi.AuditManagerImpl;
import org.jivesoftware.messenger.container.spi.BootstrapContainer;
import org.jivesoftware.messenger.disco.IQDiscoInfoHandler;
import org.jivesoftware.messenger.disco.IQDiscoItemsHandler;
import org.jivesoftware.messenger.handler.IQAuthHandler;
import org.jivesoftware.messenger.handler.IQPrivateHandler;
import org.jivesoftware.messenger.handler.IQRegisterHandler;
import org.jivesoftware.messenger.handler.IQRosterHandler;
import org.jivesoftware.messenger.handler.IQTimeHandler;
import org.jivesoftware.messenger.handler.IQVersionHandler;
import org.jivesoftware.messenger.handler.IQvCardHandler;
import org.jivesoftware.messenger.handler.PresenceSubscribeHandler;
import org.jivesoftware.messenger.handler.PresenceUpdateHandler;
import org.jivesoftware.messenger.muc.spi.MultiUserChatServerImpl;
import org.jivesoftware.messenger.spi.BasicServer;
import org.jivesoftware.messenger.spi.ConnectionManagerImpl;
import org.jivesoftware.messenger.spi.IQRouterImpl;
import org.jivesoftware.messenger.spi.MessageRouterImpl;
import org.jivesoftware.messenger.spi.PacketDelivererImpl;
import org.jivesoftware.messenger.spi.PacketRouterImpl;
import org.jivesoftware.messenger.spi.PacketTransporterImpl;
import org.jivesoftware.messenger.spi.PresenceManagerImpl;
import org.jivesoftware.messenger.spi.PresenceRouterImpl;
import org.jivesoftware.messenger.spi.RoutingTableImpl;
import org.jivesoftware.messenger.transport.TransportHandler;
import org.jivesoftware.messenger.user.spi.RosterManagerImpl;
import org.jivesoftware.messenger.user.spi.UserManagerImpl;

/**
 * A bootstrap container to launch the Messenger XMPP server. This
 * container knows what classes must be loaded to create a functional
 * Jive Messenger deployment.
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
            AuditManagerImpl.class.getName(),
            UserManagerImpl.class.getName(),
            RosterManagerImpl.class.getName(),
            PrivateStorage.class.getName()};
    }

    protected String[] getCoreModuleNames() {
        return new String[]{
            ConnectionManagerImpl.class.getName(),
            PresenceManagerImpl.class.getName(),
            PacketRouterImpl.class.getName(),

            IQRouterImpl.class.getName(),
            MessageRouterImpl.class.getName(),
            PresenceRouterImpl.class.getName(),

            PacketTransporterImpl.class.getName(),
            PacketDelivererImpl.class.getName(),
            TransportHandler.class.getName(),
            OfflineMessageStrategy.class.getName()};
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