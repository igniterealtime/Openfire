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
 * A <code>UserIdentitiesProvider</code> is responsible for providing the identities
 * of protocols supported by users. An example of a user identity is one
 * for PEP (XEP-0163): <identity category="pubsub" type="pep" />
 * <p/>
 * 
 * <p>
 * When the server starts up, IQDiscoInfoHandler will request to all the services that implement
 * the UserIdentitiesProvider interface for their identities. Whenever a disco request is received
 * IQDiscoInfoHandler will add to the provided information all the collected identities. Therefore, a
 * service must implement this interface in order to offer/publish its identities as part of the
 * user identities.
 * </p>
 *
 * @author Armando Jagucki
 */
public interface UserIdentitiesProvider {

    /**
     * Returns an Iterator (of Element) with the supported identities by users. The identities to
     * include are the identities of protocols supported by all registered users on the server. The
     * idea is that different modules may provide their identities that will ultimately be included
     * in the list user identities.
     *
     * @return an Iterator (of Element) with identities of protocols supported by users.
     */
    public abstract Iterator<Element> getIdentities();
}
