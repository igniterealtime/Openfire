/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.http;

import javax.servlet.AsyncContext;

/**
 * Listens for HTTP binding session events.
 *
 * @author Alexander Wenckus
 */
public interface SessionListener {

    /**
     * A connection was opened.
     *
     * @param context The servlet servlet context of the BOSH request that triggered this event.
     * @param session the session for which a new connection was opened.
     * @param connection the connection that was just opened.
     */
    default void connectionOpened( AsyncContext context, HttpSession session, HttpConnection connection ) {};

    /**
     * A connection was opened.
     *
     * @param session the session for which a new connection was opened.
     * @param connection the connection that was just opened.
     * @deprecated Replaced by {@link #connectionOpened(AsyncContext, HttpSession, HttpConnection)}
     */
    @Deprecated // TODO Remove in or after 4.4.0 release.
    default void connectionOpened( HttpSession session, HttpConnection connection ) {};

    /**
     * A connection was closed.
     *
     * @param context The servlet servlet context of the BOSH request that triggered this event.
     * @param session The session of which a connection was closed.
     * @param connection the connection that was closed.
     *
     */
    default void connectionClosed( AsyncContext context, HttpSession session, HttpConnection connection ) {};

    /**
     * A connection was closed.
     *
     * @param session The session of which a connection was closed.
     * @param connection the connection that was closed.
     * @deprecated Replaced by {@link #connectionClosed(AsyncContext, HttpSession, HttpConnection)}
     */
    @Deprecated // TODO Remove in or after 4.4.0 release.
    default void connectionClosed( HttpSession session, HttpConnection connection ) {};

    /**
     * Called before an {@link HttpSession} is created for a given http-bind web request
     *
     * @param context The servlet servlet context of the BOSH request that triggered this event.
     */
    default void preSessionCreated( AsyncContext context ) {};

    /**
     * Called when an {@link HttpSession} has been created for a given http-bind web request
     *
     * @param context The servlet servlet context of the BOSH request that triggered this event.
     * @param session The newly created session.
     */
    default void postSessionCreated( AsyncContext context, HttpSession session) {};

    /**
     * A session ended.
     *
     * @param session the session that was closed.
     */
    default void sessionClosed( HttpSession session ) {};
}
