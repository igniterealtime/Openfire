/**
 * $RCSfile$
 * $Revision: 570 $
 * $Date: 2004-12-01 16:00:10 -0300 (Wed, 01 Dec 2004) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.group;

/**
 * Thrown when unable to find or load a group.
 *
 * @author Matt Tucker
 */
public class GroupNotFoundException extends Exception {

    /**
     * Constructs a new exception with null as its detail message. The cause is not
     * initialized, and may subsequently be initialized by a call to
     * {@link #initCause(Throwable) initCause}.
     */
    public GroupNotFoundException() {
        super();
    }

    /**
     * Constructs a new exception with the specified detail message. The cause is
     * not initialized, and may subsequently be initialized by a call to
     * {@link #initCause(Throwable) initCause}.
     *
     * @param message the detail message. The detail message is saved for later
     *      retrieval by the {@link #getMessage()} method.
     */
    public GroupNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.<p>
     *
     * Note that the detail message associated with cause is not automatically incorporated
     * in this exception's detail message.
     *
     * @param message the detail message (which is saved for later retrieval by the
     *      {@link #getMessage()} method).
     * @param cause the cause (which is saved for later retrieval by the
     *      {@link #getCause()} method). (A null value is permitted, and indicates
     *      that the cause is nonexistent or unknown.)
     */
    public GroupNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new exception with the specified cause and a detail message of
     * (cause==null ? null : cause.toString()) (which typically contains the class and
     * detail message of cause). This constructor is useful for exceptions that are
     * little more than wrappers for other throwables (for example,
     * java.security.PrivilegedActionException).
     *
     * @param cause the cause (which is saved for later retrieval by the
     *      {@link #getCause()} method). (A null value is permitted, and indicates
     *      that the cause is nonexistent or unknown.)
     */
    public GroupNotFoundException(Throwable cause) {
        super(cause);
    }
}