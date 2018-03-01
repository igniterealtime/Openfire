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
 * Exception used for representing that the nickname used by the user is not acceptable. The reason
 * for this could be that the user has previously registered with the room and the room is
 * configured so that registered users must join using the reserved nickname. A 406 error code is 
 * returned to the user that requested the invalid operation.
 *
 * @author Gaston Dombiak
 */
public class NotAcceptableException extends Exception {

    private static final long serialVersionUID = 1L;

    private Throwable nestedThrowable = null;

    public NotAcceptableException() {
        super();
    }

    public NotAcceptableException(String msg) {
        super(msg);
    }

    public NotAcceptableException(Throwable nestedThrowable) {
        this.nestedThrowable = nestedThrowable;
    }

    public NotAcceptableException(String msg, Throwable nestedThrowable) {
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
