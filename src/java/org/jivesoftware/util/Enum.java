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
 * A type safe enumeration object. Used for indicating distinct states
 * in a generic manner. Most child classes should extend Enum and
 * create static instances.
 *
 * @author Iain Shigeoka
 */
public class Enum {
    private String name;

    protected Enum(String name) {
        this.name = name;
    }

    /**
     * Returns the name of the enum.
     *
     * @return the name of the enum.
     */
    public String getName() {
        return name;
    }

    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        else if ((this.getClass().isInstance(object)) && name.equals(((Enum)object).name)) {
            return true;
        }
        else {
            return false;
        }
    }

    public int hashCode() {
        return name.hashCode();
    }

    public String toString() {
        return name;
    }
}
