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

package org.jivesoftware.messenger;

/**
 * <p>Implements the nodeprep stringprep profile according to
 * the XMPP specification.</p>
 *
 * @author Iain Shigeoka
 */
public class NodePrep {
    /**
     * <p>Returns the given user name (node) according to the resource
     * prep profile. See the XMPP 1.0 specification for specifics.</p>
     *
     * @param username The username to prepare
     * @return The prepared name
     */
    public static String prep(String username) {
        String prep = username.trim().toLowerCase();
        return prep;
    }
}
