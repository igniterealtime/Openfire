package org.igniterealtime.openfire.plugin.avatarresizer;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.vcard.VCardManager;
import org.jivesoftware.openfire.vcard.VCardProvider;
import org.jivesoftware.util.JiveGlobals;

import java.io.File;

/**
 * A plugin that intercepts avatars in vCards retrieved from the vCardManager, and re-sizes them when appropriate.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class AvatarResizerPlugin implements Plugin
{
    @Override
    public void initializePlugin( PluginManager manager, File pluginDirectory )
    {
        final VCardProvider provider = VCardManager.getProvider();
        if ( provider != null && !( provider instanceof DelegateVCardProvider ) )
        {
            // Setting the property will cause the VCardProvider to re-initialize.
            JiveGlobals.setProperty( "provider.vcard.className", DelegateVCardProvider.class.getCanonicalName() );

            final DelegateVCardProvider delegateVCardProvider = (DelegateVCardProvider) VCardManager.getProvider();
            delegateVCardProvider.setDelegate( provider );
        }
    }

    @Override
    public void destroyPlugin()
    {
        final VCardProvider provider = VCardManager.getProvider();
        if ( provider != null && provider instanceof DelegateVCardProvider )
        {
            final DelegateVCardProvider delegateVCardProvider = (DelegateVCardProvider) provider;
            final VCardProvider originalProvider = delegateVCardProvider.getDelegate();
            JiveGlobals.setProperty( "provider.vcard.className", originalProvider.getClass().getCanonicalName() );
        }
    }
}
