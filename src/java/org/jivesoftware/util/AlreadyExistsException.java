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
package org.jivesoftware.util;

/**
 * <p>Flags an exception when something to be created or added already exists.</p>
 * <p>Use this class when it's not worth creating a unique
 * xAlreadyExistsException class, or where the context of
 * the call makes it obvious what type of object was not found.</p>
 *
 * @author Iain Shigeoka
 */
public class AlreadyExistsException extends Exception {

    public AlreadyExistsException() {
    }

    public AlreadyExistsException(String message) {
        super(message);
    }
}
