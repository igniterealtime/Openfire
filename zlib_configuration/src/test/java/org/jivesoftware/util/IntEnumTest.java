/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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