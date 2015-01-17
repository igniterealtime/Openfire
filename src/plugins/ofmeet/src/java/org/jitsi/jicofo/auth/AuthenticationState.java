/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.auth;

/**
 * Class used by {@link ShibbolethAuthAuthority} to track authentication state of
 * conference participants. It contains user JID, name of the conference room
 * for which authentication is valid, authentication identity assigned by
 * external authentication system and creation timestamp use to expire
 * pre-authentications(authentication for which the room has not been created
 * yet).
 *
 * @author Pawel Domas
 */
public class AuthenticationState
{
    private final String userJid;

    private final String roomName;

    private final String authenticatedIdentity;

    private final long authTimestamp = System.currentTimeMillis();

    /**
     * Creates new {@link AuthenticationState}.
     * @param userJid the Jabber ID of authenticated user.
     * @param roomName the name of conference room for which this
     *                 authentication is valid.
     * @param authenticatedIdentity the identity assigned by external system
     *                              that will be bound to <tt>userJid</tt>.
     */
    public AuthenticationState(String userJid,
                               String roomName,
                               String authenticatedIdentity)
    {
        this.userJid = userJid;
        this.roomName = roomName;
        this.authenticatedIdentity = authenticatedIdentity;
    }

    /**
     * Returns the Jabber ID of authenticated user.
     */
    public String getUserJid()
    {
        return userJid;
    }

    /**
     * Returns the identity assigned by external authentication system.
     */
    public String getAuthenticatedIdentity()
    {
        return authenticatedIdentity;
    }

    /**
     * Returns conference room name for which this authentication is valid.
     */
    public String getRoomName()
    {
        return roomName;
    }

    /**
     * Creation timestamp of this instance.
     */
    public long getAuthTimestamp()
    {
        return authTimestamp;
    }

    @Override
    public String toString()
    {
        return "AuthState[jid=" + this.userJid + ", room="
                + this.roomName + ", id=" + authenticatedIdentity + "]@"
                + hashCode();
    }
}
