/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.user;

import org.xmpp.packet.JID;

/**
 * Interface to be implemented by components that are capable of returning the name of entities
 * when running as internal components.
 *
 * @author Gaston Dombiak
 */
public interface UserNameProvider {

    /**
     * Returns the name of the entity specified by the following JID.
     *
     * @param entity JID of the entity to return its name.
     * @return the name of the entity specified by the following JID.
     */
    abstract String getUserName(JID entity);
}
