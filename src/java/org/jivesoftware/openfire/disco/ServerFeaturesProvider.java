/**
 * $RCSfile$
 * $Revision: 1695 $
 * $Date: 2005-07-26 02:09:55 -0300 (Tue, 26 Jul 2005) $
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

package org.jivesoftware.openfire.disco;

import java.util.Iterator;

/**
 * ServerFeaturesProviders are responsible for providing the features offered and supported
 * protocols by the SERVER. Example of server features are: jabber:iq:agents, jabber:iq:time, etc.
 * <p/>
 * <p/>
 * When the server starts up, IQDiscoInfoHandler will request to all the services that implement
 * the ServerFeaturesProvider interface for their features. Whenever a disco request is received
 * IQDiscoInfoHandler will add to the provided information all the collected features. Therefore, a
 * service must implement this interface in order to offer/publish its features as part of the
 * server features.
 *
 * @author Gaston Dombiak
 */
public interface ServerFeaturesProvider {

    /**
     * Returns an Iterator (of String) with the supported features by the server. The features to
     * include are the features offered and supported protocols by the SERVER. The idea is that
     * different modules may provide their features that will ultimately be part of the features
     * offered by the server.
     *
     * @return an Iterator (of String) with the supported features by the server.
     */
    public abstract Iterator<String> getFeatures();
}
