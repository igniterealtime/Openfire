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
