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

package org.jivesoftware.messenger.container;

import org.jivesoftware.messenger.XMPPServer;

/**
 * <p>A skeleton Module implementation that takes care of the most
 * common Module housekeeping chores.</p>
 * <p>The basic module manages the lookup registration for the
 * service but requires a small amount of help. In order to know
 * what services should be registered, the basic module will go
 * through the following during start()</p>
 * <ul>
 * <li>Call getServices(). If the result is not null, all services
 * in the list are registered.</li>
 * <li>Call getService(). If the result is not null, the service
 * is registered.</li>
 * <li>Call getServiceItemss(). If the result is not null, all service items
 * in the list are registered.</li>
 * <li>Call getServiceItem(). If the result is not null, the service item
 * is registered.</li>
 * </ul>
 * <p>It is critical that inheriting classes overriding the module
 * lifecycle methods call the parent life cycle
 * methods in order to ensure these housekeeping tasks occur.</p>
 * <p/>
 * <h2>Tracker</h2>
 * <p>The basic module can manage a ServiceTracker for inheriting classes. To
 * use the built-in tracker, you must do the following:</p>
 * <ol>
 * <li>Override getTrackInfo() - and return an array of classes that you want
 * the service tracker to track.</li>
 * <li>Override addService() and removeService() - to receive services that are
 * being added or removed from the lookup. It is important to react to both.
 * Removal should result in the removal of any references to the service object
 * so the garbage collector can clean up the service. It is also dangerous
 * (e.g. throw NPE) to use services that have been removed from the lookup.</li>
 * </ol>
 *
 * @author Iain Shigeoka
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
    public String getName() {
        return name;
    }

    /**
     * <p>Initializes the basic module.</p>
     * <p/>
     * <p>Inheriting classes that choose to override this method MUST
     * call this initialize() method before accessing BasicModule resources.</p>
     *
     * @param server the server hosting this module.
     */
    public void initialize(XMPPServer server) {
    }

    /**
     * <p>Starts the basic module.</p>
     * <p/>
     * <p>Inheriting classes that choose to override this method MUST
     * call this start() method before accessing BasicModule resources.</p>
     *
     * @throws IllegalStateException If start is called before initialize
     *                               successfully returns
     */
    public void start() throws IllegalStateException {
    }

    /**
     * <p>Stops the basic module.</p>
     * <p/>
     * <p>Inheriting classes that choose to override this method MUST
     * call this stop() method before accessing BasicModule resources.</p>
     */
    public void stop() {
    }

    /**
     * <p>Destroys the module.</p>
     * <p/>
     * <p>Does nothing in the basic module.</p>
     */
    public void destroy() {
    }
}