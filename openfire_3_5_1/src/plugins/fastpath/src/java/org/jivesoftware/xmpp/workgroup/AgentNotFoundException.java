/**

 * $RCSfile$

 * $Revision: 18995 $

 * $Date: 2005-06-06 22:50:17 -0700 (Mon, 06 Jun 2005) $

 *

 * Copyright (C) 2004-2008 Jive Software. All rights reserved.

 *

 * This software is the proprietary information of Jive Software.

 * Use is subject to license terms.

 */


package org.jivesoftware.xmpp.workgroup;


import java.io.PrintStream;
import java.io.PrintWriter;


/**
 * <p>Indicates a workgroup agent was not found.</p>
 *
 * @author Derek DeMoro
 */

public class AgentNotFoundException extends Exception {

    private Throwable nestedThrowable = null;

    public AgentNotFoundException() {
        super();
    }

    public AgentNotFoundException(String msg) {
        super(msg);
    }

    public AgentNotFoundException(Throwable nestedThrowable) {
        this.nestedThrowable = nestedThrowable;
    }

    public AgentNotFoundException(String msg, Throwable nestedThrowable) {
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
