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
 * Performs a basic logical AND evaluation on child policies (e.g. both must
 * evaluate to true in order for this policy to evaluate true).<p>
 *
 * This policy is useful for combining simpler policies to create
 * complex policy decisions. The comparison is done using the logical
 * AND operation so if the first policy evaluates to false, the second
 * policy is not evaluated.
 *
 * @author Iain Shigeoka
 */
public class AndPolicy implements AcceptPolicy {

    private AcceptPolicy policy1;
    private AcceptPolicy policy2;

    /**
     * <p>Create an AND policy with the given two child policies.</p>
     *
     * @param firstPolicy The first policy that will be evaluated
     * @param secondPolicy The first policy that will be evaluated
     */
    public AndPolicy(AcceptPolicy firstPolicy, AcceptPolicy secondPolicy){
        policy1 = firstPolicy;
        policy2 = secondPolicy;
    }

    public boolean evaluate(Connection connection) {
        return policy1.evaluate(connection) && policy2.evaluate(connection);
    }
}
