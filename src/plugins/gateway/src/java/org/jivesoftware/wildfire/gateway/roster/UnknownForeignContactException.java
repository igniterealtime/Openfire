/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.roster;

import java.text.MessageFormat;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * @author Noah Campbell
 */
public class UnknownForeignContactException extends Exception {

    /** The args. */
    private final Object[] args;
    
    /**
     * Constructs a new <code>UnknownForeignContactException</code>.
     */
    public UnknownForeignContactException() {
        super();
        args = new Object[0];
    }

    /**
     * Construct a new <code>UnknownForeignContactException</code>.
     *
     * @param message The message key.
     * @param args Any arguments that are required for the message.
     */
    public UnknownForeignContactException(String message, Object...args) {
        super(message);
        this.args = args;
    }

    /**
     * Construct a new <code>UnknownForeignContactException</code>.
     *
     * @param message The message key.
     * @param cause The cause of the exception, if this is a wrapped exception.
     * @param args Any arguments that are required for the message.
     */
    public UnknownForeignContactException(String message, Throwable cause, Object...args) {
        super(message, cause);
        this.args = args;
    }

    /**
     * Construct a new <code>UnknownForeignContactException</code>.
     *
     * @param cause
     */
    public UnknownForeignContactException(Throwable cause) {
        super(cause);
        args = new Object[0];
    }
}