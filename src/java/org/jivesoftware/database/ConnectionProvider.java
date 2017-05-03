/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * TLicensed under the Apache License, Version 2.0 (the "License");
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

package org.jivesoftware.database;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Abstract class that defines the connection provider framework. Other classes
 * extend this abstract class to make connection to actual data sources.
 * <p>
 * It is expected that each subclass be a JavaBean, so that properties of
 * the connection provider are exposed through bean introspection.</p>
 *
 * @author Jive Software
 */
public interface ConnectionProvider {

    /**
     * Returns true if this connection provider provides connections out
     * of a connection pool. Implementing and using connection providers that
     * are pooled is strongly recommended, as they greatly increase the speed
     * of Jive.
     *
     * @return true if the Connection objects returned by this provider are
     *         pooled.
     */
    boolean isPooled();

    /**
     * Returns a database connection. When a Jive component is done with a
     * connection, it will call the close method of that connection. Therefore,
     * connection pools with special release methods are not directly
     * supported by the connection provider infrastructure. Instead, connections
     * from those pools should be wrapped such that calling the close method
     * on the wrapper class will release the connection from the pool.
     *
     * @return a Connection object.
     * @throws SQLException is an SQL error occured while retrieving the connection.
     */
    Connection getConnection() throws SQLException;

    /**
     * Starts the connection provider. For some connection providers, this
     * will be a no-op. However, connection provider users should always call
     * this method to make sure the connection provider is started.
     */
    void start();

    /**
     * This method should be called whenever properties have been changed so
     * that the changes will take effect.
     */
    void restart();

    /**
     * Tells the connection provider to destroy itself. For many connection
     * providers, this will essentially result in a no-op. However,
     * connection provider users should always call this method when changing
     * from one connection provider to another to ensure that there are no
     * dangling database connections.
     */
    void destroy();
}