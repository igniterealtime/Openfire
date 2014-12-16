/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.protocol.xmpp;

import net.java.sip.communicator.service.protocol.*;

import org.osgi.framework.*;

import java.util.*;

/**
 * Protocol provider factory implementation ofr {@link XmppProtocolProvider}.
 *
 * @author Pawel Domas
 */
public class XmppProviderFactory
    extends ProtocolProviderFactory
{
    /**
     * Creates a new <tt>ProtocolProviderFactory</tt>.
     *
     * @param bundleContext the bundle context reference of the service
     * @param protocolName  the name of the protocol
     */
    protected XmppProviderFactory(
            BundleContext bundleContext,
            String protocolName)
    {
        super(bundleContext, protocolName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AccountID installAccount(String s,
                                    Map<String, String> stringStringMap)
        throws IllegalArgumentException, IllegalStateException,
               NullPointerException
    {
        throw new RuntimeException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void modifyAccount(ProtocolProviderService protocolProviderService,
                              Map<String, String> stringStringMap)
        throws NullPointerException
    {
        throw new RuntimeException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected AccountID createAccountID(String userId,
                                        Map<String, String> accountProperties)
    {
        return new XmppAccountID(userId, accountProperties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ProtocolProviderService createService(String userID,
                                                    AccountID accountID)
    {
        return new XmppProtocolProvider(accountID);
    }
}
