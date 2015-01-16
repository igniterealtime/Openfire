/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.auth;

/**
 * Interface used to listen to authentication notification fired by
 * {@link AuthenticationAuthority}.
 *
 * @author Pawel Domas
 */
public interface AuthenticationListener
{
    /**
     * Called by {@link AuthenticationAuthority} when the user identified by
     * given <tt>userJid</tt> gets confirmed identity by external authentication
     * component.
     *
     * @param userJid the real user JID(not MUC JID which can be faked).
     * @param authenticatedIdentity the identity of the user confirmed by
     *                              external authentication component.
     */
    void jidAuthenticated(String userJid, String authenticatedIdentity);
}
