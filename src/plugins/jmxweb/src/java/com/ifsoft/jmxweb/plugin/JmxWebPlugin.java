/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package com.ifsoft.jmxweb.plugin;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.spi.ConnectionManagerImpl;
import org.jivesoftware.util.*;
import org.jivesoftware.openfire.http.HttpBindManager;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.util.security.*;
import org.eclipse.jetty.security.*;
import org.eclipse.jetty.security.authentication.*;

import java.io.File;

import com.javamonitor.JmxHelper;
import com.javamonitor.openfire.mbeans.CoreThreadPool;
import com.javamonitor.openfire.mbeans.DatabasePool;
import com.javamonitor.openfire.mbeans.Openfire;
import com.javamonitor.openfire.mbeans.PacketCounter;


public class JmxWebPlugin implements Plugin  {

	private static Logger Log = LoggerFactory.getLogger("JmxWebPlugin");
	private static final String NAME 		= "jmxweb";
	private static final String DESCRIPTION = "JmxWeb Plugin for Openfire";
    private static final String NAMEBASE = "com.javamonitor.openfire.plugin:";
    public final static String OBJECTNAME_OPENFIRE = NAMEBASE + "type=Openfire";

    private Openfire openfire = null;
    private final static String OBJECTNAME_PACKET_COUNTER = NAMEBASE + "type=packetCounter";
    private PacketCounter packetCounter = null;
    private final static String OBJECTNAME_CORE_CLIENT_THREADPOOL = NAMEBASE + "type=coreThreadpool,poolname=client";
    private CoreThreadPool client = null;
    private final static String OBJECTNAME_DATABASEPOOL = NAMEBASE + "type=databasepool";
    private DatabasePool database = null;


	public void initializePlugin(PluginManager manager, File pluginDirectory) {
		Log.info( "["+ NAME + "] initialize " + NAME + " plugin resources");

        try {
            openfire = new Openfire();
            openfire.start();
            JmxHelper.register(openfire, OBJECTNAME_OPENFIRE);

            Log.info( "["+ NAME + "] .. started openfire server detector.");
        } catch (Exception e) {
            Log.debug("cannot start openfire server detector: "  + e.getMessage(), e);
        }

        try {
            packetCounter = new PacketCounter();
            packetCounter.start();
            JmxHelper.register(packetCounter, OBJECTNAME_PACKET_COUNTER);

            Log.info( "["+ NAME + "] .. started stanza counter.");
        } catch (Exception e) {
            Log.debug("cannot start stanza counter: " + e.getMessage(), e);
        }

        try {
            client = new CoreThreadPool(((ConnectionManagerImpl) XMPPServer
                    .getInstance().getConnectionManager()).getSocketAcceptor());
            client.start();
            JmxHelper.register(client, OBJECTNAME_CORE_CLIENT_THREADPOOL);

            Log.info( "["+ NAME + "] .. started client thread pool monitor.");
        } catch (Exception e) {
            Log.debug("cannot start client thread pool monitor: " + e.getMessage(), e);
        }

        try {
            database = new DatabasePool();
            database.start();
            JmxHelper.register(database, OBJECTNAME_DATABASEPOOL);

            Log.info( "["+ NAME + "] .. started database pool monitor.");
        } catch (Exception e) {
            Log.debug("cannot start database pool monitor: " + e.getMessage(), e);
        }

		try {

			ContextHandlerCollection contexts = HttpBindManager.getInstance().getContexts();

			try {
				Log.info( "["+ NAME + "] starting jolokia");
				WebAppContext context = new WebAppContext(contexts, pluginDirectory.getPath(), "/jolokia");
				context.setWelcomeFiles(new String[]{"index.html"});

				Log.info( "["+ NAME + "] starting hawtio");
				WebAppContext context2 = new WebAppContext(contexts, pluginDirectory.getPath() + "/hawtio", "/hawtio");
				context2.setWelcomeFiles(new String[]{"index.html"});

				if (JiveGlobals.getBooleanProperty("xmpp.jmx.secure", true))
				{
					SecurityHandler securityHandler = basicAuth("jmxweb");
					if (securityHandler != null) context.setSecurityHandler(securityHandler);

					SecurityHandler securityHandler2 = basicAuth("jmxweb");
					if (securityHandler2 != null) context2.setSecurityHandler(securityHandler2);
				}

			}
			catch(Exception e) {
				Log.error( "An error has occurred", e );
        	}
		}
		catch (Exception e) {
			Log.error("Error initializing JmxWeb Plugin", e);
		}
	}

	public void destroyPlugin() {
		Log.info( "["+ NAME + "] destroy " + NAME + " plugin resources");

        database.stop();
        JmxHelper.unregister(OBJECTNAME_DATABASEPOOL);

        client.stop();
        JmxHelper.unregister(OBJECTNAME_CORE_CLIENT_THREADPOOL);

        packetCounter.stop();
        JmxHelper.unregister(OBJECTNAME_PACKET_COUNTER);

        openfire.stop();
        JmxHelper.unregister(OBJECTNAME_OPENFIRE);

        Log.info("["+ NAME + "]  plugin fully destroyed.");
	}

	public String getName() {
		 return NAME;
	}

	public String getDescription() {
		return DESCRIPTION;
	}

    private static final SecurityHandler basicAuth(String realm) {

    	OpenfireLoginService l = new OpenfireLoginService();
        l.setName(realm);

        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);
        constraint.setRoles(new String[]{"jmxweb"});
        constraint.setAuthenticate(true);

        ConstraintMapping cm = new ConstraintMapping();
        cm.setConstraint(constraint);
        cm.setPathSpec("/*");

        ConstraintSecurityHandler csh = new ConstraintSecurityHandler();
        csh.setAuthenticator(new BasicAuthenticator());
        csh.setRealmName(realm);
        csh.addConstraintMapping(cm);
        csh.setLoginService(l);

        return csh;
    }
}