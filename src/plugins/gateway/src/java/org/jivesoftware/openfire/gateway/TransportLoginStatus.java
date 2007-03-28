/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.gateway;

/**
 * An enumeration for different login statuses.
 *
 * This represents a progression of login statuses to a legacy network.
 *
 * @author Daniel Henninger
 */
public enum TransportLoginStatus {

    /**
     * Not logged in
     */
    LOGGED_OUT,

    /**
     * Currently logging in
     */
    LOGGING_IN,

    /**
     * Logged in
     */
    LOGGED_IN,

    /**
     * Logging out
     */
    LOGGING_OUT

}
