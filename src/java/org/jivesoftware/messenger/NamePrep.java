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
 * <p>Implements the nameprep stringprep profile according to
 * the XMPP specification.</p>
 *
 * @author Iain Shigeoka
 */
public class NamePrep {
    /**
     * <p>Returns the given domain name according to the resource
     * prep profile. See the XMPP 1.0 specification for specifics.</p>
     *
     * @param name The domain name to prepare
     * @return The prepared name
     */
    public static String prep(String name) {
        String prep = name.trim().toLowerCase();
        return prep;
    }
}
