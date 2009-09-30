/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
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

package org.jivesoftware.openfire.disco;

import org.dom4j.Element;

import java.util.Iterator;

/**
 * <p>
 * A <code>ServerIdentitiesProvider</code> is responsible for providing the identities
 * of protocols supported by the SERVER. An example of a server identity is that
 * for PEP (XEP-0163): <identity category="pubsub" type="pep" />
 * <p/>
 * 
 * <p>
 * When the server starts up, IQDiscoInfoHandler will request to all the services that implement
 * the ServerIdentitiesProvider interface for their identities. Whenever a disco request is received
 * IQDiscoInfoHandler will add to the provided information all the collected identities. Therefore, a
 * service must implement this interface in order to offer/publish its identities as part of the
 * server identities.
 * </p>
 *
 * @author Armando Jagucki
 */
public interface ServerIdentitiesProvider {

    /**
     * Returns an Iterator (of Element) with the supported identities by the server. The identities to
     * include are the identities of protocols supported by the SERVER. The idea is that
     * different modules may provide their identities that will ultimately be included in the list
     * of server identities.
     *
     * @return an Iterator (of Element) with identities of protocols supported by the server.
     */
    public abstract Iterator<Element> getIdentities();
}
