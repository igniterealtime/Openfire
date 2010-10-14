/**
 * $RCSfile$
 * $Revision: 128 $
 * $Date: 2004-10-25 20:42:00 -0300 (Mon, 25 Oct 2004) $
 *
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

package org.jivesoftware.openfire;

import java.io.PrintStream;
import java.io.PrintWriter;

public class SessionNotFoundException extends Exception {
    private Throwable nestedThrowable = null;

    public SessionNotFoundException() {
        super();
    }

    public SessionNotFoundException(String msg) {
        super(msg);
    }

    public SessionNotFoundException(Throwable nestedThrowable) {
        this.nestedThrowable = nestedThrowable;
    }

    public SessionNotFoundException(String msg, Throwable nestedThrowable) {
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
