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

import junit.framework.TestCase;

/**
 * Simple test of the int enum class.
 *
 * @author Iain Shigeoka
 */
public class IntEnumTest extends TestCase {

    /**
     * Create a test case with a given name.
     *
     * @param name The name of the test
     */
    public IntEnumTest (String name){
        super(name);
    }

    static public class TestIntEnum extends IntEnum{
        public TestIntEnum(String name, int value){
            super(name,value);
            register(this);
        }
        public static TestIntEnum getTypeFromInt(int value){
            return (TestIntEnum) getEnumFromInt(TestIntEnum.class,value);
        }
    }
    /**
     * Tests the IntEnum's enforcement of unique int values for each enum type
     */
    public void testStaticEnumUniqueEnforcement(){
        IntEnum e = new IntEnum("plain",1);
        IntEnum.register(e);
        new TestIntEnum("test",1); // auto registers the same value - does it clash with super class?
        assertEquals("plain",IntEnum.getEnumFromInt(IntEnum.class,1).getName());
        assertEquals("test",TestIntEnum.getTypeFromInt(1).getName());
    }
}