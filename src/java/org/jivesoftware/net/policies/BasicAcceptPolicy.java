/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.net.policies;

import org.jivesoftware.net.AcceptPolicy;
import org.jivesoftware.net.Connection;
import org.jivesoftware.net.AcceptPolicy;
import org.jivesoftware.net.Connection;

/**
 * <p>The simplest possible accept policy that either accepts or rejects
 * all connections.</p>
 *
 * @author Iain Shigeoka
 */
public class BasicAcceptPolicy implements AcceptPolicy {

    private boolean accept;

    /**
     * <p>Create a basic accept policy that either accepts or denies all
     * incoming connections.</p>
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
