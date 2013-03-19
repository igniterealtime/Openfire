/**
 * $RCSfile$
 * $Revision: 28502 $
 * $Date: 2006-03-13 13:38:47 -0800 (Mon, 13 Mar 2006) $
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

package org.jivesoftware.xmpp.workgroup;

import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Allows customers to customize the request filters being used by the workgroup.</p>
 *
 * <p>In order to provide custome request filters to be used by Live Assistant, create
 * a RequestFilterFactory that derives from this class and implements the getFilter() method.
 * Add the custom implementation classes to the classpath (most commonly by jarring them and
 * adding them to the Openfire <tt>home/lib</tt> directory). Finally, set the
 * RequestFilterFactory.className property in the system property:</p>
 *
 * <tt>RequestFilterFactory.className = package.name.className</tt>
 *
 * @author Derek DeMoro
 */
abstract public class RequestFilterFactory {

	private static final Logger Log = LoggerFactory.getLogger(RequestFilterFactory.class);
	
    /** <p>The factory to be used.</p> */
    private static RequestFilterFactory factory;

    /**
     * <p>Obtain a request filter factory.</p>
     *
     * @return The request filter factory to be used
     */
    public static RequestFilterFactory getRequestFilterFactory(){
        loadProviders();
        return factory;
    }

    /**
     * Returns a request filter.
     *
     * @return the filter to use
     */
    abstract public RequestFilter getFilter();

    /**
     * The default class to instantiate is an empty implementation.
     */
    private static String [] classNames = {
            "org.jivesoftware.xmpp.workgroup.spi.BasicRequestFilterFactory" };

    /**
     * The property names to use to decide what classes to load
     */
    private static String [] propNames = { "RequestFilterFactory.className" };

    /**
     * <p>Sets up the providers based on the defaults and jive properties.</p>
     *
     * @param providers the provider classes to use.
     * @throws IllegalAccessException if the class could not be loaded.
     * @throws InstantiationException if the class coul not be instantiated.
     */
    private static void setProviders(Class[]providers) throws IllegalAccessException,
            InstantiationException
    {
        factory = (RequestFilterFactory) providers[0].newInstance();
    }

    /**
     * <p>Loads the provider names from the jive properties config file.</p>
     */
    private static void loadProviders(){
        if (factory == null){
            // Use className as a convenient object to get a lock on.
            synchronized(classNames) {
                if (factory == null){
                    try {
                        Class []providers = new Class[classNames.length];
                        for (int i = 0; i < classNames.length; i++){
                            // Convert XML based provider setup to Database based
                            JiveGlobals.migrateProperty(propNames[i]);

                            String className = classNames[i];
                            //See if the classname has been set as a Jive property.
                            String classNameProp = JiveGlobals.getProperty(propNames[i]);
                            if (classNameProp != null) {
                                className = classNameProp;
                            }
                            try {
                                providers[i] = ClassUtils.forName(className);
                            } catch (Exception e){
                                Log.error(
                                        "Exception loading class: " + className, e);
                            }
                        }
                        setProviders(providers);
                    } catch (Exception e) {
                        Log.error(
                                "Exception loading class: " + classNames, e);
                    }
                }
            }
        }
    }
}