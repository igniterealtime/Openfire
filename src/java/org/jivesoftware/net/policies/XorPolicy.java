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
 * Performs a bitwise logical XOR (exclusive OR) evaluation on
 * child policies (e.g. if both policies evaluate to true or false
 * the XOR result is false).<p>
 *
 * This policy is useful for combining simpler policies to create
 * complex policy decisions. The comparison is done using the bitwise
 * XOR operation so both policies will always be evaluated.
 *
 * @author Iain Shigeoka
 */
public class XorPolicy implements AcceptPolicy {

    private AcceptPolicy policy1;
    private AcceptPolicy policy2;

    /**
     * Create an AND policy with the given two child policies.
     *
     * @param firstPolicy The first policy that will be evaluated
     * @param secondPolicy The first policy that will be evaluated
     */
    public XorPolicy(AcceptPolicy firstPolicy, AcceptPolicy secondPolicy){
        policy1 = firstPolicy;
        policy2 = secondPolicy;
    }

    public boolean evaluate(Connection connection) {
        return policy1.evaluate(connection) ^ policy2.evaluate(connection);
    }
}