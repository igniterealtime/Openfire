/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.auth;

/**
 * FIXME work in progress
 * This interface is intended to encapsulate authorization method like
 * Shibboleth, OAuth or XMPP domain.
 *
 * @author Pawel Domas
 */
public interface AuthenticationAuthority
{
    /**
     * Checks if given user is allowed to create the room.
     * @param peerJid the Jabber ID of the user.
     * @param roomName the name of the conference room to be checked.
     * @return <tt>true</tt> if it's OK to create the room for given name on
     *         behalf of verified user or <tt>false</tt> otherwise.
     */
    boolean isAllowedToCreateRoom(String peerJid, String roomName);

    /**
     * Registers to the list of <tt>AuthenticationListener</tt>s.
     * @param l the <tt>AuthenticationListener</tt> to be added to listeners
     *          list.
     */
    void addAuthenticationListener(AuthenticationListener l);

    /**
     * Unregisters from the list of <tt>AuthenticationListener</tt>s.
     * @param l the <tt>AuthenticationListener</tt> that will be removed from
     *          authentication listeners list.
     */
    void removeAuthenticationListener(AuthenticationListener l);

    /**
     * Returns <tt>true</tt> if user is authenticated in given conference room.
     * @param jabberID the Jabber ID of the user to be verified.
     * @param roomName conference room name which is the context of
     *                 authentication.
     */
    boolean isUserAuthenticated(String jabberID, String roomName);

    String createAuthenticationUrl(String peerFullJid, String roomName);

    /**
     * Returns <tt>true</tt> if this is external authentication method that
     * requires user to visit authentication URL.
     */
    boolean isExternal();

    void start();

    void stop();
}
