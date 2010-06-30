/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
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

/**
 * Listens for HTTP binding session events.
 *
 * @author Alexander Wenckus
 */
public interface SessionListener {

    /**
     * A connection was opened.
     *
     * @param session the session.
     * @param connection the connection.
     */
    public void connectionOpened(HttpSession session, HttpConnection connection);

    /**
     * A conneciton was closed.
     *
     * @param session the session.
     * @param connection the connection.
     */
    public void connectionClosed(HttpSession session, HttpConnection connection);

    /**
     * A session ended.
     *
     * @param session the session.
     */
    public void sessionClosed(HttpSession session);
}