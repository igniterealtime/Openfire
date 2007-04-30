/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.util;

import java.util.*;

/**
 * <p>A type safe enumeration object that is keyed by an Int
 * value for switch statements and storage in DBs.</p>
 * <p/>
 * <p>Used for indicating distinct states in a generic manner
 * where each enum should have an associated int value. The
 * given int should be unique for each enum value as hashCode
 * and equals depends solely on the int value given. Most
 * child classes should extend IntEnum and create static instances.</p>
 *
 * @author Iain Shigeoka
 */
public class IntEnum extends Enum {

    private int value;
    protected static Hashtable enumTypes = new Hashtable();

    protected IntEnum(String name, int val) {
        super(name);
        this.value = val;
    }

    /**
     * Returns the int value associated with the enum.
     *
     * @return the int value of the enum.
     */
    public int getValue() {
        return value;
    }

    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        else if ((this.getClass().isInstance(object)) && value == (((IntEnum)object).value)) {
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * <p>Checks in an enum for use in the getEnumFromInt() method.</p>
     *
     * @param enumeration The enum to be registered
     */
    protected static void register(IntEnum enumeration) {
        Map enums = (Map)enumTypes.get(enumeration.getClass());
        if (enums == null) {
            enums = new HashMap<Integer,Object>();
            enumTypes.put(enumeration.getClass(), enums);
        }
        enums.put(enumeration.getValue(), enumeration);
    }

    /**
     * <p>Obtain the enum associated with the given value.</p>
     * <p>Values must be registered earlier using the register() method.</p>
     *
     * @param value the value to lookup the enum for
     * @return The associated enum or null if no matching enum exists
     */
    protected static IntEnum getEnumFromInt(Class enumClass, int value) {
        Map enums = (Map)enumTypes.get(enumClass);
        if (enums != null) {
            return (IntEnum)enums.get(value);
        }
        return null;
    }

    public int hashCode() {
        return value;
    }

    public String toString() {
        return Integer.toString(value) + " " + super.toString();
    }
}