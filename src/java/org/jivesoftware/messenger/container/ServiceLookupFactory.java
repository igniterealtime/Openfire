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

import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.UnauthorizedException;

/**
 * Works with the AuthFactory to create a simple way for classes
 * (especially JSPs) to bootstrap their access to the container system.
 * This factory singleton is static across the entire JVM (technically across
 * the classloader context).
 *
 * @author Iain Shigeoka
 */
public class ServiceLookupFactory {

    /**
     * The provider of service lookups.
     */
    private static ServiceLookupProvider provider = null;

    /**
     * Protect the factory class from being instantiated.
     */
    private ServiceLookupFactory() {
    }

    /**
     * Obtain a service lookup with a given authentication token. You can obtain
     * the token from an AuthFactory.
     *
     * @param token The authentication token representing your system authorization
     * @return The service lookup
     * @throws UnauthorizedException If the caller doesn't have permission to
     *                               invoke this method
     */
    public static ServiceLookup getLookup(AuthToken token)
            throws UnauthorizedException {

        if (token == null || token.isAnonymous()) {
            throw new UnauthorizedException("Auth token not valid");
        }

        if (provider == null) {
            throw new IllegalStateException("Factory not configured with provider");
        }
        return provider.getServiceLookup();
    }

    /**
     * Obtain a service lookup without an authentication token.
     *
     * @return the service lookup invokes this method.
     * @throws UnauthorizedException if the caller does not have permission to obtain a lookup.
     */
    public static ServiceLookup getLookup() throws UnauthorizedException {

        if (provider == null) {
            throw new IllegalStateException("Factory not configured with provider");
        }
        return provider.getServiceLookup();
    }

    /**
     * Sets the provider this factory uses to generate lookups. The provider may only be
     * added once (all subsequent calls are ignored). Calls to the getServiceLookup() methods
     * will fail with an IllegalStateException until a provider is set (no default provider
     * is available).
     *
     * @param slProvider The provider for this factory to use
     */
    public static void setLookupProvider(ServiceLookupProvider slProvider) {
        if (provider == null) {
            provider = slProvider;
        }
    }
}
