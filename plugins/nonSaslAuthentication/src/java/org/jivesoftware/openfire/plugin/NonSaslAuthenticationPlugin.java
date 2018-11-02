package org.jivesoftware.openfire.plugin;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.handler.IQAuthHandler;

import java.io.File;

/**
 * An Openfire plugin that implements the obsolete Non-SASL Authentication plugin as specified in XEP-0078.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="http://xmpp.org/extensions/xep-0078.html">XEP-0078: Non-SASL Authentication</a>
 */
public class NonSaslAuthenticationPlugin implements Plugin
{
    private IQAuthHandler iqAuthHandler;

    @Override
    public void initializePlugin( PluginManager manager, File pluginDirectory )
    {
        iqAuthHandler = new IQAuthHandler();
        XMPPServer.getInstance().getIQRouter().addHandler( iqAuthHandler );
    }

    @Override
    public void destroyPlugin()
    {
        if ( iqAuthHandler != null )
        {
            XMPPServer.getInstance().getIQRouter().removeHandler( iqAuthHandler );
            iqAuthHandler = null;
        }
    }
}
