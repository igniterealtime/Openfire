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
import org.jivesoftware.net.AcceptPolicy;

/**
 * Performs a basic logical NOT evaluation on a child policy (e.g.
 * turns true to false and false to true).<p>
 *
 * This policy is useful for combining simpler policies to create
 * complex policy decisions.
 *
 * @author Iain Shigeoka
 */
public class NotPolicy implements AcceptPolicy {

    private AcceptPolicy pol;

    /**
     * Create an NOT policy for the given policy.
     *
     * @param policy The policy that will be NOT'd
     */
    public NotPolicy(AcceptPolicy policy){
        pol = policy;
    }

    public boolean evaluate(Connection connection) {
        return !pol.evaluate(connection);
    }
}
