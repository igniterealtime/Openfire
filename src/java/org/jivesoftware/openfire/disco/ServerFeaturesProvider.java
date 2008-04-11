/**
 * $RCSfile$
 * $Revision: 1695 $
 * $Date: 2005-07-26 02:09:55 -0300 (Tue, 26 Jul 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
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
