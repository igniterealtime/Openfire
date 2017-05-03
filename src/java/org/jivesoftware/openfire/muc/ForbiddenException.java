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

package org.jivesoftware.openfire.muc;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Exception used for representing that the requested operation is forbidden for the user in 
 * the MUCRoom. There are many reasons why a forbidden error could occur such as: a banned user
 * tries to join a room where he/she is an outcast. A 403 error code is returned to the user that 
 * requested the invalid operation.
 *
 * @author Gaston Dombiak
 */
public class ForbiddenException extends Exception {

    private static final long serialVersionUID = 1L;

    private Throwable nestedThrowable = null;

    public ForbiddenException() {
        super();
    }

    public ForbiddenException(String msg) {
        super(msg);
    }

    public ForbiddenException(Throwable nestedThrowable) {
        this.nestedThrowable = nestedThrowable;
    }

    public ForbiddenException(String msg, Throwable nestedThrowable) {
        super(msg);
        this.nestedThrowable = nestedThrowable;
    }

    @Override
	public void printStackTrace() {
        super.printStackTrace();
        if (nestedThrowable != null) {
            nestedThrowable.printStackTrace();
        }
    }

    @Override
	public void printStackTrace(PrintStream ps) {
        super.printStackTrace(ps);
        if (nestedThrowable != null) {
            nestedThrowable.printStackTrace(ps);
        }
    }

    @Override
	public void printStackTrace(PrintWriter pw) {
        super.printStackTrace(pw);
        if (nestedThrowable != null) {
            nestedThrowable.printStackTrace(pw);
        }
    }
}
