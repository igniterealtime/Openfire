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

package org.ifsoft.nodejs.openfire;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.jitsi.util.OSUtils;


public class PluginImpl implements Plugin, PropertyEventListener
{
    private static final Logger Log = LoggerFactory.getLogger(PluginImpl.class);
    private String pluginDirectoryPath = null;
    private HashMap<String, NodeThread> scripts = new HashMap<String, NodeThread>();
    private ExecutorService executor;
    private String nodeExePath = null;

    public void destroyPlugin()
    {
        PropertyEventDispatcher.removeListener(this);

        try {
            for (NodeThread script : scripts.values())
            {
                script.stop();
            }

            executor.shutdown();
        }
        catch (Exception e) {
            //Log.error("NodeJs destroyPlugin ", e);
        }
    }

    public void initializePlugin(final PluginManager manager, final File pluginDirectory)
    {
        PropertyEventDispatcher.addListener(this);
        pluginDirectoryPath = JiveGlobals.getProperty("org.ifsoft.nodejs.openfire.path", JiveGlobals.getHomeDirectory() + File.separator + "nodejs");

        checkNatives(pluginDirectory);

        if (nodeExePath != null)
        {
            executor = Executors.newCachedThreadPool();

            executor.submit(new Callable<Boolean>()
            {
                public Boolean call() throws Exception {
                    try {
                        List<String> properties = JiveGlobals.getPropertyNames();

                        for (String propertyName : properties)
                        {
                            String propertyValue = JiveGlobals.getProperty(propertyName);

                            executeScript(propertyName, propertyValue);
                        }
                    }

                    catch (Exception e) {
                        Log.error("NodeJs initializePluginn", e);
                    }

                    return true;
                }
            });
        }
    }

    public String getPath()
    {
        return pluginDirectoryPath;
    }

    private void checkNatives(File pluginDirectory)
    {
        File nodeFolder = new File(pluginDirectoryPath);

        if(!nodeFolder.exists())
        {
            Log.info("initializePlugin home " + pluginDirectory);
            nodeFolder.mkdirs();
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
            else if(OSUtils.IS_WINDOWS32)
            {
                suffix = "win-32";
            }
            else if(OSUtils.IS_WINDOWS64)
            {
                suffix = "win-64";
            }
            else if(OSUtils.IS_MAC)
            {
                suffix = "osx-64";
            }

            if (suffix != null)
            {
                nodeExePath = pluginDirectory.getAbsolutePath() + File.separator + "native" + File.separator + suffix  + File.separator + "node";

                File file = new File(nodeExePath);
                file.setReadable(true, true);
                file.setWritable(true, true);
                file.setExecutable(true, true);

                Log.info("checkNatives node executable path " + nodeExePath);

            } else {

                Log.error("checkNatives unknown OS " + pluginDirectory.getAbsolutePath());
            }
        }
        catch (Exception e)
        {
            Log.error(e.getMessage(), e);
        }
    }

    private void executeScript(String scriptPropertyName, String scriptPropertyValue)
    {
        try {

            if (scriptPropertyName.indexOf("js.") == 0 && scriptPropertyName.indexOf(".path") != scriptPropertyName.length() - 5)
            {
                String scriptPath = scriptPropertyValue;
                String scriptHomePath = JiveGlobals.getProperty(scriptPropertyName + ".path", pluginDirectoryPath);

                Log.info("executeScript executable path " + scriptPath + " " + scriptHomePath);

                NodeThread nodeThread = new NodeThread();
                nodeThread.start(nodeExePath + " " + scriptPath,  new File(scriptHomePath));
                scripts.put(scriptPropertyName, nodeThread);

            }

        } catch (Throwable t) {

            Log.error("Error running NodeJ Scripts ", t);
        }
    }

//-------------------------------------------------------
//
//
//
//-------------------------------------------------------


    public void propertySet(String property, Map params)
    {
        if (property.indexOf("js.") == 0 && property.indexOf(".path") != property.length() - 5)
        {
            String value = (String)params.get("value");

            if (scripts.containsKey(property))
            {
                NodeThread script = scripts.get(property);
                script.stop();
            }

            executeScript(property, value);
        }
    }

    public void propertyDeleted(String property, Map<String, Object> params)
    {
        if (scripts.containsKey(property))
        {
            NodeThread script = scripts.remove(property);
            script.stop();
        }
    }

    public void xmlPropertySet(String property, Map<String, Object> params) {

    }

    public void xmlPropertyDeleted(String property, Map<String, Object> params) {

    }

}
