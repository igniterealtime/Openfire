/**
 * $RCSfile$
 * $Revision: 19263 $
 * $Date: 2005-07-08 15:30:05 -0700 (Fri, 08 Jul 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup.interceptor;

import java.util.Arrays;
import java.util.Collection;

/**
 * Manages the packet interceptors that will be invoked when sending an offer to an agent or when
 * an agent accepts or rejects the offer.
 *
 * @author Gaston Dombiak
 */
public class OfferInterceptorManager extends InterceptorManager {

    private static InterceptorManager instance = new OfferInterceptorManager();

    /**
     * Returns a singleton instance of OfferInterceptorManager.
     *
     * @return an instance of OfferInterceptorManager.
     */
    public static InterceptorManager getInstance() {
        return instance;
    }

    protected String getPropertySuffix() {
        return "offer";
    }

    protected Collection<Class> getBuiltInInterceptorClasses() {
        return Arrays.asList((Class) TrafficMonitor.class);
    }
}
