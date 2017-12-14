package com.javamonitor.openfire.mbeans;

import org.jivesoftware.database.ConnectionProvider;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.DefaultConnectionProvider;
import org.jivesoftware.util.JiveConstants;

/**
 * The database monitor pool.
 * 
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class DatabasePool implements DatabasePoolMBean {

    private static DefaultConnectionProvider getDefaultConnectionProvider() {
        final ConnectionProvider connectionProvider = DbConnectionManager.getConnectionProvider();
        if(connectionProvider instanceof DefaultConnectionProvider) {
            return (DefaultConnectionProvider) connectionProvider;
        } else {
            return null;
        }
    }


    /**
     * Start collecting database packets.
     */
    public void start() {
        // nothing to do...
    }

    /**
     * Stop collecting data.
     */
    public void stop() {
        // nothing to do...
    }

    /**
     * @see com.javamonitor.openfire.mbeans.DatabasePoolMBean#getActiveConnectionCount()
     */
    public int getActiveConnectionCount() {
        final DefaultConnectionProvider defaultConnectionProvider = getDefaultConnectionProvider();
        return defaultConnectionProvider == null ? 0 : defaultConnectionProvider.getActiveConnections();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.DatabasePoolMBean#getAvailableConnectionCount()
     */
    public int getAvailableConnectionCount() {
        final DefaultConnectionProvider defaultConnectionProvider = getDefaultConnectionProvider();
        return defaultConnectionProvider == null ? 0 : defaultConnectionProvider.getMaxConnections() - defaultConnectionProvider.getActiveConnections();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.DatabasePoolMBean#getConnectionCount()
     */
    public long getConnectionCount(){
        final DefaultConnectionProvider defaultConnectionProvider = getDefaultConnectionProvider();
        return defaultConnectionProvider == null ? 0 : defaultConnectionProvider.getActiveConnections() + defaultConnectionProvider.getIdleConnections();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.DatabasePoolMBean#getRefusedCount()
     */
    public long getRefusedCount() {
        final DefaultConnectionProvider defaultConnectionProvider = getDefaultConnectionProvider();
        return defaultConnectionProvider == null ? 0 : defaultConnectionProvider.getRefusedCount();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.DatabasePoolMBean#getServedCount()
     */
    public long getServedCount() {
        final DefaultConnectionProvider defaultConnectionProvider = getDefaultConnectionProvider();
        return defaultConnectionProvider == null ? 0 : defaultConnectionProvider.getConnectionsServed();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.DatabasePoolMBean#getMaximumConnectionCount()
     */
    public int getMaximumConnectionCount() {
        final DefaultConnectionProvider defaultConnectionProvider = getDefaultConnectionProvider();
        return defaultConnectionProvider == null ? 0 : defaultConnectionProvider.getMaxConnections();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.DatabasePoolMBean#getMinimumConnectionCount()
     */
    public int getMinimumConnectionCount() {
        final DefaultConnectionProvider defaultConnectionProvider = getDefaultConnectionProvider();
        return defaultConnectionProvider == null ? 0 : defaultConnectionProvider.getMinConnections();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.DatabasePoolMBean#getMaximumConnectionLifetime()
     */
    public long getMaximumConnectionLifetime() {
        final DefaultConnectionProvider defaultConnectionProvider = getDefaultConnectionProvider();
        return defaultConnectionProvider == null ? 0L : (long) (defaultConnectionProvider.getConnectionTimeout() * JiveConstants.DAY);
    }
}
