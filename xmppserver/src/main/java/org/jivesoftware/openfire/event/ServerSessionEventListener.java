/*
 * Copyright (C) 2019 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.event;

import org.jivesoftware.openfire.session.Session;

/**
 * Interface to listen for  server session events (s2s). Use the
 * {@link ServerSessionEventDispatcher#addListener(ServerSessionEventListener)}
 * method to register for events.
 *
 * @author Manasse Ngudia manasse@mnsuccess.com
 */
public interface ServerSessionEventListener {

    /**
     * Notification event indicating that a server has connected with the server. 
     * @param session the connected session of a server
     */
    void sessionCreated( Session session );

    /**
     * A connected session of a server was destroyed.
     *
     * @param session the connected session of  a server.
     */
    void sessionDestroyed( Session session );

}
