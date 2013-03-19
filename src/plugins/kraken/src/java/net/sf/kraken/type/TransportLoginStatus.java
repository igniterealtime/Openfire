/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.type;

/**
 * An enumeration for different login statuses.
 *
 * This represents a progression of login statuses to a legacy network.
 *
 * @author Daniel Henninger
 */
public enum TransportLoginStatus {

    /**
     * Not logged in - not logged into the remote service at all
     */
    LOGGED_OUT,

    /**
     * Currently logging in - in the process of logging into the remote service
     */
    LOGGING_IN,

    /**
     * Logged in - active session that should be completely functional
     */
    LOGGED_IN,

    /**
     * Logging out - in the process of logging out of the remote service
     */
    LOGGING_OUT,

    /**
     * Disconnected - automatically disconnected for some reason, similar to LOGGED_OUT
     */
    DISCONNECTED,

    /**
     * Reconnecting - in the process of automatically reconnecting, similar to LOGGING_IN
     */
    RECONNECTING

}
