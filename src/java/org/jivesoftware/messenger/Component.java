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

package org.jivesoftware.messenger;

import org.xmpp.packet.Packet;

/**
 * Interface for Components.
 *
 * @see ComponentManager
 * @author Derek DeMoro
 */
public interface Component {

    /**
     * Processes an incoming packet addressed to this component.
     *
     * @param packet the packet.
     */
    void processPacket(Packet packet);
}
