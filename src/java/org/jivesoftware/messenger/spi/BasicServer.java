/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.container.Container;
import org.jivesoftware.messenger.container.ServiceLookup;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.Version;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import java.text.DateFormat;
import java.util.*;

/**
 * Main entry point into the Jive xmpp server powered by a JDBC backend. Most of this code
 * is actually generic to any server so should be moved to a base class when we have
 * a separate server implementation.
 *
 * @author Iain Shigeoka
 */
public class BasicServer
        extends BasicModule
        implements XMPPServer, BasicServerMBean {

    private String name;
    private Version version;
    private Date startDate;
    private Date stopDate;
    private ServiceLookup lookup;
    private ConnectionManager connectionManager;
    private boolean initialized = false;

    /**
     * Create a default loopback test server.
     */
    public BasicServer() {
        super("XMPP Server");
    }

    public XMPPServerInfo getServerInfo() {
        Iterator ports;
        if (connectionManager == null) {
            connectionManager =
                    (ConnectionManager)lookup.lookup(ConnectionManager.class);
        }
        if (connectionManager == null) {
            ports = Collections.EMPTY_LIST.iterator();
        }
        else {
            ports = connectionManager.getPorts();
        }
        if (!initialized) {
            throw new IllegalStateException("Not initialized yet");
        }
        return new XMPPServerInfoImpl(name, version, startDate, stopDate, ports);
    }

    public boolean isLocal(XMPPAddress jid) {
        boolean local = false;
        if (jid != null && name != null && name.equalsIgnoreCase(jid.getHost())) {
            local = true;
        }
        return local;
    }

    public XMPPAddress createAddress(String username, String resource) {
        return new XMPPAddress(username, name, resource);
    }

    private Session serverSession =
            new ServerSession(new XMPPAddress(null, name, null),
                    new BasicStreamIDFactory().createStreamID(name));

    public Session getSession() {
        return serverSession;
    }

    public String getName() {
        return "XMPP Server Kernel";
    }

    public void initialize(Container container) {
        super.initialize(container);
        try {
            lookup = container.getServiceLookup();
            name = JiveGlobals.getProperty("xmpp.domain");
            if (name == null) {
                name = "127.0.0.1";
            }

            version = new Version(2, 0, 0, Version.ReleaseStatus.Beta, 1);
            initialized = true;
        }
        catch (UnauthorizedException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    public void start() {
        super.start();
        DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
                DateFormat.MEDIUM);
        List params = new ArrayList();
        params.add(version.getVersionString());
        params.add(formatter.format(new Date()));
        String startupBanner = LocaleUtils.getLocalizedString("startup.name", params);
        Log.info("####################################################################");
        Log.info(startupBanner);
        Log.info("--------------------------------------------------------------------");
        System.out.println(startupBanner);

        params.clear();
        params.add(name);
        Log.info(LocaleUtils.getLocalizedString("startup.starting", params));

        // Register the server as an MBean.
//            MBeanManager.registerMBean(this, ("Server:Name=" + getName()));

        startDate = new Date();
        stopDate = null;
    }

    public void stop() {
        super.stop();
        stopDate = new Date();
    }
}
