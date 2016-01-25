/**
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

import java.io.InputStream;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginClassLoader;

/**
 * A utility class to assist with loading classes or resources by name. Many application servers use
 * custom classloaders, which will break uses of:
 * <pre>
 *    Class.forName(className);
 * </pre>
 *
 * This utility attempts to load the class or resource using a number of different mechanisms to
 * work around this problem.
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

    /**
     * Loads the given resource as a stream.
     *
     * @param name the name of the resource that exists in the classpath.
     * @return the resource as an input stream or <tt>null</tt> if the resource was not found.
     */
    public static InputStream getResourceAsStream(String name) {
        return instance.loadResource(name);
    }

    /**
     * Not instantiatable.
     */
    private ClassUtils() {}

    public Class loadClass(String className) throws ClassNotFoundException {
		// added by xingqisheng start===============
		// 当of启动的时候，此时还没有加载vplugin,因此无法加载vauthplugin.
		// 当加载vplugin的时候，VClassLoader已经初始化，之后extedplugin会改写与用户有关的provider，
		// 此时再加载相关的类已经可以了，但是需要在此坐下判断，否则仍然无法加载
		if (className.startsWith("com.vidmt")) {
			Plugin vplugin = XMPPServer.getInstance().getPluginManager().getPlugin("vplugin");
			if (vplugin != null) {
				PluginClassLoader pcl = XMPPServer.getInstance().getPluginManager().getPluginClassloader(vplugin);
				try {
					return pcl.loadClass(className);
				} catch (ClassNotFoundException e) {
					System.err.println("===类没有被加载:" + className + "====");
				}
			}
		}
		//added by xingqisheng end=================
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

    private InputStream loadResource(String name) {
        InputStream in = getClass().getResourceAsStream(name);
        if (in == null) {
            in = Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
            if (in == null) {
                in = getClass().getClassLoader().getResourceAsStream(name);
            }
        }
        return in;
    }
}
