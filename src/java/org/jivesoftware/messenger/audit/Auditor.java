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

package org.jivesoftware.messenger.audit;

import org.jivesoftware.messenger.XMPPPacket;

/**
 * <p>Use auditors to audit events and messages on the server.</p>
 * <p/>
 * <p>All events and messages are sent to the auditor for recording.
 * The auditor will determine if auditing should take place, and what
 * to do with the data.</p>
 *
 * @author Iain Shigeoka
 */
public interface Auditor {

    /**
     * Audit an XMPP packet.
     *
     * @param packet the packet being audited
     */
    void audit(XMPPPacket packet);

    /**
     * Audit any packet that was dropped (undeliverables, etc).
     *
     * @param packet the packet that was dropped.
     */
    //void auditDroppedPacket(XMPPPacket packet);

    /**
     * Audit a non-packet event.
     *
     * @param event the event being audited.
     */
    //void audit(AuditEvent event);

    /**
     * Prepares the auditor for system shutdown.
     */
    void stop();

    /**
     * Returns the number of queued packets that are still in memory and need to be saved to a
     * permanent store.
     *
     * @return the number of queued packets that are still in memory.
     */
    int getQueuedPacketsNumber();
}