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
 * Manages the packet interceptors that will be invoked every time the workgroup's chatbot
 * receives or sends a packet. This includes packets for joining the queue as well as for
 * gathering user information. Therefore, rejection of packets should be done with extremely
 * caution to ensure the proper performance of the chatbot.
 *
 * @author Gaston Dombiak
 */
public class ChatbotInterceptorManager extends InterceptorManager {

    private static InterceptorManager instance = new ChatbotInterceptorManager();

    /**
     * Returns a singleton instance of ChatbotInterceptorManager.
     *
     * @return an instance of ChatbotInterceptorManager.
     */
    public static InterceptorManager getInstance() {
        return instance;
    }

    protected String getPropertySuffix() {
        return "bot";
    }

    protected Collection<Class> getBuiltInInterceptorClasses() {
        return Arrays.asList((Class) TrafficMonitor.class, (Class) UserInterceptor.class);
    }
}
