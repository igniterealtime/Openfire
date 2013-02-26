/**
 * $RCSfile$
 * $Revision: 1660 $
 * $Date: 2005-07-21 00:05:27 -0300 (Thu, 21 Jul 2005) $
 *
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

package org.jivesoftware.openfire.plugin.presence;

import org.xmpp.packet.Presence;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Abstract class for the different ways to provide information about user presences.
 *
 * @author Gaston Dombiak
 */
abstract class PresenceInfoProvider {

    /**
     * Sends information to the sender of the http request about the presence of a user.
     *
     * @param httpServletRequest the http request.
     * @param httpServletResponse the http response.
     * @param presence the presence of the user or <tt>null</tt> if the user is offline.
     * @throws IOException If an error occured while sending the information.
     */
    public abstract void sendInfo(HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse, Presence presence) throws IOException;

    /**
     * Informs the sender of the http request that the user presence is not available. This may
     * happen if the user does not exist or if the user that made the request is not allowed to
     * see the presence of the requested user.
     *
     * @param httpServletRequest the http request.
     * @param httpServletResponse the http response.
     * @throws IOException If an error occured while sending the information.
     */
    public abstract void sendUserNotFound(HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) throws IOException;
}
