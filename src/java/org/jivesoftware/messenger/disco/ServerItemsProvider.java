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
 * ServerItemsProvider are responsible for providing the items associated with the SERVER. Example
 * of server items are: Public Chatrooms, PubSub service, etc.<p>
 * <p/>
 * When the server starts up, IQDiscoItemsHandler will request to all the services that implement
 * the ServerItemsProvider interface for their DiscoServerItems. Each DiscoServerItem will provide
 * its DiscoInfoProvider which will automatically be included in IQDiscoInfoHandler as the provider
 * for this item's JID. Moreover, each DiscoServerItem will also provide its DiscoItemsProvider
 * which will automatically be included in IQDiscoItemsHandler. Special attention must be paid to
 * the JID since all the items with the same host will share the same DiscoInfoProvider or
 * DiscoItemsProvider. Therefore, a service must implement this interface in order to get its
 * services published as items associatd with the server.
 *
 * @author Gaston Dombiak
 */
public interface ServerItemsProvider {

    /**
     * Returns an Iterator (of DiscoServerItem) with the items associated with the server or null
     * if none.
     *
     * @return an Iterator (of DiscoServerItem) with the items associated with the server or null
     *         if none.
     */
    public abstract Iterator getItems();
}
