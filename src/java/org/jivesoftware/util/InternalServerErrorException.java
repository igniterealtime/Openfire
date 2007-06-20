/**
 * $Revision:$
 * $Date:$
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.util;

/**
 * A generic exception for when errors occur in the system.
 */
public class InternalServerErrorException extends RuntimeException {
    public InternalServerErrorException(String s, Exception e) {
        super(s, e);
    }
}
