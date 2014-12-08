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

import java.util.*;

/**
 *
 */
public class OpSetSimpleCapsImpl
    implements OperationSetSimpleCaps
{
    /**
     * The logger.
     */
    private final static Logger logger
        = Logger.getLogger(OpSetSimpleCapsImpl.class);

    private final XmppProtocolProvider xmppProvider;

    public OpSetSimpleCapsImpl(XmppProtocolProvider xmppProtocolProvider)
    {
        this.xmppProvider = xmppProtocolProvider;
    }

    @Override
    public List<String> getItems(String node)
    {
        try
        {
            return xmppProvider.discoverItems(node);
        }
        catch (XMPPException e)
        {
            logger.error("Error while discovering the services of " + node, e);

            return null;
        }
    }

    @Override
    public boolean hasFeatureSupport(String node, String[] features)
    {
        return xmppProvider.checkFeatureSupport(
            node, features);
    }

    //@Override
    public boolean hasFeatureSupport(String node, String subnode,
                                     String[] features)
    {
        return xmppProvider.checkFeatureSupport(node, subnode, features);
    }
}
