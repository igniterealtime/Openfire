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
package org.jivesoftware.messenger;

import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.dom4j.Element;

/**
 * <p>Private storage for user accounts.</p>
 * <p>Used by some XMPP systems for saving client settings on the server.</p>
 *
 * @author Iain Shigeoka
 */
public interface PrivateStore {

    /**
     * <p>Retrieve a flag indicating if the private store is enabled or not.</p>
     * <p>Private storage is subject to resource abuse and must be managed. The first
     * line of defense is simple to enable or disable private storage outright.</p>
     *
     * @return True if this private store is enabled (allowing at least some private storage)
     */
    boolean isEnabled();

    /**
     * <p>Enable or disable the private store.</p>
     * <p>Private storage is subject to resource abuse and must be managed. The first
     * line of defense is simple to enable or disable private storage outright.</p>
     *
     * @param enabled True if this private store is enabled (allowing at least some private storage)
     * @throws UnauthorizedException If there are insufficient permissions to access the data
     */
    void setEnabled(boolean enabled) throws UnauthorizedException;

    /**
     * <p>Store the given data.</p>
     * <p>If the name and namespace of the element matches another
     * stored private data XML document, then replace it with the new one.</p>
     *
     * @param data   The data to store (XML element)
     * @param userID The user ID of account where private data is being stored
     * @throws UnauthorizedException If there are insufficient permissions to access the data
     */
    void add(long userID, Element data) throws UnauthorizedException;

    /**
     * <p>Retrieve the data stored under a key corresponding to the name and namespace of
     * the data element given.</p>
     * <p>The data query will be of the form:</p>
     * <code><pre>
     * &lt;name xmlns='namespace'/&gt;
     * </pre></code>
     * <p>If no data is currently stored under the given key, return the query.</p>
     *
     * @param data   An XML document who's element name and namespace is used to match previously stored private data
     * @param userID The user ID of account where private data is being stored
     * @return The data stored under the given key or the data element
     * @throws UnauthorizedException If there are insufficient permissions to access the data
     */
    Element get(long userID, Element data) throws UnauthorizedException;
}
