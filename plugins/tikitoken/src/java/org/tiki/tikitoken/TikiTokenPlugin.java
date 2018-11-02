package org.tiki.tikitoken;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.net.SASLAuthentication;

import java.io.File;
import java.security.Security;

/**
 * An Openfire plugin that adds the TikiToken SASL mechanism.
 */
public class TikiTokenPlugin implements Plugin
{
    @Override
    public void initializePlugin( PluginManager manager, File pluginDirectory )
    {
        Security.addProvider( new TikiTokenSaslProvider() );
        SASLAuthentication.addSupportedMechanism( TikiTokenSaslServer.MECHANISM_NAME );
    }

    @Override
    public void destroyPlugin()
    {
        SASLAuthentication.removeSupportedMechanism( TikiTokenSaslServer.MECHANISM_NAME );
        Security.removeProvider( TikiTokenSaslProvider.NAME );
    }
}
