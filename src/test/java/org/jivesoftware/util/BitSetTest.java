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

import java.util.BitSet;

/**
 * Test the standard with an emphasis on making it work with our scheduler.
 *
 * @author Iain Shigeoka
 */
public class BitSetTest extends TestCase {

    /**
     * Create a test case with a given name.
     *
     * @param name The name of the test
     */
    public BitSetTest (String name){
        super(name);
    }

    /**
     * Test storage and retrieval of a bit set
     */
    public void testStorage(){
        BitSet bits = new BitSet();
    }
}