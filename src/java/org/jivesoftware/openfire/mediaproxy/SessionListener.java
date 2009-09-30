/**
 * $Revision$
 * $Date$
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

package org.jivesoftware.openfire.mediaproxy;

/**
 * Listener for media proxy session events.
 *
 * @author Thiago Camargo
 */
public interface SessionListener {

    /**
     * A media proxy session was closed as a result of normal termination or because
     * the max idle time elapsed.
     *
     * @param session the session that closed.
     */
    public void sessionClosed(MediaProxySession session);

}