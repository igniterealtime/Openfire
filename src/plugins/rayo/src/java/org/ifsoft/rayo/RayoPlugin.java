/*
 * Copyright (C) 2005-2010 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ifsoft.rayo;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.event.SessionEventDispatcher;
import org.jivesoftware.openfire.event.SessionEventListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.jnodes.*;
import org.xmpp.jnodes.nio.LocalIPResolver;
import org.xmpp.jnodes.nio.PublicIPResolver;
import org.xmpp.packet.*;

import org.jitsi.util.*;

import com.rayo.core.verb.*;

import org.voicebridge.*;

import com.sun.voip.server.*;
import com.sun.voip.*;



public class RayoPlugin implements Plugin, SessionEventListener  {

    private static final Logger Log = LoggerFactory.getLogger(RayoPlugin.class);

    public static final String JN_PUB_IP_PROPERTY = "rayo.publicip";
    private ComponentManager componentManager;

    private final ConcurrentHashMap<String, RelayChannel> channels = new ConcurrentHashMap<String, RelayChannel>();
    private final long timeout = 60000;
    private final AtomicInteger ids = new AtomicInteger(0);
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    private final String serviceName = "rayo";

    private final boolean bindAllInterfaces;

    private boolean hasPublicIP = false;

    private Application bridge = new Application();
    public static RayoComponent component = null;

    public RayoPlugin() {
        final String os = System.getProperty("os.name");
        bindAllInterfaces = !(os != null && os.toLowerCase().indexOf("win") > -1);
    }

    public String getName() {
        return "rayo";
    }

    public String getDescription() {
        return "Rayo Plugin";
    }

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        componentManager = ComponentManagerFactory.getComponentManager();
        component = new RayoComponent(this);
        try {
            componentManager.addComponent(serviceName, component);
            bridge.appStart(pluginDirectory);
            checkNatives(pluginDirectory);
            checkRecordingFolder(pluginDirectory);
            SessionEventDispatcher.addListener(this);
        } catch (ComponentException e) {
            Log.error("Could NOT load " + component.getName());
        }
        setup();

        component.doStart();
    }

    private void setup() {
        executor.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                for (final RelayChannel c : channels.values()) {
                    final long current = System.currentTimeMillis();
                    final long da = current - c.getLastReceivedTimeA();
                    final long db = current - c.getLastReceivedTimeB();

                    if (da > timeout || db > timeout) {
                        removeChannel(c);
                    }
                }
            }
        }, timeout, timeout, TimeUnit.MILLISECONDS);
        Log.info("Rayo Plugin Loaded.");

        verifyNetwork();
    }

    public void verifyNetwork() {
        final String localAddress = JiveGlobals.getProperty(JN_PUB_IP_PROPERTY, LocalIPResolver.getLocalIP());
        LocalIPResolver.setOverrideIp(localAddress);
        final InetSocketAddress publicAddress = PublicIPResolver.getPublicAddress("stun.l.google.com", 19302);
        hasPublicIP = publicAddress != null && publicAddress.getAddress().getHostAddress().equals(localAddress);
    }

    private void closeAllChannels() {
        for (final RelayChannel c : channels.values()) {
            removeChannel(c);
        }
    }

    public RelayChannel getRelayChannel(String id)
    {
        if (channels.containsKey(id))
            return channels.get(id);
        else
            return null;
    }

    public void handleVoiceBridge(String id, String operation, String parameter)
    {
            Log.info("handleVoiceBridge " + id);
            bridge.manageCallParticipant(id, operation, parameter);
    }


    public RelayChannel createRelayChannel(JID jid, Handset handset) {
        RelayChannel rc = null;

        try {
            rc = RelayChannel.createLocalRelayChannel(bindAllInterfaces ? "0.0.0.0" : LocalIPResolver.getLocalIP(), 30000, 50000);
            final int id = ids.incrementAndGet();
            final String sId = JID.escapeNode(jid.toString());
            rc.setAttachment(sId);
            rc.setFrom(jid, component);
            rc.setCrypto(handset);
            channels.put(sId, rc);

        } catch (IOException e) {
            Log.error("Could Not Create Channel.", e);
        }

        return rc;
    }

    public void removeChannel(final RelayChannel c) {
        channels.remove(c.getAttachment());
        c.close();
    }

    public String getServiceName() {
        return serviceName;
    }

    public void destroyPlugin() {
        try {
            componentManager.removeComponent(serviceName);
            bridge.appStop();
            SessionEventDispatcher.removeListener(this);
        } catch (ComponentException e) {
            Log.error("Could NOT Remove " + serviceName + " Component");
        }
        closeAllChannels();
        executor.shutdownNow();
        component.doStop();
    }

    public boolean hasPublicIP() {
        return hasPublicIP;
    }

    public int getActiveChannelCount() {
        return channels.size();
    }

    /**
     * Checks whether we have folder with extracted natives, if missing
     * find the appropriate jar file and extract them. Normally this is
     * done once when plugin is installed or updated.
     * If folder with natives exist add it to the java.library.path so
     * rayo can use those native libs.
     */
    private void checkNatives(File pluginDirectory)
    {
        // Find the root path of the class that will be our plugin lib folder.
        try
        {
            String nativeLibsJarPath = pluginDirectory.getAbsolutePath() + File.separator + "lib";
            Log.info("checkNatives." + nativeLibsJarPath);

            File nativeLibFolder = new File(nativeLibsJarPath, "native");

            if(!nativeLibFolder.exists())
            {
                nativeLibFolder.mkdirs();

                // lets find the appropriate jar file to extract and
                // extract it
                String jarFileSuffix = null;
                if(OSUtils.IS_LINUX32)
                {
                    jarFileSuffix = "rayo-native-linux-32.jar";
                }
                else if(OSUtils.IS_LINUX64)
                {
                    jarFileSuffix = "rayo-native-linux-64.jar";
                }
                else if(OSUtils.IS_WINDOWS32)
                {
                    jarFileSuffix = "rayo-native-windows-32.jar";
                }
                else if(OSUtils.IS_WINDOWS64)
                {
                    jarFileSuffix = "rayo-native-windows-64.jar";
                }
                else if(OSUtils.IS_MAC)
                {
                    jarFileSuffix = "rayo-native-macosx.jar";
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

    private void checkRecordingFolder(File pluginDirectory)
    {
        String rayoHome = JiveGlobals.getHomeDirectory() + File.separator + "resources" + File.separator + "spank" + File.separator + "rayo";

        try
        {
            File rayoFolderPath = new File(rayoHome);

            if(!rayoFolderPath.exists())
            {
                rayoFolderPath.mkdirs();

            }

            File recordingFolderPath = new File(rayoHome + File.separator + "recordings");

            if(!recordingFolderPath.exists())
            {
                recordingFolderPath.mkdirs();

            }

            File soundsFolderPath = new File(rayoHome + File.separator + "sounds");

            if(!soundsFolderPath.exists())
            {
                soundsFolderPath.mkdirs();

            }
        }
        catch (Exception e)
        {
            Log.error(e.getMessage(), e);
        }
    }

    public void anonymousSessionCreated(Session session)
    {
        Log.debug("RayoPlugin anonymousSessionCreated "+ session.getAddress().toString() + "\n" + ((ClientSession) session).getPresence().toXML());
    }

    public void anonymousSessionDestroyed(Session session)
    {
        Log.debug("RayoPlugin anonymousSessionDestroyed "+ session.getAddress().toString() + "\n" + ((ClientSession) session).getPresence().toXML());

        CallHandler.hangupOwner(session.getAddress().toString(), "User has ended session");
    }

    public void resourceBound(Session session)
    {
        Log.debug("RayoPlugin resourceBound "+ session.getAddress().toString() + "\n" + ((ClientSession) session).getPresence().toXML());
    }

    public void sessionCreated(Session session)
    {
        Log.debug("RayoPlugin sessionCreated "+ session.getAddress().toString() + "\n" + ((ClientSession) session).getPresence().toXML());
    }

    public void sessionDestroyed(Session session)
    {
        Log.debug("RayoPlugin sessionDestroyed "+ session.getAddress().toString() + "\n" + ((ClientSession) session).getPresence().toXML());

        CallHandler.hangupOwner(session.getAddress().toString(), "User has ended session");
    }
}
