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
 * A utility class to assist with loading classes by name. Many application servers use
 * custom classloaders, which will break uses of:
 * <pre>
 *    Class.forName(className);
 * </pre>
 * <p/>
 * This utility attempts to load the class using a number of different mechanisms to work
 * around this problem.
 *
 * @author Matt Tucker
 */
public class ClassUtils {

    private static ClassUtils instance = new ClassUtils();

    /**
     * Loads the class with the specified name.
     *
     * @param className the name of the class
     * @return the resulting <code>Class</code> object
     * @throws ClassNotFoundException if the class was not found
     */
    public static Class forName(String className) throws ClassNotFoundException {
        return instance.loadClass(className);
    }

    private ClassUtils() {

    }

    public Class loadClass(String className) throws ClassNotFoundException {
        Class theClass = null;
        try {
            theClass = Class.forName(className);
        }
        catch (ClassNotFoundException e1) {
            try {
                theClass = Thread.currentThread().getContextClassLoader().loadClass(className);
            }
            catch (ClassNotFoundException e2) {
                theClass = getClass().getClassLoader().loadClass(className);
            }
        }
        return theClass;
    }
}
