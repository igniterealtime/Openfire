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

import org.jivesoftware.openfire.XMPPServer;
import org.xmpp.packet.JID;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main responsibility of this class is to return the correct name of XMPP entities. For local
 * entities (i.e. users) the {@link User} name is used. For remote entities the following logic
 * is used:
 * <ol>
 * <li>Check if a {@link UserNameProvider} is registered for the entity's domain. If a provider
 * was found then use it to get the entity's name</li>
 * <li>If no provider was found then retrieve the vCard of the entity and return the name as
 * defined in the vCard. <i>This is not implemented yet.</i></li>
 * <li>If no vCard was found then return the string representation of the entity's JID.</li>
 * </ol>
 *
 * @author Gaston Dombiak
 */
public class UserNameManager {

    private static XMPPServer server = XMPPServer.getInstance();
    /**
     * Map that keeps the UserNameProvider to use for each specific domain.
     */
    private static Map<String, UserNameProvider> providersByDomain =
            new ConcurrentHashMap<String, UserNameProvider>();

    private UserNameManager() {
    }

    /**
     * Adds the specified {@link UserNameProvider} as the provider of users of the specified domain.
     *
     * @param domain   the domain hosted by the UserNameProvider.
     * @param provider the provider that will provide the name of users in the specified domain.
     */
    public static void addUserNameProvider(String domain, UserNameProvider provider) {
        providersByDomain.put(domain, provider);
    }

    /**
     * Removes any {@link UserNameProvider} that was associated with the specified domain.
     *
     * @param domain the domain hosted by a UserNameProvider.
     */
    public static void removeUserNameProvider(String domain) {
        providersByDomain.remove(domain);
    }

    /**
     * Returns the name of the XMPP entity. If the entity is a local user then the User's name
     * will be returned. However, if the user is not a local user then check if there exists a
     * UserNameProvider that provides name for the specified domain. If none was found then
     * the vCard of the entity might be requested and if none was found then a string
     * representation of the entity's JID will be returned.
     *
     * @param entity the JID of the entity to get its name.
     * @return the name of the XMPP entity.
     * @throws UserNotFoundException if the jid belongs to the local server but no user was
     *                               found for that jid.
     */
    public static String getUserName(JID entity) throws UserNotFoundException {
        return getUserName(entity, entity.toString());
    }

    /**
     * Returns the name of the XMPP entity. If the entity is a local user then the User's name
     * will be returned. However, if the user is not a local user then check if there exists a
     * UserNameProvider that provides name for the specified domain. If none was found then
     * the vCard of the entity might be requested and if none was found then a string
     * representation of the entity's JID will be returned.
     *
     * @param entity      the JID of the entity to get its name.
     * @param defaultName default name to return when no name was found.
     * @return the name of the XMPP entity.
     * @throws UserNotFoundException if the jid belongs to the local server but no user was
     *                               found for that jid.
     */
    public static String getUserName(JID entity, String defaultName) throws UserNotFoundException {
        if (server.isLocal(entity)) {
            // Contact is a local entity so search for his user name
            User localUser = UserManager.getInstance().getUser(entity.getNode());
            return !localUser.isNameVisible() || "".equals(localUser.getName()) ? entity.getNode() : localUser.getName();
        } else {
            UserNameProvider provider = providersByDomain.get(entity.getDomain());
            if (provider != null) {
                return provider.getUserName(entity);
            }
            // TODO Request vCard to the remote server/component and return the name as
            // TODO defined in the vCard. We might need to cache this information to avoid
            // TODO high traffic.

            // Return the jid itself as the username
            return defaultName;
        }
    }
}
