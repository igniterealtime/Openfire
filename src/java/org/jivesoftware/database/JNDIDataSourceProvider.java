/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.database;

import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.JiveGlobals;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;

/**
 * An implementation of ConnectionProvider that utilizes a JDBC 2.0 DataSource
 * made available via JNDI. This is useful for application servers where a pooled
 * data connection is already provided so Jive can share the pool with the
 * other applications.<p>
 * <p/>
 * The JNDI location of the DataSource stored as the Jive property
 * <code>database.JNDIProvider.name</code>. This can be overridden by setting
 * the provider's <code>name</code> property if required.
 *
 * @author <a href="mailto:joe@truemesh.com">Joe Walnes</a>
 * @see ConnectionProvider
 */
public class JNDIDataSourceProvider implements ConnectionProvider {

    private String dataSourceName;
    private DataSource dataSource;

    /**
     * Keys of JNDI properties to query PropertyManager for.
     */
    private static final String[] jndiPropertyKeys = {
        Context.APPLET,
        Context.AUTHORITATIVE,
        Context.BATCHSIZE,
        Context.DNS_URL,
        Context.INITIAL_CONTEXT_FACTORY,
        Context.LANGUAGE,
        Context.OBJECT_FACTORIES,
        Context.PROVIDER_URL,
        Context.REFERRAL,
        Context.SECURITY_AUTHENTICATION,
        Context.SECURITY_CREDENTIALS,
        Context.SECURITY_PRINCIPAL,
        Context.SECURITY_PROTOCOL,
        Context.STATE_FACTORIES,
        Context.URL_PKG_PREFIXES
    };

    /**
     * Initialize.
     */
    public JNDIDataSourceProvider() {
        dataSourceName = JiveGlobals.getJiveProperty("database.JNDIProvider.name");
    }

    public String getName() {
        return "JNDI DataSource Connection Provider";
    }

    public String getDescription() {
        return "Connection Provider for Jive to lookup pooled "
                + "DataSource from JNDI location. Requires 'name' "
                + "property with JNDI location. This can be set in "
                + "the properties file as 'JNDIDataSource.name'";
    }

    public String getAuthor() {
        return "Joe Walnes - joe@truemesh.com";
    }

    public int getMajorVersion() {
        return 2;
    }

    public int getMinorVersion() {
        return 1;
    }

    public boolean isPooled() {
        return true;
    }

    public void start() {
        if (dataSourceName == null || dataSourceName.equals("")) {
            error("No name specified for DataSource. JNDI lookup will fail", null);
            return;
        }
        try {
            Properties contextProperties = new Properties();
            for (int i = 0; i < jndiPropertyKeys.length; i++) {
                String k = jndiPropertyKeys[i];
                String v = JiveGlobals.getJiveProperty(k);
                if (v != null) {
                    contextProperties.setProperty(k, v);
                }
            }
            Context context = null;
            if (contextProperties.size() > 0) {
                context = new InitialContext(contextProperties);
            }
            else {
                context = new InitialContext();
            }
            dataSource = (DataSource)context.lookup(dataSourceName);
        }
        catch (Exception e) {
            error("Could not lookup DataSource at '" + dataSourceName + "'", e);
        }
    }

    public void restart() {
        destroy();
        start();
    }

    public void destroy() {

    }

    public Connection getConnection() {
        if (dataSource == null) {
            error("DataSource has not been initialized.", null);
            return null;
        }
        try {
            return dataSource.getConnection();
        }
        catch (SQLException e) {
            error("Could not retrieve Connection from DataSource", e);
            return null;
        }
    }

    public String getProperty(String name) {
        if ("name".equals(name)) {
            return dataSourceName;
        }
        else {
            return null;
        }
    }

    public void setProperty(String name, String value) {
        if ("name".equals(name)) {
            this.dataSourceName = value;
            JiveGlobals.setJiveProperty("database.JNDIProvider.name", value);
        }
    }

    public Iterator propertyNames() {
        List list = new ArrayList();
        list.add("name");
        return Collections.unmodifiableList(list).iterator();
    }

    public String getPropertyDescription(String name) {
        if ("name".equals(name)) {
            return "JNDI name to lookup. eg: java:comp/env/jdbc/MyDataSource";
        }
        else {
            return null;
        }
    }

    /**
     * Log an error.
     *
     * @param msg Description of error
     * @param e   Exception to printStackTrace (may be null)
     */
    private final void error(String msg, Exception e) {
        Log.error(msg, e);
    }
}
