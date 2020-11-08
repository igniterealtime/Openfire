/*
 * Copyright (C) 2020 Ignite Realtime Foundation. All rights reserved.
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
 * Exception used for representing unavailability of FMUC (XEP-0289). The resulting error is expected to be delivered
 * to the originating entity in the form of a presence stanza, possibly containing a human-readable 'reject' message.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="https://xmpp.org/extensions/xep-0289.html">XEP-0289: Federated MUC for Constrained Environments</a>
 */
public class FMUCException extends Exception {

    private static final long serialVersionUID = 1L;

    private Throwable nestedThrowable = null;

    public FMUCException() {
        super();
    }

    public FMUCException( String reject ) {
        super( reject );
    }

    public FMUCException( Throwable nestedThrowable ) {
        this.nestedThrowable = nestedThrowable;
    }

    public FMUCException( String reject, Throwable nestedThrowable ) {
        super(reject);
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
