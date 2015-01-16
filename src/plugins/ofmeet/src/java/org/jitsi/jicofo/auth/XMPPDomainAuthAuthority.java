/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.auth;

/**
 * XMPP domain authentication authority that authorizes user who are logged
 * in on specified domain.
 *
 * FIXME move to separate package
 *
 * @author Pawel Domas
 */
public class XMPPDomainAuthAuthority
    extends AbstractAuthAuthority
{
    /**
     * Trusted domain for which users are considered authenticated.
     */
    private final String domain;

    public XMPPDomainAuthAuthority(String domain)
    {
        this.domain = domain;
    }

    private boolean verifyJid(String fullJid)
    {
        String bareJid = fullJid.substring(0, fullJid.indexOf("/"));

        return bareJid.endsWith("@" + domain);
    }

    @Override
    public boolean isAllowedToCreateRoom(String peerJid, String roomName)
    {
        return verifyJid(peerJid);
    }

    @Override
    public boolean isUserAuthenticated(String jabberID, String roomName)
    {
        return verifyJid(jabberID);
    }

    @Override
    public String createAuthenticationUrl(String peerFullJid, String roomName)
    {
        return "null";
    }

    @Override
    public boolean isExternal()
    {
        return false;
    }

    @Override
    public void start()
    {

    }

    @Override
    public void stop()
    {

    }
}
