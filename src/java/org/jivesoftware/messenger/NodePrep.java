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
