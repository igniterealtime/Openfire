/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright 2004 Jive Software.
 *
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
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

package org.jivesoftware.messenger.event;

import java.util.Map;

/**
 * Interface to listen for property events. Use the
 * {@link PropertyEventDispatcher#addListener(PropertyEventListener)}
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