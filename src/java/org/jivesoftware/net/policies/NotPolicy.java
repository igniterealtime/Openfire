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

/**
 * <p>Performs a basic logical NOT evaluation on a child policy (e.g.
 * turns true to false and false to true).</p>
 *
 * <p>This policy is useful for combining simpler policies to create
 * complex policy decisions.</p>
 *
 * @author Iain Shigeoka
 */
public class NotPolicy implements AcceptPolicy {

    private AcceptPolicy pol;

    /**
     * <p>Create an NOT policy for the given policy.</p>
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
