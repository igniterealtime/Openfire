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
 * Bundle activator for {@link XmppProtocolProvider}.
 *
 * @author Pawel Domas
 */
public class XmppProtocolActivator
    implements BundleActivator
{
    private ServiceRegistration<?> focusRegistration;

    @Override
    public void start(BundleContext bundleContext)
        throws Exception
    {
        XmppProviderFactory focusFactory
            = new XmppProviderFactory(
                    bundleContext, ProtocolNames.JABBER);

        Hashtable<String, String> hashtable = new Hashtable<String, String>();

        // Register XMPP
        hashtable.put(ProtocolProviderFactory.PROTOCOL,
                      ProtocolNames.JABBER);

        focusRegistration = bundleContext.registerService(
            ProtocolProviderFactory.class.getName(),
            focusFactory,
            hashtable);
    }

    @Override
    public void stop(BundleContext bundleContext)
        throws Exception
    {
        if (focusRegistration != null)
            focusRegistration.unregister();
    }
}
