/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.plugin.presence;

import org.xmpp.packet.Presence;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The XMLPresenceProvider provides information about the users presence in XML format.
 * The returned XML format has the following structure:
 *
 *
 *
 * @author Gaston Dombiak
 *
 */
class XMLPresenceProvider extends PresenceInfoProvider {

    public void sendInfo(HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse, Presence presence) {
        //TODO Implement
    }

    public void sendUserNotFound(HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) {
        //TODO Implement
    }
}
