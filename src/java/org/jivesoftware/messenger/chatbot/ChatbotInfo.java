/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.chatbot;

import org.jivesoftware.messenger.auth.UnauthorizedException;
import java.util.Date;

/**
 * <p>The ChatbotInfo interface provides information about and services for users of the system.</p>
 * <p/>
 * <p>The name and email field will normally be required fields when creating user accounts for most
 * implementations. However, some users may wish to keep that information private. Therefore, there
 * are two flags to set if the name and email fields should be made visible to other users. If
 * the flags are set to deny access, getName() and getEmail() will throw UnauthorizedExceptions to
 * users that don't have administrator permissions.</p>
 * <p/>
 * <p>Security for UserInfo objects is provide by UserInfoProxy protection proxy objects.</p>
 *
 * @author Iain Shigeoka
 */
public interface ChatbotInfo {
    /**
     * <p>Obtain the long ID of the user that owns this info.</p>
     *
     * @return The ID of the user that owns this info
     */
    long getId();

    /**
     * Returns the user's description.
     *
     * @return the name of the user.
     */
    String getDescription();

    /**
     * Sets the user's description.
     *
     * @param description new description for the user.
     * @throws UnauthorizedException if does not have administrator permissions.
     */
    void setDescription(String description) throws UnauthorizedException;

    /**
     * Returns the date that the user was created.
     *
     * @return the date the user was created.
     */
    Date getCreationDate();

    /**
     * Sets the creation date of the user. In most cases, the creation date will default to when the
     * user was entered into the system. However, the date needs to be set manually when importing
     * data. In other words, skin authors should ignore this method since it only intended for
     * system maintenance.
     *
     * @param creationDate the date the user was created.
     * @throws UnauthorizedException if does not have administrator permissions.
     */
    void setCreationDate(Date creationDate) throws UnauthorizedException;

    /**
     * Returns the date that the user was last modified.
     *
     * @return the date the user record was last modified.
     */
    Date getModificationDate();

    /**
     * Sets the date the user was last modified. Skin authors should ignore this method since it
     * only intended for system maintenance.
     *
     * @param modificationDate the date the user was modified.
     * @throws UnauthorizedException if does not have administrator permissions.
     */
    void setModificationDate(Date modificationDate) throws UnauthorizedException;
}
