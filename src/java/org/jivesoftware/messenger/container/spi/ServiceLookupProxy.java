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

package org.jivesoftware.messenger.container.spi;

import org.jivesoftware.messenger.container.EventListener;
import org.jivesoftware.messenger.container.EventRegistration;
import org.jivesoftware.messenger.container.ServiceID;
import org.jivesoftware.messenger.container.ServiceItem;
import org.jivesoftware.messenger.container.ServiceLookup;
import org.jivesoftware.messenger.container.ServiceMatches;
import org.jivesoftware.messenger.container.ServiceRegistration;
import org.jivesoftware.messenger.container.ServiceTemplate;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.Permissions;

/**
 * Authorization proxy for service lookups.
 *
 * @author Iain Shigeoka
 */
public class ServiceLookupProxy implements ServiceLookup {

    private AuthToken authToken;
    private Permissions permissions;
    private ServiceLookup lookup;

    /**
     * Create a lookup security proxy
     *
     * @param authToken   the authentication token for the user.
     * @param permissions the permissions of the user.
     * @param lookup      the lookup being proxied.
     */
    public ServiceLookupProxy(AuthToken authToken, Permissions permissions, ServiceLookup lookup) {
        this.authToken = authToken;
        this.permissions = permissions;
        this.lookup = lookup;
    }

    public ServiceID getServiceID() {
        return lookup.getServiceID();
    }

    public Class[] getServiceTypes(ServiceTemplate tmpl, String prefix) {
        return lookup.getServiceTypes(tmpl, prefix);
    }

    public Object lookup(Class type) {
        return lookup.lookup(type);
    }

    public Object lookup(ServiceTemplate tmpl) {
        return lookup.lookup(tmpl);
    }

    public ServiceMatches lookup(ServiceTemplate tmpl, int maxMatches) {
        return lookup.lookup(tmpl, maxMatches);
    }

    public EventRegistration notifyRegister(ServiceTemplate tmpl, int transitions, EventListener listener) {
        return lookup.notifyRegister(tmpl, transitions, listener);
    }

    public ServiceRegistration register(ServiceItem item) {
        return lookup.register(item);
    }
}