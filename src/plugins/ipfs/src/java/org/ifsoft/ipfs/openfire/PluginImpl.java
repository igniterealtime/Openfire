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

package org.ifsoft.ipfs.openfire;

import java.io.File;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;

import java.lang.reflect.*;
import java.util.*;

import org.jitsi.util.OSUtils;

import de.mxro.process.*;

import io.ipfs.api.*;
import io.ipfs.api.cbor.*;
import io.ipfs.cid.*;
import io.ipfs.multihash.Multihash;
import io.ipfs.multiaddr.MultiAddress;


public class PluginImpl implements Plugin, PropertyEventListener, ProcessListener
{
    private static final Logger Log = LoggerFactory.getLogger(PluginImpl.class);
    private String pluginDirectoryPath = null;
    private XProcess ipfsThread = null;
    private String ipfsExePath = null;
    private String ipfsHomePath = null;
    private boolean ipfsInitialise = false;
    private boolean ipfsStart = false;
    private boolean ipfsReady = false;
    private IPFS ipfs;

    public void destroyPlugin()
    {
        PropertyEventDispatcher.removeListener(this);

        try {
            if (ipfsThread != null) {
                ipfsThread.destory();
            }
        }
        catch (Exception e) {
            //Log.error("IPFS destroyPlugin ", e);
        }
    }

    public void initializePlugin(final PluginManager manager, final File pluginDirectory)
    {
        PropertyEventDispatcher.addListener(this);
        pluginDirectoryPath = JiveGlobals.getProperty("ipfs.path", JiveGlobals.getHomeDirectory() + File.separator + "ipfs");
        checkNatives(pluginDirectory);

        boolean ipfsEnabled = JiveGlobals.getBooleanProperty("ipfs.enabled", true);

        if (ipfsExePath != null && ipfsEnabled)
        {
            ipfsThread = Spawn.startProcess(ipfsExePath + " daemon", new File(ipfsHomePath), this);

        } else {
            Log.info("IPFS disabled");
        }
    }

    public void sendLine(String command)
    {
        if (ipfsThread != null) ipfsThread.sendLine(command);
    }

    public String getPath()
    {
        return pluginDirectoryPath;
    }

    public void onOutputLine(final String line) {
        Log.info(line);

        if (line.startsWith("please run: 'ipfs init'"))
        {
            ipfsInitialise = true;
        }
        else

        if (line.startsWith("Daemon is ready"))
        {
            try {
                ipfsReady = true;
                ipfs = new IPFS(new MultiAddress("/ip4/127.0.0.1/tcp/5001"));

                Log.info("IPFS version " + ipfs.version() + " ready");
            } catch (Exception e) {
                Log.error("IPFS error ", e);
            }
        }
    }

    public void onProcessQuit(int code) {
        ipfsThread = null;

        if (code > 0)
        {
            Log.error("IPFS terminated in error. Code :" + code);

            if (code == 1)
            {
                Log.error("IPFS initialise " + ipfsInitialise);

                ipfsThread = Spawn.startProcess(ipfsExePath + " init", new File(ipfsHomePath), this);
                ipfsStart = true;
            }
        }
        else

        if (ipfsStart) {
            Spawn.startProcess(ipfsExePath + " daemon", new File(ipfsHomePath), this);
            ipfsStart = false;
            ipfsInitialise = true;
        }
    }

    public void onOutputClosed() {
        Log.error("IPFS terminated normally");
    }

    public void onErrorLine(final String line) {
        Log.error(line);
    }

    public void onError(final Throwable t) {
        Log.error("IPFSThread error", t);
    }

    private void checkNatives(File pluginDirectory)
    {
        File ipfsFolder = new File(pluginDirectoryPath);

        if(!ipfsFolder.exists())
        {
            Log.info("initializePlugin home " + pluginDirectory);
            ipfsFolder.mkdirs();
        }

        try
        {
            String suffix = null;

            if(OSUtils.IS_LINUX32)
            {
                suffix = "linux-32";
            }
            else if(OSUtils.IS_LINUX64)
            {
                suffix = "linux-64";
            }
            else if(OSUtils.IS_WINDOWS64)
            {
                suffix = "win-64";
            }

            if (suffix != null)
            {
                ipfsHomePath = pluginDirectory.getAbsolutePath() + File.separator + "classes" + File.separator + suffix;
                ipfsExePath = ipfsHomePath + File.separator + "ipfs";

                if (suffix.startsWith("win-")) {
                    ipfsExePath = ipfsExePath + ".exe";
                }

                File file = new File(ipfsExePath);
                file.setReadable(true, true);
                file.setWritable(true, true);
                file.setExecutable(true, true);

                Log.info("checkNatives ipfs executable path " + ipfsExePath);

            } else {

                Log.error("checkNatives unknown OS " + pluginDirectory.getAbsolutePath());
            }
        }
        catch (Exception e)
        {
            Log.error(e.getMessage(), e);
        }
    }

//-------------------------------------------------------
//
//
//
//-------------------------------------------------------


    public void propertySet(String property, Map params)
    {

    }

    public void propertyDeleted(String property, Map<String, Object> params)
    {

    }

    public void xmlPropertySet(String property, Map<String, Object> params) {

    }

    public void xmlPropertyDeleted(String property, Map<String, Object> params) {

    }

}
