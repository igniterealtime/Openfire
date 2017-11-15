/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.container;

import org.jivesoftware.openfire.XMPPServer;

/**
 * A default Module implementation that basically avoids subclasses having to implement the whole
 * Module interface.
 *
 * @author Gaston Dombiak
 */
public class BasicModule implements Module {

    /**
     * The name of the module
     */
    private String name;

    /**
     * <p>Create a basic module with the given name.</p>
     *
     * @param moduleName The name for the module or null to use the default
     */
    public BasicModule(String moduleName) {
        if (moduleName == null) {
            this.name = "No name assigned";
        }
        else {
            this.name = moduleName;
        }
    }

    /**
     * <p>Obtain the name of the module.</p>
     *
     * @return The name of the module
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * <p>Initializes the basic module.</p>
     * <p>Inheriting classes that choose to override this method MUST
     * call this initialize() method before accessing BasicModule resources.</p>
     *
     * @param server the server hosting this module.
     */
    @Override
    public void initialize(XMPPServer server) {
    }

    /**
     * <p>Starts the basic module.</p>
     * <p>Inheriting classes that choose to override this method MUST
     * call this start() method before accessing BasicModule resources.</p>
     *
     * @throws IllegalStateException If start is called before initialize
     *                               successfully returns
     */
    @Override
    public void start() throws IllegalStateException {
    }

    /**
     * <p>Stops the basic module.</p>
     * <p>Inheriting classes that choose to override this method MUST
     * call this stop() method before accessing BasicModule resources.</p>
     */
    @Override
    public void stop() {
    }

    /**
     * <p>Destroys the module.</p>
     * <p>Does nothing in the basic module.</p>
     */
    @Override
    public void destroy() {
    }
}
