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

/**
 * <p>Allows a generic method of finding container services.</p>
 * <p>A service is any
 * Java object that has registered itself with the lookup including the
 * container itself. The lookup and AuthFactory are the only static singletons
 * in the system allowing any object in the JVM to locate Jive related objects.
 * </p>
 *
 * @author Iain Shigeoka
 */
public interface ServiceLookup {
    /**
     * Indicates a transition that occurs when a service matches the lookup
     * template before and after a change. Typically indicates an attribute
     * change to an existing service.
     */
    final int TRANSITION_MATCH_MATCH = 1;

    /**
     * <p>Indicates a transition that occurs when a service matches the lookup
     * template before and does not match after a change.</p>
     * <p/>
     * Typically indicates a watched service is no longer in the lookup.</p>
     */
    final int TRANSITION_MATCH_NOMATCH = 2;

    /**
     * <p>Indicates a transition that occurs when a service does not
     * match the lookup template before and does match after a change.</p>
     * <p>Typically indicates a new service has been registered that
     * meets the search criteria.</p>
     */
    final int TRANSITION_NOMATCH_MATCH = 4;

    /**
     * <p>Returns the service ID of the the service
     * lookup (it is, itself, a service).</p>
     *
     * @return the service lookup's service id.
     */
    ServiceID getServiceID();

    /**
     * <p>Returns the classes of all registered services that match the given service
     * template.</p>
     * <p/>
     * Matches will return the most specific class that is not equal to,
     * or a superclass of the template search class. Matches can be further restricted
     * to those with names that begin with the given prefix (use null for the prefix
     * to indicate any classes may match).</p>
     *
     * @return The list of matching service classes in the lookup
     */
    Class[] getServiceTypes(ServiceTemplate tmpl, String prefix);

    /**
     * <p>Locates the a service based on it's class.</p>
     * <p/>
     * This is a convenience method for finding a service without having to create
     * a lookup template. In most cases, we'll just want to find the a service
     * implementing an interface, letting the service lookup do all the work.
     * </p>
     *
     * @param type The class defining the type of service you want.
     * @return An object guaranteed to implement the search type,
     *         or null if none are found
     */
    Object lookup(Class type);

    /**
     * <p>Locates a service that matches the given service template.</p>
     * <p>There are no
     * guarantees about which service is returned as long as it matches the template
     * (repeated calls may return the same matching service or different ones).
     * </p><p>
     * Note: The lookup returns the service (serviceItem.service), not the ServiceItem.
     * </p>
     *
     * @param tmpl The lookup template to use
     * @return The matching service (e.g. serviceItem.service)
     */
    Object lookup(ServiceTemplate tmpl);

    /**
     * <p>Locate services that matche the given service template.</p>
     * <p>In the case
     * where maxMatches is smaller than the number of
     * matching items, there are no guarantees about which
     * subset of services is returned as long as they match the template
     * (repeated calls may return the same matching set of services
     * or different ones).
     * </p>
     * <p/>
     * The items array in the returned matches is guaranteed to never be null, although
     * it may contain a null entry.
     * </p>
     *
     * @param tmpl       The lookup template to use
     * @param maxMatches The maximum number of matches to return
     * @return The matching services
     */
    ServiceMatches lookup(ServiceTemplate tmpl, int maxMatches);

    /**
     * <p>Register a service event listener for notification of lookup changes.</p>
     * <p>Only transitions matching the given template and transition type will
     * cause a notification (multiple transition types can be bit-wise OR'd together).
     * The generated event is a ServiceEvent.</p>
     *
     * @param tmpl        The lookup template to indicate what services to watch
     * @param transitions The transition types to watch (multiple
     *                    types may be bit-wise OR'd together)
     * @param listener    The listener to receive events
     * @return The event registration object used to manage the registration
     */
    EventRegistration notifyRegister(ServiceTemplate tmpl,
                                     int transitions,
                                     EventListener listener);

    /**
     * <p>Register a service with the lookup.</p>
     * <p>The item contains all information relevant
     * to the registration. New registrations should leave the item's ServiceID null
     * so that the lookup can assign a new, unique ID. Previously registered services
     * may be re-registered with the new item replacing the old one. Items are identified
     * by service ID so you can easily replace any registration by reusing service IDs.
     * It is preferable to replace dissimilar services by cancelling the previous service
     * registration and registering a new service item with null ServiceID.</p>
     *
     * @param item The item to register
     * @return The lookup registration object used to manage the registration
     */
    ServiceRegistration register(ServiceItem item);
}
