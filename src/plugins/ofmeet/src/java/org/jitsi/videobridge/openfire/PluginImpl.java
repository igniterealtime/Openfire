/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.videobridge.openfire;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;

import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;
import org.jitsi.videobridge.xmpp.*;
import org.jivesoftware.openfire.container.*;
import org.jivesoftware.util.*;
import org.slf4j.*;
import org.slf4j.Logger;
import org.xmpp.component.*;

/**
 * Implements <tt>org.jivesoftware.openfire.container.Plugin</tt> to integrate
 * Jitsi Video Bridge into Openfire.
 *
 * @author Lyubomir Marinov
 * @author Damian Minkov
 */
public class PluginImpl
    implements Plugin,
               PropertyEventListener
{
    /**
     * The logger.
     */
    private static final Logger Log = LoggerFactory.getLogger(PluginImpl.class);

    /**
     * The name of the property that contains the maximum port number that we'd
     * like our RTP managers to bind upon.
     */
    public static final String MAX_PORT_NUMBER_PROPERTY_NAME
        = "org.jitsi.videobridge.media.MAX_PORT_NUMBER";

    /**
     * The name of the property that contains the minimum port number that we'd
     * like our RTP managers to bind upon.
     */
    public static final String MIN_PORT_NUMBER_PROPERTY_NAME
        = "org.jitsi.videobridge.media.MIN_PORT_NUMBER";

    /**
     * The minimum port number default value.
     */
    public static final int MIN_PORT_DEFAULT_VALUE = 5000;

    /**
     * The maximum port number default value.
     */
    public static final int MAX_PORT_DEFAULT_VALUE = 6000;

    /**
     * The name of the property that contains the name of video conference application
     */
    public static final String CHECKREPLAY_PROPERTY_NAME = "org.jitsi.videobridge.video.srtpcryptocontext.checkreplay";

    /**
     * The name of the property that contains the name of video conference application
     */
    public static final String NAT_HARVESTER_LOCAL_ADDRESS = "org.jitsi.videobridge.nat.harvester.local.address";
    /**
     * The name of the property that contains the name of video conference application
     */
    public static final String NAT_HARVESTER_PUBLIC_ADDRESS = "org.jitsi.videobridge.nat.harvester.public.address";

    /**
     * The name of the property that contains the maximum port number that we'd
     * like our RTP managers to bind upon.
     */
    public static final String RECORD_PROPERTY_NAME = "org.jitsi.videobridge.ofmeet.media.record";


    /**
     * The Jabber component which has been added to {@link #componentManager}
     * i.e. Openfire.
     */
    public static ComponentImpl component;

    /**
     * The <tt>ComponentManager</tt> to which the {@link #component} of this
     * <tt>Plugin</tt> has been added.
     */
    private ComponentManager componentManager;

    /**
     * The subdomain of the address of {@link #component} with which it has been
     * added to {@link #componentManager}.
     */
    private String subdomain;

    /**
     * Destroys this <tt>Plugin</tt> i.e. releases the resources acquired by
     * this <tt>Plugin</tt> throughout its life up until now and prepares it for
     * garbage collection.
     *
     * @see Plugin#destroyPlugin()
     */
    public void destroyPlugin()
    {
		Log.info("PluginImpl destroyPlugin");

        PropertyEventDispatcher.removeListener(this);

        if ((componentManager != null) && (subdomain != null))
        {
            try
            {
                componentManager.removeComponent(subdomain);
            }
            catch (ComponentException ce)
            {
                // TODO Auto-generated method stub
            }
            componentManager = null;
            subdomain = null;
        }
    }

    /**
     * Initializes this <tt>Plugin</tt>.
     *
     * @param manager the <tt>PluginManager</tt> which loads and manages this
     * <tt>Plugin</tt>
     * @param pluginDirectory the directory into which this <tt>Plugin</tt> is
     * located
     * @see Plugin#initializePlugin(PluginManager, File)
     */
    public void initializePlugin(PluginManager manager, File pluginDirectory)
    {
		Log.info("PluginImpl initializePlugin "+ pluginDirectory);

		String enableRecording = JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.media.record", "false");
		String recordingPath = JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.recording.path", pluginDirectory.getAbsolutePath() + File.separator + "recordings");
		String recordingSecret = JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.recording.secret", "secret");

		String localAddress = JiveGlobals.getProperty(NAT_HARVESTER_LOCAL_ADDRESS, null);
		String publicAddress = JiveGlobals.getProperty(NAT_HARVESTER_PUBLIC_ADDRESS, null);

		System.setProperty("net.java.sip.communicator.SC_HOME_DIR_LOCATION", pluginDirectory.getPath());
		System.setProperty("net.java.sip.communicator.SC_HOME_DIR_NAME", ".");
		System.setProperty("org.jitsi.impl.neomedia.transform.srtp.SRTPCryptoContext.checkReplay", JiveGlobals.getProperty(CHECKREPLAY_PROPERTY_NAME, "false"));

		System.setProperty("org.jitsi.videobridge.ENABLE_MEDIA_RECORDING", enableRecording);
		System.setProperty("org.jitsi.videobridge.MEDIA_RECORDING_PATH", recordingPath);
		System.setProperty("org.jitsi.videobridge.MEDIA_RECORDING_TOKEN", recordingSecret);


		if (localAddress != null && !"".equals(localAddress))
		{
			System.setProperty("org.jitsi.videobridge.NAT_HARVESTER_LOCAL_ADDRESS", localAddress);
		}

		if (publicAddress != null && !"".equals(publicAddress))
		{
			System.setProperty("org.jitsi.videobridge.NAT_HARVESTER_PUBLIC_ADDRESS", publicAddress);
		}

        PropertyEventDispatcher.addListener(this);

        // Let's check for custom configuration
        String maxVal = JiveGlobals.getProperty(MAX_PORT_NUMBER_PROPERTY_NAME);
        String minVal = JiveGlobals.getProperty(MIN_PORT_NUMBER_PROPERTY_NAME);

        if(maxVal != null)
            setIntProperty(
                DefaultStreamConnector.MAX_PORT_NUMBER_PROPERTY_NAME,
                maxVal);
        if(minVal != null)
            setIntProperty(
                DefaultStreamConnector.MIN_PORT_NUMBER_PROPERTY_NAME,
                minVal);

        checkNatives(pluginDirectory);

        ComponentManager componentManager
            = ComponentManagerFactory.getComponentManager();
        String subdomain = "ofmeet-jitsi-videobridge"; //ComponentImpl.SUBDOMAIN;
        PluginImpl.component = new ComponentImpl();
        boolean added = false;

        try
        {
            componentManager.addComponent(subdomain, PluginImpl.component);
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
        }
        else
        {
            this.componentManager = null;
            this.subdomain = null;
        }
    }

    /**
     * Checks whether we have folder with extracted natives, if missing
     * find the appropriate jar file and extract them. Normally this is
     * done once when plugin is installed or updated.
     * If folder with natives exist add it to the java.library.path so
     * libjitsi can use those native libs.
     */
    private void checkNatives(File pluginDirectory)
    {
        // Find the root path of the class that will be our plugin lib folder.
        try
        {
			String nativeLibsJarPath = pluginDirectory.getAbsolutePath() + File.separator + "lib";
            File nativeLibFolder = new File(nativeLibsJarPath, "native");

            if(!nativeLibFolder.exists())
            {
                nativeLibFolder.mkdirs();

                // lets find the appropriate jar file to extract and
                // extract it
                String jarFileSuffix = null;
                if(OSUtils.IS_LINUX32)
                {
                    jarFileSuffix = "jitsi-videobridge-native-linux-32.jar";
                }
                else if(OSUtils.IS_LINUX64)
                {
                    jarFileSuffix = "jitsi-videobridge-native-linux-64.jar";
                }
                else if(OSUtils.IS_WINDOWS32)
                {
                    jarFileSuffix = "jitsi-videobridge-native-windows-32.jar";
                }
                else if(OSUtils.IS_WINDOWS64)
                {
                    jarFileSuffix = "jitsi-videobridge-native-windows-64.jar";
                }
                else if(OSUtils.IS_MAC)
                {
                    jarFileSuffix = "jitsi-videobridge-native-macosx.jar";
                }

                JarFile jar = new JarFile(nativeLibsJarPath + File.separator + jarFileSuffix);
                Enumeration en = jar.entries();

                while (en.hasMoreElements())
                {
                    try
                    {
                        JarEntry file = (JarEntry) en.nextElement();
                        File f = new File(nativeLibFolder, file.getName());
                        if (file.isDirectory())
                        {
                            continue;
                        }

                        InputStream is = jar.getInputStream(file);
                        FileOutputStream fos = new FileOutputStream(f);
                        while (is.available() > 0)
                        {
                            fos.write(is.read());
                        }
                        fos.close();
                        is.close();
                    }
                    catch(Throwable t)
                    {}
                }

                Log.info("Native lib folder created and natives extracted");
            }
            else
                Log.info("Native lib folder already exist.");


            String newLibPath = nativeLibFolder.getCanonicalPath() + File.pathSeparator + System.getProperty("java.library.path");
            System.setProperty("java.library.path", newLibPath);

            // this will reload the new setting
            Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
            fieldSysPath.setAccessible(true);
            fieldSysPath.set(System.class.getClassLoader(), null);
        }
        catch (Exception e)
        {
            Log.error(e.getMessage(), e);
        }
    }

    /**
     * Returns the value of max port if set or the default one.
     * @return the value of max port if set or the default one.
     */
    public String getMaxPort()
    {
        String val = System.getProperty(
            DefaultStreamConnector.MAX_PORT_NUMBER_PROPERTY_NAME);

        if(val != null)
            return val;
        else
            return String.valueOf(MAX_PORT_DEFAULT_VALUE);
    }

    /**
     * Returns the value of min port if set or the default one.
     * @return the value of min port if set or the default one.
     */
    public String getMinPort()
    {
        String val = System.getProperty(
            DefaultStreamConnector.MIN_PORT_NUMBER_PROPERTY_NAME);

        if(val != null)
            return val;
        else
            return String.valueOf(MIN_PORT_DEFAULT_VALUE);
    }

    /**
     * A property was set. The parameter map <tt>params</tt> will contain the
     * the value of the property under the key <tt>value</tt>.
     *
     * @param property the name of the property.
     * @param params event parameters.
     */
    public void propertySet(String property, Map params)
    {
        if(property.equals(MAX_PORT_NUMBER_PROPERTY_NAME))
        {
            setIntProperty(
                DefaultStreamConnector.MAX_PORT_NUMBER_PROPERTY_NAME,
                (String)params.get("value"));
        }
        else if(property.equals(MIN_PORT_NUMBER_PROPERTY_NAME))
        {
            setIntProperty(
                DefaultStreamConnector.MIN_PORT_NUMBER_PROPERTY_NAME,
                (String)params.get("value"));
        }
    }

    /**
     * Sets int property.
     * @param property the property name.
     * @param value the value to change.
     */
    private void setIntProperty(String property, String value)
    {
        try
        {
            // let's just check that value is integer
            int port = Integer.valueOf(value);

            if(port >= 1 && port <= 65535)
                System.setProperty(property, value);
        }
        catch(NumberFormatException ex)
        {
            Log.error("Error setting port", ex);
        }
    }

    /**
     * A property was deleted.
     *
     * @param property the name of the property deleted.
     * @param params event parameters.
     */
    public void propertyDeleted(String property, Map params)
    {
        if(property.equals(MAX_PORT_NUMBER_PROPERTY_NAME))
        {
            System.setProperty(
                DefaultStreamConnector.MAX_PORT_NUMBER_PROPERTY_NAME,
                String.valueOf(MAX_PORT_DEFAULT_VALUE));
        }
        else if(property.equals(MIN_PORT_NUMBER_PROPERTY_NAME))
        {
            System.setProperty(
                DefaultStreamConnector.MIN_PORT_NUMBER_PROPERTY_NAME,
                String.valueOf(MIN_PORT_DEFAULT_VALUE));
        }
    }

    /**
     * An XML property was set. The parameter map <tt>params</tt> will contain the
     * the value of the property under the key <tt>value</tt>.
     *
     * @param property the name of the property.
     * @param params event parameters.
     */
    public void xmlPropertySet(String property, Map params)
    {
        propertySet(property, params);
    }

    /**
     * An XML property was deleted.
     *
     * @param property the name of the property.
     * @param params event parameters.
     */
    public void xmlPropertyDeleted(String property, Map params)
    {
        propertyDeleted(property, params);
    }
}
