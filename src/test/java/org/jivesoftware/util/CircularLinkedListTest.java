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
 * Test the CircularLinkedList with an emphasis on making it work with RoundRobinDispatcher.
 *
 * @author Iain Shigeoka
 */
public class CircularLinkedListTest extends TestCase {

    /**
     * Create a test case with a given name.
     *
     * @param name The name of the test
     */
    public CircularLinkedListTest (String name){
        super(name);
    }

    CircularLinkedList list;
    Character A = new Character('A');
    Character B = new Character('B');
    Character C = new Character('C');

    protected void setUp() throws Exception {
        list = new CircularLinkedList(new Character('A'));
        list.add(new Character('B'));
        list.next(); // Must setup list of A, B, C. Without this call, it would be A, C, B.
        list.add(new Character('C'));
        list.next(); // set the counter to A again
    }

    /**
     * Test iteration through a list
     */
    public void testNext(){
        assertEquals(A,list.next());
        assertEquals(B,list.next());
        assertEquals(C,list.next());
        assertEquals(A,list.next());
    }

    /**
     * <p>Test behavior of the pass count and iteration: what is next() after passcount increments (same or next)?</li>
     */
    public void testPassNext(){
        while (!list.next().equals(C)){
            // wind the list to A
        }
        list.mark(); // mark list with the next being A
        while (list.getPassCount() < 1){
            list.next();
        }
        // One spin around the list should make A the next again
        assertEquals(A,list.next());
    }

    /**
     * Tests the ability of the circular list to maintain pass counts
     */
    public void testPassCount(){
        list.mark();
        for (int i = 0; i < 3; i ++){
            assertEquals(0,list.getPassCount());
            list.next();
        }
        assertEquals(1,list.getPassCount());
        list.mark();
        for (int i = 0; i < 10; i ++){
            list.next();
        }
        assertEquals(3,list.getPassCount());
    }
}