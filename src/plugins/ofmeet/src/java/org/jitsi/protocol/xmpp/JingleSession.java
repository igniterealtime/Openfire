/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.protocol.xmpp;

/**
 * Class describes Jingle session.
 *
 * @author Pawel Domas
 */
public class JingleSession
{
    /**
     * Jingle session identifier.
     */
    private final String sid;

    /**
     * Remote peer XMPP address.
     */
    private final String address;

    /**
     * Creates new instance of <tt>JingleSession</tt> for given parameters.
     *
     * @param sid Jingle session identifier of new instance.
     * @param address remote peer XMPP address.
     */
    public JingleSession(String sid, String address)
    {
        this.sid = sid;
        this.address = address;
    }

    /**
     * Returns Jingle session identifier.
     */
    public String getSessionID()
    {
        return sid;
    }

    /**
     * Returns remote peer's full XMPP address.
     */
    public String getAddress()
    {
        return address;
    }
}
