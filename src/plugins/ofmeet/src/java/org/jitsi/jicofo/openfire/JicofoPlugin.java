/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.openfire;

import java.io.File;

import org.jivesoftware.util.*;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.XMPPServer;

import org.jitsi.jicofo.*;
import org.slf4j.*;
import org.slf4j.Logger;

import org.xmpp.component.*;
import org.xmpp.packet.*;


/**
 * SIP gateway for Jitsi Videobridge conferences.
 */

public class JicofoPlugin
{
   /**
     * The Log.
     */
    private static final Logger Log = LoggerFactory.getLogger(JicofoPlugin.class);

    private FocusComponent component;
    private ComponentManager componentManager;
    private String subdomain;
   	private File pluginDirectory;


    public void initializePlugin(ComponentManager componentManager, PluginManager manager, File pluginDirectory)
    {
		boolean added = false;
		this.componentManager = componentManager;

        try
        {
			Log.info("JicofoPlugin - initializePlugin");

			this.pluginDirectory = pluginDirectory;

			String hostName = JiveGlobals.getProperty("org.jitsi.videobridge.nat.harvester.public.address", XMPPServer.getInstance().getServerInfo().getHostname());
			String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
			String focusUserJid = JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.focus.user.jid", "focus@"+domain);
			String focusUserPassword = JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.focus.user.password", null);

			if (focusUserPassword != null)
			{
				Log.info("JicofoPlugin - using focus " + focusUserJid + ":" + hostName);

				System.setProperty("org.jitsi.videobridge.ofmeet.audio.mixer", JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.audio.mixer", "false"));
				System.setProperty("org.jitsi.videobridge.ofmeet.sip.enabled", JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.sip.enabled", "false"));

				System.setProperty("net.java.sip.communicator.service.gui.ALWAYS_TRUST_MODE_ENABLED",  "true");
				System.setProperty(FocusManager.HOSTNAME_PNAME, hostName);
				System.setProperty(FocusManager.XMPP_DOMAIN_PNAME, domain);
				System.setProperty(FocusManager.FOCUS_USER_DOMAIN_PNAME, domain);
				System.setProperty(FocusManager.FOCUS_USER_NAME_PNAME, (new JID(focusUserJid)).getNode());
				System.setProperty(FocusManager.FOCUS_USER_PASSWORD_PNAME, focusUserPassword);

				String subdomain = "ofmeet-focus";
				FocusComponent component = new FocusComponent(false);
				componentManager.addComponent(subdomain, component);
				added = true;

			} else {
				Log.error("Focus user not setup. password missing " + focusUserJid);
			}
        }
        catch (Exception ce)
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
		Log.info("JicofoPlugin - destroyPlugin");

        if ((componentManager != null) && (subdomain != null))
        {
            try
            {
				component.shutdown();
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
