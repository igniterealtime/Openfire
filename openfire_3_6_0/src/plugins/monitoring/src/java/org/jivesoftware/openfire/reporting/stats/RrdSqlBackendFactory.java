/**
 * $RCSfile  $
 * $Revision  $
 * $Date  $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.reporting.stats;

import org.jrobin.core.RrdBackendFactory;
import org.jrobin.core.RrdBackend;
import java.io.IOException;

public class RrdSqlBackendFactory extends RrdBackendFactory {
    // name of the factory
    public static final String NAME = "SQL";

    // creates bew RrdSqlBackend object for the given id (path)
    // the second parameter is ignored
    // for the reason of simplicity
    protected RrdBackend open(String path, boolean readOnly)
    throws IOException {
        return new RrdSqlBackend(path, readOnly);
    }

    // checks if the RRD with the given id (path) already exists
    // in the database
    protected boolean exists(String path) throws IOException {
        return RrdSqlBackend.exists(path);
    }

    // returns factory name
    public String getFactoryName() {
        return NAME;
    }
}