/**
 * Copyright (C) 2004-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.xmpp.component;

/**
 * Factory to get a ComponentManager implementation. The ComponentManager implementation
 * used will determined in the following way:<ul>
 *
 *      <li>An external process can set the ComponentManager using
 *      {@link #setComponentManager(ComponentManager)}.
 *      <li>If the component manager is <tt>null</tt>, the factory will check for
 *      the Java system property "whack.componentManagerClass". The value of the
 *      property should be the fully qualified class name of a ComponentManager
 *      implementation (e.g. com.foo.MyComponentManager). The class must have a default
 *      constructor.
 * </ul>
 *
 * @author Matt Tucker
 */
public class ComponentManagerFactory {

    private static ComponentManager componentManager;

    /**
     * Returns a ComponentManager instance.
     *
     * @return a ComponentManager instance.
     */
    public static synchronized ComponentManager getComponentManager() {
        if (componentManager != null) {
            return componentManager;
        }
        // ComponentManager is null so we have to try to figure out how to load
        // an instance. Look for a Java property.
        String className = System.getProperty("whack.componentManagerClass");
        if (className != null) {
            try {
                Class c = Class.forName(className);
                componentManager = (ComponentManager)c.newInstance();
                return componentManager;
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        // Got here, so throw exception.
        throw new NullPointerException("No ComponentManager implementation available.");
    }

    /**
     * Sets the ComponentManager instance that will be used.
     *
     * @param manager the ComponentManager instance.
     */
    public static void setComponentManager(ComponentManager manager) {
        componentManager = manager;
    }
}
