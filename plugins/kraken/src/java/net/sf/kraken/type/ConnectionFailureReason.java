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
 * An enumeration for different failed connection statuses.
 *
 * When a failure to connect to a legacy service occurs, this represents a reason why the failure occurred.  This
 * includes both login time problems, and "out of the blue" problems.
 *
 * @author Daniel Henninger
 */
public enum ConnectionFailureReason {

    /**
     * There is no known problem currently.
     */
    NO_ISSUE,

    /**
     * Bad username or password.
     */
    USERNAME_OR_PASSWORD_INCORRECT,

    /**
     * Can not connect to host.
     */
    CAN_NOT_CONNECT,

    /**
     * Locked out by remote system.
     */
    LOCKED_OUT,

    /**
     * We don't really know why the failure occurred.  =(
     */
    UNKNOWN

}
