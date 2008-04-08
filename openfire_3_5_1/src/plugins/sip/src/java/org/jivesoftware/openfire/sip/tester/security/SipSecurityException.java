/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.sip.tester.security;

/**
 * This exception is used by SipSecurityManager to indicate failure to provide
 * valid credentials for a given request.
 *
 * @author Emil Ivov <emcho@dev.java.net>
 * @version 1.0
 */
public class SipSecurityException extends Exception {

    public SipSecurityException() {

        this("SipSecurityException");
    }

    public SipSecurityException(String message) {
        super(message);
    }

}
