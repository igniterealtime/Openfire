/**
 * $RCSfile$
 * $Revision: 7175 $
 * $Date: 2007-02-16 14:50:15 -0500 (Fri, 16 Feb 2007) $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.muc.spi;

import java.util.Map;

/**
 * Interface to listen for property events. Use the
 * {@link org.jivesoftware.openfire.muc.spi.MUCServicePropertyEventDispatcher#addListener(MUCServicePropertyEventListener)}
 * method to register for events.
 *
 * @author Daniel Henninger
 */
public interface MUCServicePropertyEventListener {

    /**
     * A property was set. The parameter map <tt>params</tt> will contain the
     * the value of the property under the key <tt>value</tt>.
     *
     * @param service the subdomain of the service the property was set on.
     * @param property the name of the property.
     * @param params event parameters.
     */
    public void propertySet(String service, String property, Map<String, Object> params);

    /**
     * A property was deleted.
     *
     * @param service the subdomain of the service the property was deleted from.
     * @param property the name of the property deleted.
     * @param params event parameters.
     */
    public void propertyDeleted(String service, String property, Map<String, Object> params);

}