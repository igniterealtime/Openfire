/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004-2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.util;

import java.util.Map;

/**
 * Interface to listen for property events. Use the
 * {@link org.jivesoftware.util.PropertyEventDispatcher#addListener(PropertyEventListener)}
 * method to register for events.
 *
 * @author Matt Tucker
 */
public interface PropertyEventListener {

    /**
     * A property was set.
     *
     * @param property the property.
     * @param params event parameters.
     */
    public void propertySet(String property, Map params);

    /**
     * A property was deleted.
     *
     * @param property the deleted.
     * @param params event parameters.
     */
    public void propertyDeleted(String property, Map params);

    /**
     * An XML property was set.
     *
     * @param property the property.
     * @param params event parameters.
     */
    public void xmlPropertySet(String property, Map params);

    /**
     * An XML property was deleted.
     *
     * @param property the property.
     * @param params event parameters.
     */
    public void xmlPropertyDeleted(String property, Map params);

}