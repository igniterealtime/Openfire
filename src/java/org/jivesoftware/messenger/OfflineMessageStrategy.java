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

package org.jivesoftware.messenger;

import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserNotFoundException;

/**
 * <p>Configures and controls the server's offline message storage strategy.</p>
 * <p>There are several ways to handle messages sent to offline users. The offline strategy
 * centralizes these options and their configuration. In addition, internal components can
 * use the strategy without knowing or caring about the actual behavior specified.</p>
 * <p>The valid strategies currently supported include:</p>
 * <ul>
 * <li>Type.bounce - bounce all messages back to the sender. This is one of two ways
 *      to support offline messages without actually storing them. This is the tactic
 *      used by AOL.
 * </li>
 * <li>Type.drop - drop all messages sent to offline users. This is the second of two
 *      ways to support offline messages without actually storing them. It's much less
 *      user friendly, but provides better privacy (bounced messages can be used to probe
 *      the offline/online presence of another user without subscribing to their presence).
 * </li>
 * <li>Type.store - unconditionally store all messages for later delivery. Ideal for small
 *      deployments or where you are sure people won't abuse offline message storage.
 * </li>
 * <li>Type.store_and_bounce - Stores offline messages for later delivery until the quota
 *      is reached. When the quota is exceeded, all subsequent messages are bounced (see BOUNCE).
 * </li>
 * <li>Type.store_and_drop - Stores offline messages for later delivery until the quota
 *      is reached. When the quota is exceeded, all subsequent messages are silently dropped
 *      (see DROP).
 * </li>
 * </ul>
 *
 * @author Iain Shigeoka
 */
public interface OfflineMessageStrategy {

    /**
     * Returns the storage quota for offline messages in bytes per user. The quota
     * limit only has significance if the strategy type is {@link Type#store_and_bounce}
     * or {@link Type#store_and_drop}.
     *
     * @return the quota in bytes per user for offline message storage.
     */
    int getQuota();

    /**
     * Sets the storage quota for offline messages in bytes per user. The quota
     * limit only has significance if the strategy type is {@link Type#store_and_bounce}
     * or {@link Type#store_and_drop}.
     *
     * @param quota the quota in bytes per user for offline message storage.
     */
    void setQuota(int quota);

    /**
     * Returns the storage strategy type.
     *
     * @return the strategy type in use.
     */
    Type getType();

    /**
     * Sets the storage strategy type.
     *
     * @param type the strategy type to use.
     */
    void setType(Type type);

    /**
     * Store the given message for an offline user. The strategy will
     * take the appropriate action based on it's type.
     *
     * @param message the message to handle.
     */
    void storeOffline(Message message) throws UnauthorizedException, UserNotFoundException;

    /**
     * Strategy types.
     */
    public enum Type {

        /**
         * All messages are bounced to the sender.
         */
        bounce,

        /**
         * All messages are silently dropped.
         */
        drop,

        /**
         * All messages are stored.
         */
        store,

        /**
         * Messages are stored up to the storage limit, and then bounced.
         */
        store_and_bounce,

        /**
         * Messages are stored up to the storage limit, and then silently dropped.
         */
        store_and_drop;
    }
}