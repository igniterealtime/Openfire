/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.protocol.xmpp;

import net.java.sip.communicator.util.*;
import org.jitsi.protocol.xmpp.*;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.*;

/**
 * Straightforward implementation of {@link OperationSetDirectSmackXmpp}
 * for {@link org.jitsi.impl.protocol.xmpp.XmppProtocolProvider}.
 *
 * @author Pawel Domas
 */
public class OpSetDirectSmackXmppImpl
    implements OperationSetDirectSmackXmpp
{
    /**
     *  The logger used by this class.
     */
    private final static Logger logger
        = Logger.getLogger(OpSetDirectSmackXmppImpl.class);

    /**
     * Parent protocol provider service.
     */
    private final XmppProtocolProvider xmppProvider;

    /**
     * Creates new instance of <tt>OpSetDirectSmackXmppImpl</tt>.
     *
     * @param xmppProvider parent {@link XmppProtocolProvider}.
     */
    public OpSetDirectSmackXmppImpl(XmppProtocolProvider xmppProvider)
    {
        this.xmppProvider = xmppProvider;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XmppConnection getXmppConnection()
    {
        return xmppProvider.getConnectionAdapter();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addPacketHandler(PacketListener listener, PacketFilter filter)
    {
        XMPPConnection connection = xmppProvider.getConnection();
        if (connection != null)
        {
            connection.addPacketListener(listener, filter);
        }
        else
        {
            logger.error("Failed to add packet handler: "
                             + listener + " - no valid connection object");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removePacketHandler(PacketListener listener)
    {
        XMPPConnection connection = xmppProvider.getConnection();
        if (connection != null)
        {
            xmppProvider.getConnection().removePacketListener(listener);
        }
        else
        {
            logger.error("Failed to remove packet handler: "
                             + listener + " - no valid connection object");
        }
    }
}
