/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.protocol.xmpp;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.util.*;

import org.jitsi.protocol.xmpp.*;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.*;
import org.jivesoftware.smack.packet.*;

/**
 * Implementation of {@link OperationSetJingleImpl} for
 * {@link XmppProtocolProvider}.
 *
 * @author Pawel Domas
 */
class OperationSetJingleImpl
    extends AbstractOperationSetJingle
    implements PacketFilter,
               PacketListener
{
    /**
     * The logger used by this class.
     */
    private final static Logger logger
            = Logger.getLogger(OperationSetJingleImpl.class);

    /**
     * Parent {@link XmppProtocolProvider}.
     */
    private final XmppProtocolProvider xmppProvider;

    /**
     * Creates new instance of <tt>OperationSetJingleImpl</tt>.
     *
     * @param xmppProvider parent XMPP protocol provider
     */
    OperationSetJingleImpl(XmppProtocolProvider xmppProvider)
    {
        this.xmppProvider = xmppProvider;
    }

    /**
     * Initializes this instance and binds packets processor.
     */
    public void initialize()
    {
        xmppProvider.getConnection().addPacketListener(this, this);
    }

    /**
     * Returns our XMPP address that will be used as 'from' attribute
     * in Jingle QIs.
     */
    protected String getOurJID()
    {
        return xmppProvider.getOurJid();
    }

    /**
     * {@inheritDoc}
     */
    protected XmppConnection getConnection()
    {
        return xmppProvider.getConnectionAdapter();
    }

    /**
     * Packets filter implementation.
     *
     * {@inheritDoc}
     */
    public boolean accept(Packet packet)
    {
        try
        {
            // We handle JingleIQ and SessionIQ.
            if (!(packet instanceof JingleIQ))
            {
                // FIXME: find session for packet ID to make sure
                // that the error belongs to this class
                String packetID = packet.getPacketID();
                XMPPError error = packet.getError();
                if (error != null)
                {
                    String errorMessage = error.getMessage();

                    logger.error(
                        "Received an error: code=" + error.getCode()
                            + " message=" + errorMessage);
                }
            }

            return packet instanceof JingleIQ
                && getSession(((JingleIQ) packet).getSID()) != null;
        }
        catch(Throwable t)
        {
            logger.error(t, t);
            return false;
        }
    }

    /**
     * FIXME: this method can go to abstract class.
     *
     * {@inheritDoc}
     */
    public void processPacket(Packet packet)
    {
        IQ iq = (IQ) packet;

        //first ack all "set" requests.
        if(iq.getType() == IQ.Type.SET)
        {
            IQ ack = IQ.createResultIQ(iq);

            getConnection().sendPacket(ack);
        }

        try
        {
            if (iq instanceof JingleIQ)
                processJingleIQ((JingleIQ) iq);
        }
        catch(Throwable t)
        {
            if (logger.isInfoEnabled())
            {
                String packetClass;

                if (iq instanceof JingleIQ)
                    packetClass = "Jingle";
                else
                    packetClass = packet.getClass().getSimpleName();

                logger.info(
                    "Error while handling incoming " + packetClass
                        + " packet: ",
                    t);
            }

            /*
             * The Javadoc on ThreadDeath says: If ThreadDeath is caught by
             * a method, it is important that it be rethrown so that the
             * thread actually dies.
             */
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
        }
    }
}
