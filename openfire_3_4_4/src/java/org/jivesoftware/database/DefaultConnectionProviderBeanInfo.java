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

    public Class getBeanClass() {
        return org.jivesoftware.database.DefaultConnectionProvider.class;
    }

    public String[] getPropertyNames() {
        return PROPERTY_NAMES;
    }

    public String getName() {
        return "DefaultConnectionProvider";
    }
}