/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    @Override
    protected String getPropertySuffix() {
        return "bot";
    }

    @Override
    protected Collection<Class> getBuiltInInterceptorClasses() {
        return Arrays.asList((Class) TrafficMonitor.class, (Class) UserInterceptor.class);
    }
}
