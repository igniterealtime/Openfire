/*
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.container;

/**
 * Thrown if a service encounters an Entry class that does not
 * follow the Entry class requirements.
 *
 * @author Iain Shigeoka
 */
public class UnuseableEntryException extends RuntimeException {

    /**
     * Creates an exception without a message.
     */
    public UnuseableEntryException() {
        super();
    }

    /**
     * Create an exception with a message.
     *
     * @param msg the message to include with the exception.
     */
    public UnuseableEntryException(String msg) {
        super(msg);
    }
}
