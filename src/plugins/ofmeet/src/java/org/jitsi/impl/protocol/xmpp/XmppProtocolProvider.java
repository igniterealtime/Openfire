/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.protocol.xmpp;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.jabber.*;

import org.jitsi.protocol.xmpp.*;
import org.jitsi.util.*;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smackx.packet.*;

import java.util.*;

/**
 * XMPP protocol provider service used by Jitsi Meet focus to create anonymous
 * accounts. Implemented with Smack.
 *
 * @author Pawel Domas
 */
public class XmppProtocolProvider
    extends AbstractProtocolProviderService
{
    /**
     * The logger used by this class.
     */
    private final static Logger logger
        = Logger.getLogger(XmppProtocolProvider.class);

    /**
     * Active account.
     */
    private final JabberAccountID jabberAccountID;

    /**
     * Jingle operation set.
     */
    private final OperationSetJingleImpl jingleOpSet;

    /**
     * Current registration state.
     */
    private RegistrationState registrationState
            = RegistrationState.UNREGISTERED;

    /**
     * The XMPP connection used by this instance.
     */
    private XMPPConnection connection;

    /**
     * Colibri operation set.
     */
    private OperationSetColibriConferenceImpl colibriTools
        = new OperationSetColibriConferenceImpl();

    /**
     * Smack connection adapter to {@link XmppConnection} used by this instance.
     */
    private XmppConnectionAdapter connectionAdapter;

    /**
     * Jitsi service discovery manager.
     */
    private ScServiceDiscoveryManager discoInfoManager;

    /**
     * Creates new instance of {@link XmppProtocolProvider} for given AccountID.
     *
     * @param accountID the <tt>JabberAccountID</tt> that will be used by new
     *                  instance.
     */
    public XmppProtocolProvider(AccountID accountID)
    {
        this.jabberAccountID = (JabberAccountID) accountID;

        addSupportedOperationSet(
            OperationSetColibriConference.class, colibriTools);

        this.jingleOpSet = new OperationSetJingleImpl(this);
        addSupportedOperationSet(
            OperationSetJingle.class, jingleOpSet);

        addSupportedOperationSet(
            OperationSetMultiUserChat.class,
            new OperationSetMultiUserChatImpl(this));

        addSupportedOperationSet(
            OperationSetJitsiMeetTools.class,
            new OperationSetMeetToolsImpl());

        addSupportedOperationSet(
            OperationSetSimpleCaps.class,
            new OpSetSimpleCapsImpl(this));

        addSupportedOperationSet(
            OperationSetDirectSmackXmpp.class,
            new OpSetDirectSmackXmppImpl(this));

        addSupportedOperationSet(
            OperationSetSubscription.class,
            new OpSetSubscriptionImpl(this));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void register(SecurityAuthority securityAuthority)
        throws OperationFailedException
    {
        String serviceName
            = org.jivesoftware.smack.util.StringUtils.parseServer(
                    getAccountID().getUserID());

        String serverAddressUserSetting
            = jabberAccountID.getServerAddress();

        int serverPort
            = getAccountID().getAccountPropertyInt(
                    ProtocolProviderFactory.SERVER_PORT, 5222);

        ConnectionConfiguration connConfig
            = new ConnectionConfiguration(
                    serverAddressUserSetting, serverPort, serviceName);

        connection = new XMPPConnection(connConfig);

        try
        {
            connection.connect();

            if (jabberAccountID.isAnonymousAuthUsed())
            {
                connection.loginAnonymously();
            }
            else
            {
                String login = jabberAccountID.getAuthorizationName();
                String pass = jabberAccountID.getPassword();
                String resource = jabberAccountID.getResource();
                connection.login(login, pass, resource);
            }
        }
        catch (XMPPException e)
        {
            throw new OperationFailedException(
                "Failed to connect",
                OperationFailedException.GENERAL_ERROR, e);
        }

        colibriTools.initialize(getConnectionAdapter());

        jingleOpSet.initialize();

        discoInfoManager = new ScServiceDiscoveryManager(
            this, connection,
            new String[]{}, new String[]{}, false);

        registrationState = RegistrationState.REGISTERED;

        fireRegistrationStateChanged(
            RegistrationState.UNREGISTERED,
            RegistrationState.REGISTERED,
            RegistrationStateChangeEvent.REASON_NOT_SPECIFIED,
            null);

        logger.info("XMPP provider " + jabberAccountID + " connected (JID: "
                        + connection.getUser() + ")");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void unregister()
        throws OperationFailedException
    {
        if (connection != null)
        {
            connection.disconnect();

            logger.info(
                "XMPP provider "
                    + jabberAccountID + " disconnected");

            RegistrationState prevState = registrationState;

            registrationState = RegistrationState.UNREGISTERED;

            fireRegistrationStateChanged(
                prevState,
                RegistrationState.UNREGISTERED,
                RegistrationStateChangeEvent.REASON_NOT_SPECIFIED,
                null);

            connection = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RegistrationState getRegistrationState()
    {
        return registrationState;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProtocolName()
    {
        return ProtocolNames.JABBER;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProtocolIcon getProtocolIcon()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown()
    {
        if (connection != null)
        {
            try
            {
                unregister();
            }
            catch (OperationFailedException e)
            {
                logger.error(e.getMessage(), e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AccountID getAccountID()
    {
        return jabberAccountID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSignalingTransportSecure()
    {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TransportProtocol getTransportProtocol()
    {
        return TransportProtocol.UNKNOWN;
    }

    /**
     * Returns implementation of {@link org.jitsi.protocol.xmpp.XmppConnection}.
     */
    public XMPPConnection getConnection()
    {
        return connection;
    }

    /**
     * Returns our JID if we're connected or <tt>null</tt> otherwise.
     */
    public String getOurJid()
    {
        return connection != null ? connection.getUser() : null;
    }

    /**
     * Lazy initializer for {@link #connectionAdapter}.
     *
     * @return {@link XmppConnection} provided by this instance.
     */
    XmppConnection getConnectionAdapter()
    {
        if (connectionAdapter == null)
        {
            connectionAdapter
                = new XmppConnectionAdapter(connection);
        }
        return connectionAdapter;
    }

    /**
     * FIXME: move to operation set together with ScServiceDiscoveryManager
     */
    public boolean checkFeatureSupport(String contactAddress, String[] features)
    {
        try
        {
            //FIXME: fix logging levels
            logger.debug("Discovering info for: " + contactAddress);

            DiscoverInfo info = discoInfoManager.discoverInfo(contactAddress);

            logger.debug("HAVE Discovering info for: " + contactAddress);

            logger.debug("Features");
            Iterator<DiscoverInfo.Feature> featuresList = info.getFeatures();
            while (featuresList.hasNext())
            {
                DiscoverInfo.Feature f = featuresList.next();
                logger.debug(f.toXML());
            }

            logger.debug("Identities");
            Iterator<DiscoverInfo.Identity> identities = info.getIdentities();
            while (identities.hasNext())
            {
                DiscoverInfo.Identity identity = identities.next();
                logger.debug(identity.toXML());
            }
        }
        catch (XMPPException e)
        {
            logger.error("Error discovering features", e);
        }

        for (String feature : features)
        {
            if (!discoInfoManager.supportsFeature(contactAddress, feature))
            {
                return false;
            }
        }
        return true;
    }

    public boolean checkFeatureSupport(String node, String subnode,
                                       String[] features)
    {
        try
        {
            //FIXME: fix logging levels
            logger.info("Discovering info for: " + node + " subnode: " + subnode);

            DiscoverInfo info = discoInfoManager.discoverInfo(node, subnode);

            logger.info("Features");
            Iterator<DiscoverInfo.Feature> featuresList = info.getFeatures();
            while (featuresList.hasNext())
            {
                DiscoverInfo.Feature f = featuresList.next();
                logger.info(f.toXML());
            }

            logger.info("Identities");
            Iterator<DiscoverInfo.Identity> identities = info.getIdentities();
            while (identities.hasNext())
            {
                DiscoverInfo.Identity identity = identities.next();
                logger.info(identity.toXML());
            }
        }
        catch (XMPPException e)
        {
            logger.error(e, e);
        }

        for (String feature : features)
        {
            if (!discoInfoManager.supportsFeature(node, feature))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * FIXME: move to operation set together with ScServiceDiscoveryManager
     */
    public List<String> discoverItems(String node)
        throws XMPPException
    {
        DiscoverItems itemsDisco = discoInfoManager.discoverItems(node);

        //FIXME: fix logging levels
        logger.info("HAVE Discovered items for: " + node);

        ArrayList<String> result = new ArrayList<String>();

        Iterator<DiscoverItems.Item> items = itemsDisco.getItems();
        while (items.hasNext())
        {
            DiscoverItems.Item item = items.next();
            logger.info(item.toXML());
            if (item.getNode() != null && item.getEntityID().equals(node))
            {
                // Subnode
                result.add(item.getNode());
            }
            else
            {
                result.add(item.getEntityID());
            }
        }

        return result;
    }

    /**
     * Implements {@link XmppConnection}.
     */
    class XmppConnectionAdapter
        implements XmppConnection
    {
        private final XMPPConnection connection;

        XmppConnectionAdapter(XMPPConnection connection)
        {
            this.connection = connection;
        }

        @Override
        public void sendPacket(Packet packet)
        {
            connection.sendPacket(packet);
        }

        @Override
        public Packet sendPacketAndGetReply(Packet packet)
        {
            PacketCollector packetCollector
                = connection.createPacketCollector(
                        new PacketIDFilter(packet.getPacketID()));

            connection.sendPacket(packet);

            //FIXME: retry allocation on timeout
            Packet response = packetCollector.nextResult(60000);	// BAO

            packetCollector.cancel();

            return response;
        }
    }
}
