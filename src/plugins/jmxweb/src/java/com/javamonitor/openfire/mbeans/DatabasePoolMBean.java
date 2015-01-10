package com.javamonitor.openfire.mbeans;

import org.logicalcobwebs.proxool.ProxoolException;

/**
 * The proxool database pool MBean interface.
 * 
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public interface DatabasePoolMBean {
    int getMinimumConnectionCount() throws ProxoolException;

    int getMaximumConnectionCount() throws ProxoolException;

    int getAvailableConnectionCount() throws ProxoolException;

    int getActiveConnectionCount() throws ProxoolException;

    long getMaximumActiveTime() throws ProxoolException;

    long getMaximumConnectionLifetime() throws ProxoolException;

    long getServedCount() throws ProxoolException;

    long getRefusedCount() throws ProxoolException;

    int getOfflineConnectionCount() throws ProxoolException;

    long getConnectionCount() throws ProxoolException;
}
