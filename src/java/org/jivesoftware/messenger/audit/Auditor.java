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

import org.jivesoftware.messenger.IQ;
import org.jivesoftware.messenger.Message;
import org.jivesoftware.messenger.Presence;
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
     * Audit a message packet.
     *
     * @param packet the packet being audited.
     */
    void audit(Message packet);

    /**
     * Audit a presence packet.
     *
     * @param packet the packet being audited.
     * @param transition the presence transition type from AuditManager.
     */
    void audit(Presence packet, int transition);

    /**
     * Audit an IQ packet.
     *
     * @param packet the packet being audited.
     */
    void audit(IQ packet);

    /**
     * Audit any packet that was dropped (undeliverables, etc).
     *
     * @param packet the packet that was dropped.
     */
    void auditDroppedPacket(XMPPPacket packet);

    /**
     * Audit a non-packet event.
     *
     * @param event the event being audited.
     */
    void audit(AuditEvent event);

    /**
     * Prepares the auditor for system shutdown.
     */
    void close();
}