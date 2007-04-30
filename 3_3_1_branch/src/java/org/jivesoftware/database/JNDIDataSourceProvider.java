/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004-2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.database;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

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
     * Constructs a new JNDI pool.
     */
    public JNDIDataSourceProvider() {
        dataSourceName = JiveGlobals.getXMLProperty("database.JNDIProvider.name");
    }

    public boolean isPooled() {
        return true;
    }

    public void start() {
        if (dataSourceName == null || dataSourceName.equals("")) {
            Log.error("No name specified for DataSource. JNDI lookup will fail", null);
            return;
        }
        try {
            Properties contextProperties = new Properties();
            for (String key: jndiPropertyKeys) {
                String value = JiveGlobals.getXMLProperty(key);
                if (value != null) {
                    contextProperties.setProperty(key, value);
                }
            }
            Context context;
            if (contextProperties.size() > 0) {
                context = new InitialContext(contextProperties);
            }
            else {
                context = new InitialContext();
            }
            dataSource = (DataSource)context.lookup(dataSourceName);
        }
        catch (Exception e) {
            Log.error("Could not lookup DataSource at '" + dataSourceName + "'", e);
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
            Log.error("DataSource has not been initialized.", null);
            return null;
        }
        try {
            return dataSource.getConnection();
        }
        catch (SQLException e) {
            Log.error("Could not retrieve Connection from DataSource", e);
            return null;
        }
    }
}