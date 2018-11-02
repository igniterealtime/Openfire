package com.javamonitor.openfire.mbeans;

import java.sql.SQLException;

/**
 * The proxool database pool MBean interface.
 * 
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public interface DatabasePoolMBean {
    int getMinimumConnectionCount() throws SQLException;

    int getMaximumConnectionCount() throws SQLException;

    int getAvailableConnectionCount() throws SQLException;

    int getActiveConnectionCount() throws SQLException;

    long getMaximumConnectionLifetime() throws SQLException;

    long getServedCount() throws SQLException;

    long getRefusedCount() throws SQLException;

    long getConnectionCount() throws SQLException;
}
