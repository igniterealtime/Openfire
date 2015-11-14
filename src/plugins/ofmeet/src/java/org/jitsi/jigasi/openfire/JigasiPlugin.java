/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jigasi.openfire;

import java.io.File;

import org.jivesoftware.util.*;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;

import org.slf4j.*;
import org.slf4j.Logger;

import org.xmpp.component.*;
import org.xmpp.packet.*;



/**
 * SIP gateway for Jitsi Videobridge conferences.
 */

public class JigasiPlugin
{
   /**
     * The Log.
     */
    private static final Logger Log = LoggerFactory.getLogger(JigasiPlugin.class);

    private CallControlComponent component;
    private ComponentManager componentManager;
    private String subdomain;
   	private File pluginDirectory;


    public void initializePlugin(ComponentManager componentManager, PluginManager manager, File pluginDirectory)
    {
		Log.info("JigasiPlugin - initializePlugin");

		this.pluginDirectory = pluginDirectory;
		this.componentManager = componentManager;

        String subdomain = "ofmeet-call-control";
        CallControlComponent component = new CallControlComponent(pluginDirectory);
        boolean added = false;

        try
        {
            componentManager.addComponent(subdomain, component);
            added = true;
        }
        catch (ComponentException ce)
        {
            ce.printStackTrace(System.err);
        }
        if (added)
        {
            this.componentManager = componentManager;
            this.subdomain = subdomain;
            this.component = component;
        }
        else
        {
            this.componentManager = null;
            this.subdomain = null;
            this.component = null;
        }
	}

    public void destroyPlugin()
    {
		Log.info("JigasiPlugin - destroyPlugin");

        if ((componentManager != null) && (subdomain != null))
        {
            try
            {
				component.stop();
                componentManager.removeComponent(subdomain);
            }
            catch (ComponentException ce)
            {
                // TODO Auto-generated method stub
            }
            componentManager = null;
            subdomain = null;
            component = null;
        }
	}
}
