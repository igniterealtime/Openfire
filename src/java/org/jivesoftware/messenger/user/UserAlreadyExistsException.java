/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2002 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.user;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Thrown when attempting to create a user that already exists.
 *
 * @author Iain Shigeoka
 */
public class UserAlreadyExistsException extends Exception {

    private Throwable nestedThrowable = null;

    public UserAlreadyExistsException() {
        super();
    }

    public UserAlreadyExistsException(String msg) {
        super(msg);
    }

    public UserAlreadyExistsException(Throwable nestedThrowable) {
        this.nestedThrowable = nestedThrowable;
    }

    public UserAlreadyExistsException(String msg, Throwable nestedThrowable) {
        super(msg);
        this.nestedThrowable = nestedThrowable;
    }

    public void printStackTrace() {
        super.printStackTrace();
        if (nestedThrowable != null) {
            nestedThrowable.printStackTrace();
        }
    }

    public void printStackTrace(PrintStream ps) {
        super.printStackTrace(ps);
        if (nestedThrowable != null) {
            nestedThrowable.printStackTrace(ps);
        }
    }

    public void printStackTrace(PrintWriter pw) {
        super.printStackTrace(pw);
        if (nestedThrowable != null) {
            nestedThrowable.printStackTrace(pw);
        }
    }
}