/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.auth;

/**
 * Authentication token is used to identify authentication request. It also
 * binds some resources related to authentication internally instead of
 * exposing them as direct HTTP request parameters. Each token can be used
 * only once to authenticate and they expire after {@link
 * AuthAuthority#TOKEN_LIFETIME} if not used.
 *
 * @author Pawel Domas
 */
public class AuthenticationToken
{
    private final long creationTimestamp;

    private final String token;

    private final String userJid;

    private final String roomName;

    /**
     * Creates new {@link AuthenticationToken} for given parameters.
     * @param token generated random token string which will server as token
     *              value.
     * @param userJid the Jabber ID of the user associated with
     *                authentication request.
     * @param roomName the name of conference room which is a context for
     *                 authentication request identified by new token instance.
     */
    public AuthenticationToken(String token, String userJid, String roomName)
    {
        if (token == null)
        {
            throw new NullPointerException("token");
        }
        if (userJid == null)
        {
            throw new NullPointerException("userJid");
        }
        if (roomName == null)
        {
            throw new NullPointerException("roomName");
        }
        this.token = token;
        this.userJid = userJid;
        this.roomName = roomName;
        creationTimestamp = System.currentTimeMillis();
    }

    /**
     * Returns creation timestamp of this token instance.
     * @return timestamp in milliseconds obtained using {@link
     *         System#currentTimeMillis()}.
     */
    public long getCreationTimestamp()
    {
        return creationTimestamp;
    }

    /**
     * Returns conference room name for which this token can be used for
     * authentication.
     */
    public String getRoomName()
    {
        return roomName;
    }

    /**
     * Returns string token value of this instance.
     */
    public String getToken()
    {
        return token;
    }

    /**
     * Returns Jabber ID of the user associated with this token instance.
     */
    public String getUserJid()
    {
        return userJid;
    }

}
