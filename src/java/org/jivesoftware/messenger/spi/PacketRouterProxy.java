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

package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.Permissions;

/**
 * Standard security proxy.
 *
 * @author Iain Shigeoka
 */
public class PacketRouterProxy implements PacketRouter {
    PacketRouter router;
    private org.jivesoftware.messenger.auth.AuthToken authToken;
    private Permissions permissions;

    public PacketRouterProxy(PacketRouter router, AuthToken authToken, Permissions permissions) {
        this.router = router;
        this.authToken = authToken;
        this.permissions = permissions;
    }

    public void route(XMPPPacket packet) {
        router.route(packet);
    }

    public void route(IQ packet) {
        router.route(packet);
    }

    public void route(Message packet) {
        router.route(packet);
    }

    public void route(Presence packet) {
        router.route(packet);
    }

}
