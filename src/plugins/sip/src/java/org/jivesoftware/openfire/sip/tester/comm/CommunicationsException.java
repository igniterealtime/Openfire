/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.sip.tester.comm;

/**
 * Title: SIP Register Tester
 * Description:JAIN-SIP Test application
 *
 * @author Thiago Rocha Camargo (thiago@jivesoftware.com)
 */

public class CommunicationsException extends Exception {
    /**
     */
    private boolean isFatal = false;

    public CommunicationsException() {
        this("CommunicationsException");
    }

    public CommunicationsException(String message) {
        this(message, null);
    }

    public CommunicationsException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public CommunicationsException(String message, Throwable cause,
                                   boolean isFatal) {
        super(message, cause);
        setFatal(isFatal);
    }

    // ------------------ is fatal

    /**
     * @return
     * @uml.property name="isFatal"
     */
    public boolean isFatal() {
        return isFatal;
    }

    /**
     * @param isFatal The isFatal to set.
     * @uml.property name="isFatal"
     */
    public void setFatal(boolean isFatal) {
        this.isFatal = isFatal;
    }
}
