/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.component;

import org.jivesoftware.util.ModificationNotAllowedException;

/**
 * Listener that will be alerted when an external component is disabled/enabled,
 * the port is changed or configuration about an external component is modified.<p>
 *
 * All listeners of the event will be alerted. Moreover, listeners have the chance
 * to deny a change from happening. If a single listener denied the operation then
 * it will not be allowed.
 *
 * @author Gaston Dombiak
 */
public interface ExternalComponentManagerListener {

    /**
     * Notification indicating whether the service is being enabled or disabled. The
     * listener may throw an exception to not allow the change from taking place.
     *
     * @param enabled true if the service is being enabled.
     * @throws ModificationNotAllowedException if the operation was denied.
     */
    void serviceEnabled(boolean enabled) throws ModificationNotAllowedException;

    /**
     * Notification indicating that the port used by external components is being
     * modified. The listener may throw an exception to not allow the change from
     * taking place.
     *
     * @param newPort new default secret being set.
     * @throws ModificationNotAllowedException if the operation was denied.
     */
    void portChanged(int newPort) throws ModificationNotAllowedException;

    /**
     * Notification indicating that the default secret is being modified. The
     * listener may throw an exception to not allow the change from taking place.
     *
     * @param newSecret new default secret being set.
     * @throws ModificationNotAllowedException if the operation was denied.
     */
    void defaultSecretChanged(String newSecret) throws ModificationNotAllowedException;

    /**
     * Notification indicating that the permission policy is being modified. See
     * {@link ExternalComponentManager.PermissionPolicy} for more information. The
     * listener may throw an exception to not allow the change from taking place.
     *
     * @param newPolicy new permission policy being set.
     * @throws ModificationNotAllowedException if the operation was denied.
     */
    void permissionPolicyChanged(ExternalComponentManager.PermissionPolicy newPolicy) throws ModificationNotAllowedException;

    /**
     * Notification indicating that a new component was allowed to connect using a
     * given configuration. The listener may throw an exception to not allow the
     * change from taking place.
     *
     * @param subdomain subdomain of the added component.
     * @param configuration configuration for the external component.
     * @throws ModificationNotAllowedException if the operation was denied.
     */
    void componentAllowed(String subdomain, ExternalComponentConfiguration configuration)
            throws ModificationNotAllowedException;

    /**
     * Notification indicating that a component was blocked to connect to the server.
     * The listener may throw an exception to not allow the change from taking place.
     *
     * @param subdomain subdomain of the blocked component.
     * @throws ModificationNotAllowedException if the operation was denied.
     */
    void componentBlocked(String subdomain) throws ModificationNotAllowedException;


    /**
     * Notification indicating that the configuration of a component, that was either
     * blocked or allowed to connect, is being deleted. The listener may throw an exception
     * to not allow the change from taking place.
     *
     * @param subdomain subdomain of the component.
     * @param newSecret new secret being set for the component.
     * @throws ModificationNotAllowedException if the operation was denied.
     */
    void componentSecretUpdated(String subdomain, String newSecret) throws ModificationNotAllowedException;

    /**
     * Notification indicating that the configuration of a component, that was either
     * blocked or allowed to connect, is being deleted. The listener may throw an
     * exception to not allow the change from taking place.
     *
     * @param subdomain subdomain of the component.
     * @throws ModificationNotAllowedException if the operation was denied.
     */
    void componentConfigurationDeleted(String subdomain) throws ModificationNotAllowedException;
}
