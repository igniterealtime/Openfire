package com.javamonitor.openfire.mbeans;

import org.logicalcobwebs.proxool.ConnectionPoolDefinitionIF;
import org.logicalcobwebs.proxool.ProxoolException;
import org.logicalcobwebs.proxool.ProxoolFacade;
import org.logicalcobwebs.proxool.admin.SnapshotIF;

/**
 * The database monitor pool.
 * 
 * XXX it makes more sense to register Proxools JMX features directly!
 * 
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class DatabasePool implements DatabasePoolMBean {
    private static SnapshotIF getSnapshot() throws ProxoolException {
        return ProxoolFacade.getSnapshot("openfire", true);
    }

    private static ConnectionPoolDefinitionIF getPoolDef()
            throws ProxoolException {
        return ProxoolFacade.getConnectionPoolDefinition("openfire");
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
    public int getActiveConnectionCount() throws ProxoolException {
        return getSnapshot().getActiveConnectionCount();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.DatabasePoolMBean#getAvailableConnectionCount()
     */
    public int getAvailableConnectionCount() throws ProxoolException {
        return getSnapshot().getAvailableConnectionCount();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.DatabasePoolMBean#getConnectionCount()
     */
    public long getConnectionCount() throws ProxoolException {
        return getSnapshot().getConnectionCount();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.DatabasePoolMBean#getOfflineConnectionCount()
     */
    public int getOfflineConnectionCount() throws ProxoolException {
        return getSnapshot().getOfflineConnectionCount();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.DatabasePoolMBean#getRefusedCount()
     */
    public long getRefusedCount() throws ProxoolException {
        return getSnapshot().getRefusedCount();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.DatabasePoolMBean#getServedCount()
     */
    public long getServedCount() throws ProxoolException {
        return getSnapshot().getServedCount();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.DatabasePoolMBean#getMaximumConnectionCount()
     */
    public int getMaximumConnectionCount() throws ProxoolException {
        return getPoolDef().getMaximumConnectionCount();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.DatabasePoolMBean#getMinimumConnectionCount()
     */
    public int getMinimumConnectionCount() throws ProxoolException {
        return getPoolDef().getMinimumConnectionCount();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.DatabasePoolMBean#getMaximumActiveTime()
     */
    public long getMaximumActiveTime() throws ProxoolException {
        return getPoolDef().getMaximumActiveTime();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.DatabasePoolMBean#getMaximumConnectionLifetime()
     */
    public long getMaximumConnectionLifetime() throws ProxoolException {
        return getPoolDef().getMaximumConnectionLifetime();
    }
}
