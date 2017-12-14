/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
