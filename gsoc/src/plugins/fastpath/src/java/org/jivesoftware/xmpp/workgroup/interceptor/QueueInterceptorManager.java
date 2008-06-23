/**
 * $RCSfile$
 * $Revision: 19268 $
 * $Date: 2005-07-08 18:26:08 -0700 (Fri, 08 Jul 2005) $
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
 * Manages the packet interceptors that will be invoked every time a user tries to join a queue.
 * If the user is using a chatbot for joining the queue then the intercepted packets will be of
 * type Message. However, when users are using the webclient (i.e. a Workgroup compliant client)
 * the intercepted packets will be of type IQ.
 *
 * @author Gaston Dombiak
 */
public class QueueInterceptorManager extends InterceptorManager {

    private static InterceptorManager instance = new QueueInterceptorManager();

    /**
     * Returns a singleton instance of QueueInterceptorManager.
     *
     * @return an instance of QueueInterceptorManager.
     */
    public static InterceptorManager getInstance() {
        return instance;
    }

    protected String getPropertySuffix() {
        return "queue";
    }

    protected Collection<Class> getBuiltInInterceptorClasses() {
        return Arrays.asList((Class)TrafficMonitor.class, (Class)UserInterceptor.class);
    }
}
