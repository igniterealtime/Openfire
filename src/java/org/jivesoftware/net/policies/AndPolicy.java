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
import org.jivesoftware.net.Connection;

/**
 * <p>Performs a basic logical AND evaluation on child policies (e.g. both must
 * evaluate to true in order for this policy to evaluate true).</p>
 *
 * <p>This policy is useful for combining simpler policies to create
 * complex policy decisions. The comparison is done using the logical
 * AND operation so if the first policy evaluates to false, the second
 * policy is not evaluated.</p>
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
