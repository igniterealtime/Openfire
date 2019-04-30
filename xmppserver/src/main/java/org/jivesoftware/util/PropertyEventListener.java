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
     * A property was set. The parameter map {@code params} will contain the
     * the value of the property under the key {@code value}.
     *
     * @param property the name of the property.
     * @param params event parameters.
     */
    void propertySet( String property, Map<String, Object> params );

    /**
     * A property was deleted.
     *
     * @param property the name of the property deleted.
     * @param params event parameters.
     */
    void propertyDeleted( String property, Map<String, Object> params );

    /**
     * An XML property was set. The parameter map {@code params} will contain the
     * the value of the property under the key {@code value}.
     *
     * @param property the name of the property.
     * @param params event parameters.
     */
    void xmlPropertySet( String property, Map<String, Object> params );

    /**
     * An XML property was deleted.
     *
     * @param property the name of the property.
     * @param params event parameters.
     */
    void xmlPropertyDeleted( String property, Map<String, Object> params );

}
