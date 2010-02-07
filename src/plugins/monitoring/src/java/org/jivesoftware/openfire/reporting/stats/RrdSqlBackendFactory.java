/**
 * $RCSfile  $
 * $Revision  $
 * $Date  $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
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
    @Override
	protected RrdBackend open(String path, boolean readOnly)
    throws IOException {
        return new RrdSqlBackend(path, readOnly);
    }

    // checks if the RRD with the given id (path) already exists
    // in the database
    @Override
	protected boolean exists(String path) throws IOException {
        return RrdSqlBackend.exists(path);
    }

    // returns factory name
    @Override
	public String getFactoryName() {
        return NAME;
    }
}