/*
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.container;

import org.jivesoftware.messenger.auth.UnauthorizedException;

/**
 * Acts as a centralized coordinator of JVM-wide resources in the container.
 * Components may directly access the container resources using the
 * methods and resulting resource controllers. The container is passed to
 * the module in it's initialize method.
 *
 * @author Iain Shigeoka
 */
public interface Container {

    /**
     * <p>Obtain the setup mode status of the container.</p>
     * <p>If the container is in setup mode, return true. This is set to
     * true if the jive property "setup" is not set to true. The property
     * indicates that setup has already occured, while this method reports
     * the setup status (true, you're in setup mode). Setup code should
     * set the jive property "setup" to "true" by using the container's
     * module context Container.getModuleContext().</p>
     *
     * @return True if the container is in setup mode
     */
    boolean isSetupMode();

    /**
     * <p>Flag indicating if the container was started in stand alone mode.
     * If the container is in stand alone mode, return true. Stand alone
     * containers may be stopped.</p>
     *
     * @return True if the container is in stand alone mode
     */
    boolean isStandAlone();

    /**
     * <p>Flag indicating if the container can be restarted.
     * The container is restartable only if it is stand alone and
     * a service wrapper deployment was used.</p>
     *
     * @return True if the container can be restarted
     */
    boolean isRestartable();

    /**
     * Obtain the service lookup for the server. The service lookup may
     * contain remote services. You should use the localServerAttribute to
     * locate services that are known to be local to the server.
     *
     * @return The service lookup for finding and posting service items
     * @throws UnauthorizedException If the caller does not have permission to
     *                               access this resource
     */
    ServiceLookup getServiceLookup() throws UnauthorizedException;

    /**
     * <p/>
     * The container will attempt to start the given service if support
     * classes can be found.
     * </p>
     * <p/>
     * A container may not know about the requested service so
     * callers should be prepared for a null return. The call blocks
     * during service initializiation and startup so there may
     * be some delays during the method call.
     * </p>
     *
     * @param service The service to be started
     * @return The service instance or null if one could not be started
     * @throws UnauthorizedException If the caller does not have permission to
     *                               access this resource
     */
    Object startService(Class service) throws UnauthorizedException;

    /**
     * <p/>
     * The container will attempt to stop the all matching services in
     * the container.
     * </p>
     * <p/>
     * A container may not know about the requested service or it may
     * refuse to stop the service even if an authorized exception
     * is not thrown. The call blocks during service shutdon so there may
     * be some delays during the method call. In many cases, stopping a service
     * will initiate the service stopping, but complete shutdown of the service
     * may occur later on a separate thread (for example while waiting for
     * transactions in progress to commit). It is not safe to assume that
     * a successful return from this method means the service has finished
     * shutting down.
     * </p>
     *
     * @param service The service to be stopped
     * @throws UnauthorizedException If the caller does not have permission
     *                               to access this resource
     */
    void stopService(Class service) throws UnauthorizedException;

    /**
     * Obtain the general module context for this container. The module context
     * may be empty since context often is picked up on a module by module basis.
     *
     * @return ModuleContext the module context for the container
     * @throws UnauthorizedException If caller doesn't have appropriate permission
     */
    ModuleContext getModuleContext() throws UnauthorizedException;

    /**
     * Obtain a special entry that modules can use to locate or register local services in
     * the service registrar.
     * <p/>
     * To help modules locate other modules, a Jini service registrar is
     * provided. An entry is also given to narrow service
     * search for modules that are in the local JVM.
     * If your module needs to register itself with the registrar, it should
     * add the local attribute entry to it's entry sets.
     * </p>
     *
     * @return An Entry attribute used to mark local services/modules
     * @throws UnauthorizedException
     */
    Entry getLocalServerAttribute() throws UnauthorizedException;

    /**
     * <p>Stops the container and all hosted services.</p>
     * <p>In standalone mode, this also shuts down the server VM.
     * For app server deployments nothing happens.</p>
     *
     * @throws UnauthorizedException
     */
    void stop() throws UnauthorizedException;

    /**
     * <p>Restarts the container and all it's hosted services. If the
     * container is not restartable, this method does nothing.</p>
     *
     * @throws UnauthorizedException
     */
    void restart() throws UnauthorizedException;
}
