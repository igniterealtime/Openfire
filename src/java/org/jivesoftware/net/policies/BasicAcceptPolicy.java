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

package org.jivesoftware.net.policies;

import org.jivesoftware.net.AcceptPolicy;
import org.jivesoftware.net.Connection;

/**
 * The simplest possible accept policy that either accepts or rejects
 * all connections.
 *
 * @author Iain Shigeoka
 */
public class BasicAcceptPolicy implements AcceptPolicy {

    private boolean accept;

    /**
     * Create a basic accept policy that either accepts or denies all
     * incoming connections.
     *
     * @param alwaysAccept True if the policy should accept all connections
     */
    public BasicAcceptPolicy(boolean alwaysAccept){
        accept = alwaysAccept;
    }

    public boolean evaluate(Connection connection) {
        return accept;
    }
}
