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
package org.jivesoftware.net;



/**
 * <p>Defines the rules that determine whether a connection should be
 * accepted or rejected based exclusively on the initial connection
 * parameters.</p>
 *
 * <p>Accept policies allow rejection of incoming connections based on IP
 * address (creating white and black lists), time of day, rate, etc. without
 * any knowledge of the underlying protocol. In most cases, a simple white/black
 * list of IP addresses in the global policy is sufficient.</p>
 *
 * @author Iain Shigeoka
 */
public interface AcceptPolicy {

    /**
     * <p>Evaluate if the given connection should be accepted or rejected.</p>
     *
     * <p>For AcceptPorts the accept policy should examine it's rules
     * for accepting and declining. If there is no corresponding rule
     * the global accept policy should be applied. If no rules for the
     * global accept policy apply, the connection should be rejected.</p>
     *
     * @param connection The connection to evaluate
     * @return True if the connection should be accepted, false otherwise
     */
    boolean evaluate(Connection connection);
}
