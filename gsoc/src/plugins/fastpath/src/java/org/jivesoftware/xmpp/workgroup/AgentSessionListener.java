/**
 * $RCSfile$
 * $Revision: 18406 $
 * $Date: 2005-02-07 14:32:46 -0800 (Mon, 07 Feb 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup;

/**
 * <p>Implement to listen for changes in the contents of an agent session list.</p>
 * <p>Many entities will need to monitor agent session lists for membership changes.
 * Implementing this interface allows an object to register for callbacks
 * when agent lists change.</p>
 *
 * @author Derek DeMoro
 */
public interface AgentSessionListener {
    /**
     * <p>Called after the given agent session is added to the list.</p>
     *
     * @param session The session that was added
     */
    void notifySessionAdded(AgentSession session);

    /**
     * <p>Called after the given agent session is removed from the list.</p>
     *
     * @param session The session that was removed
     */
    void notifySessionRemoved(AgentSession session);
}
