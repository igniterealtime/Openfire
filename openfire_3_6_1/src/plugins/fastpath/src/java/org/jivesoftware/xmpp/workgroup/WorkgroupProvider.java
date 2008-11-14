/**
 * $RCSfile$
 * $Revision: 19009 $
 * $Date: 2005-06-09 07:17:26 -0700 (Thu, 09 Jun 2005) $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup;

import org.xmpp.packet.IQ;

/**
 * Implement this class to add your own Workgroup IQ Provider. This allows for clean
 * separation of logic from the main WorkgroupIQHandler and for simple task execution.
 * To register your provider, please use the WorkgorupProviderManager.#addWorkgroupProvider.
 *
 * @see WorkgroupProviderManager
 * @see org.jivesoftware.openfire.fastpath.providers.ChatNotes
 * @see org.jivesoftware.openfire.fastpath.providers.AgentHistory
 */
public interface WorkgroupProvider {

    /**
     * Return true to take responsibility for this packet. If true is returned,
     * #executeGet will be executed.
     *
     * @param packet the packet to check.
     * @return true to handle this packet, otherwise return false.
     */
    boolean handleGet(IQ packet);

    /**
     * Return true to take responsibility for this packet. If true is returned,
     * #executeSet will be executed.
     *
     * @param packet the packet to check.
     * @return true to handle this packet, otherwise return false.
     */
    boolean handleSet(IQ packet);

    /**
     * Handle the IQ packet along with the Workgroup that it was sent to. This packet was
     * sent as type IQ.GET.
     *
     * @param packet    the IQ packet to handle.
     * @param workgroup the Workgroup it was sent to.
     */
    void executeGet(IQ packet, Workgroup workgroup);

    /**
     * Handle the IQ packet along with the Workgroup that it was sent to.
     *
     * @param packet    the IQ packet to handle. This packet was sent as type
     *                  IQ.SET.
     * @param workgroup the Workgroup it was sent to.
     */
    void executeSet(IQ packet, Workgroup workgroup);
}
