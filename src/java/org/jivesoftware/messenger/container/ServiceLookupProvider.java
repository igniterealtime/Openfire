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
 * <p>Provides the service lookup factory with a producer of service
 * lookups.</p>
 *
 * @author Iain Shigeoka
 */
public interface ServiceLookupProvider {
    /**
     * <p>Provide a lookup on demand. Implementations should cache
     * the result (or use a singleton). The factory will call this
     * method every time it has a service lookup request (the factory
     * does no caching).</p>
     *
     * @return The lookup from this provider
     * @throws UnauthorizedException If the caller does not have permission
     *                               to access the service lookup
     */
    ServiceLookup getServiceLookup() throws UnauthorizedException;
}
