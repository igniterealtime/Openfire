/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.disco;

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
    public abstract Iterator getFeatures();
}
