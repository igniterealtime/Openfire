/*
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

package org.jivesoftware.database;

import org.jivesoftware.util.JiveBeanInfo;

/**
 * BeanInfo class for the DefaultConnectionProvider class.
 *
 * @author Jive Software
 */
public class DefaultConnectionProviderBeanInfo extends JiveBeanInfo {

    public static final String[] PROPERTY_NAMES = {
        "driver",
        "serverURL",
        "username",
        "password",
        "minConnections",
        "maxConnections",
        "connectionTimeout"
    };

    public DefaultConnectionProviderBeanInfo() {
        super();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jivesoftware.util.JiveBeanInfo#getBeanClass()
     */
    @Override
    public Class getBeanClass() {
        return org.jivesoftware.database.DefaultConnectionProvider.class;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jivesoftware.util.JiveBeanInfo#getPropertyNames()
     */
    @Override
    public String[] getPropertyNames() {
        return PROPERTY_NAMES;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jivesoftware.util.JiveBeanInfo#getName()
     */
    @Override
    public String getName() {
        return "DefaultConnectionProvider";
    }
}
