/**
 * $RCSfile$
 * $Revision: 624 $
 * $Date: 2004-12-05 02:38:08 -0300 (Sun, 05 Dec 2004) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.container;

import org.jivesoftware.openfire.XMPPServer;

/**
 * Logical, server-managed entities must implement this interface. A module
 * represents an operational unit and may contain zero or more services
 * and rely on zero or more services that may be hosted by the container.
 * <p/>
 * In order to be hosted in the Jive server container, all modules must:
 * </p>
 * <ul>
 * <li>Implement the Module interface</li>
 * <li>Have a public no-arg constructor</li>
 * </ul>
 * <p/>
 * The Jive container will run all modules through a simple lifecycle:
 * <pre>
 * constructor -> initialize() -> start() -> stop() -> destroy() -> finalizer
 *                    |<-----------------------|          ^
 *                    |                                   |
 *                    V----------------------------------->
 * </pre>
 * </p>
 * <p/>
 * The Module interface is intended to provide the simplest mechanism
 * for creating, deploying, and managing server modules.
 * </p>
 *
 * @author Iain Shigeoka
 */
public interface Module {

    /**
     * Returns the name of the module for display in administration interfaces.
     *
     * @return The name of the module.
     */
    String getName();

    /**
     * Initialize the module with the container.
     * Modules may be initialized and never started, so modules
     * should be prepared for a call to destroy() to follow initialize().
     *
     * @param server the server hosting this module.
     */
    void initialize(XMPPServer server);

    /**
     * Start the module (must return quickly). Any long running
     * operations should spawn a thread and allow the method to return
     * immediately.
     */
    void start();

    /**
     * Stop the module. The module should attempt to free up threads
     * and prepare for either another call to initialize (reconfigure the module)
     * or for destruction.
     */
    void stop();

    /**
     * Module should free all resources and prepare for deallocation.
     */
    void destroy();
}
