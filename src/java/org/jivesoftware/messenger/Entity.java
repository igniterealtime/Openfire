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

/**
 * <p>A generic entity on the system identified by a unique ID and username.</p>
 * <p/>
 * <p>Entities include Chatbots and Users and can reserve messenger 'entity
 * resources' such as groups, permissions, private storage, etc. From an
 * API standpoint, the Entity provides a common base interface for these
 * items when being sent and returned from generic resource services.</p>
 *
 * @author Iain Shigeoka
 */
public interface Entity {
    /**
     * <p>Returns the entity's id.</p>
     * <p/>
     * <p>All ids must be unique in the system across all Chatbots and Users.</p>
     *
     * @return the entity's id.
     */
    long getID();

    /**
     * <p>Returns the entity's username.</p>
     * <p/>
     * <p>All usernames must be unique in the system.</p>
     *
     * @return the username of the entity.
     */
    String getUsername();
}
