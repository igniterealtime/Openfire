/**
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
 * Generates unique IDs.
 *
 * @author Iain Shigeoka
 */
public class IDFactory {
    
    private String prefix;
    private long count;

    /**
     * <p>Create the ID factory using the given prefix for all generated id's.</p>
     *
     * @param prefix The unique string prefix to all id's for this factory
     */
    public IDFactory(String prefix) {
        this.prefix = prefix;
    }

    /**
     * <p>Create the ID factory using no prefix for generated id's.</p>
     */
    public IDFactory() {
    }

    /**
     * Obtain the string representation of this id.
     *
     * @return The string representation of the id
     */
    public String getID() {
        String id;
        if (prefix == null) {
            id = Long.toHexString(count++);
        }
        else {
            id = prefix + Long.toHexString(count++);
        }
        return id;
    }

    public String toString() {
        return getID();
    }
}
