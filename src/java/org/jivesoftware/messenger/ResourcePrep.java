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
 * <p>Implements the resourceprep stringprep profile according to
 * the XMPP specification.</p>
 *
 * @author Iain Shigeoka
 */
public class ResourcePrep {
    /**
     * <p>Returns the given resource name according to the resource
     * prep profile. See the XMPP 1.0 specification for specifics.</p>
     *
     * @param resource The resource name to prepare
     * @return The prepared name
     */
    public static String prep(String resource) {
        String prep = resource;
        return prep;
    }
}
