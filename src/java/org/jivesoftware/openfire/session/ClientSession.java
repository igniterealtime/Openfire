/*
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

package org.jivesoftware.openfire.session;

import org.jivesoftware.openfire.privacy.PrivacyList;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.packet.Presence;

/**
 * Represents a session between the server and a client.
 *
 * @author Gaston Dombiak
 */
public interface ClientSession extends Session {

    /**
     * Returns the Privacy list that overrides the default privacy list. This list affects
     * only this session and only for the duration of the session.
     *
     * @return the Privacy list that overrides the default privacy list.
     */
    PrivacyList getActiveList();

    /**
     * Sets the Privacy list that overrides the default privacy list. This list affects
     * only this session and only for the duration of the session.
     *
     * @param activeList the Privacy list that overrides the default privacy list.
     */
    void setActiveList( PrivacyList activeList );

    /**
     * Returns the default Privacy list used for the session's user. This list is
     * processed if there is no active list set for the session.
     *
     * @return the default Privacy list used for the session's user.
     */
    PrivacyList getDefaultList();

    /**
     * Sets the default Privacy list used for the session's user. This list is
     * processed if there is no active list set for the session.
     *
     * @param defaultList the default Privacy list used for the session's user.
     */
    void setDefaultList( PrivacyList defaultList );

    /**
     * Returns the username associated with this session. Use this information
     * with the user manager to obtain the user based on username.
     *
     * @return the username associated with this session
     * @throws UserNotFoundException if a user is not associated with a session
     *      (the session has not authenticated yet)
     */
    String getUsername() throws UserNotFoundException;

    /**
     * Returns true if the authetnicated user is an anonymous user or if
     * the use has not authenticated yet.
     *
     * @return true if the authetnicated user is an anonymous user or if
     * the use has not authenticated yet.
     */
    boolean isAnonymousUser();

    /**
     * Flag indicating if this session has been initialized once coming
     * online. Session initialization occurs after the session receives
     * the first "available" presence update from the client. Initialization
     * actions include pushing offline messages, presence subscription requests,
     * and presence statuses to the client. Initialization occurs only once
     * following the first available presence transition.
     *
     * @return True if the session has already been initializsed
     */
    boolean isInitialized();

    /**
     * Sets the initialization state of the session.
     *
     * @param isInit True if the session has been initialized
     * @see #isInitialized
     */
    void setInitialized( boolean isInit );

    /**
     * Returns true if the offline messages of the user should be sent to the user when
     * the user becomes online. If the user sent a disco request with node
     * "http://jabber.org/protocol/offline" before the available presence then do not
     * flood the user with the offline messages. If the user is connected from many resources
     * then if one of the sessions stopped the flooding then no session should flood the user.
     *
     * @return true if the offline messages of the user should be sent to the user when the user
     *         becomes online.
     */
    boolean canFloodOfflineMessages();

    /**
     * Returns true if the user requested to not receive offline messages when sending
     * an available presence. The user may send a disco request with node
     * "http://jabber.org/protocol/offline" so that no offline messages are sent to the
     * user when he becomes online. If the user is connected from many resources then
     * if one of the sessions stopped the flooding then no session should flood the user.
     *
     * @return true if the user requested to not receive offline messages when sending
     *         an available presence.
     */
    boolean isOfflineFloodStopped();

    /**
     * Obtain the presence of this session.
     *
     * @return The presence of this session or null if not authenticated
     */
    Presence getPresence();

    /**
     * Set the presence of this session
     *
     * @param presence The presence for the session
     */
    void setPresence( Presence presence );

    /**
     * Increments the conflict by one and returns new number of conflicts detected on this session.
     *
     * @return the new number of conflicts detected on this session.
     */
    int incrementConflictCount();

    /**
     * Indicates, whether message carbons are enabled.
     *
     * @return True, if message carbons are enabled.
     */
    boolean isMessageCarbonsEnabled();

    /**
     * Enables or disables <a href="http://xmpp.org/extensions/xep-0280.html">XEP-0280: Message Carbons</a> for this session.
     *
     * @param enabled True, if message carbons are enabled.
     * @see <a href="hhttp://xmpp.org/extensions/xep-0280.html">XEP-0280: Message Carbons</a>
     */
    void setMessageCarbonsEnabled(boolean enabled);

    /**
     * Indicates whether this session has requested a blocklist, as specified in XEP-0191.
     *
     * @return true when a blocklist was requested, otherwise false;
     */
    boolean hasRequestedBlocklist();

    /**
     * Defines if this session has requested a blocklist, as specified in XEP-0191.
     *
     * @param hasRequestedBlocklist True when a blocklist has been requested by this session, otherwise false.
     */
    void setHasRequestedBlocklist( boolean hasRequestedBlocklist );
}
