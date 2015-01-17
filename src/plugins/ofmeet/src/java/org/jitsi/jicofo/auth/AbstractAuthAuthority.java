/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.auth;

import net.java.sip.communicator.util.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Common class for {@link AuthenticationAuthority} implementations.
 *
 * @author Pawel Domas
 */
public abstract class AbstractAuthAuthority
    implements AuthenticationAuthority
{
    /**
     * The logger.
     */
    private final static Logger logger
        = Logger.getLogger(AbstractAuthAuthority.class);

    /**
     * The list of registered {@link AuthenticationListener}s.
     */
    private List<AuthenticationListener> authenticationListeners
            = new CopyOnWriteArrayList<AuthenticationListener>();

    /**
     * Registers to the list of <tt>AuthenticationListener</tt>s.
     * @param l the <tt>AuthenticationListener</tt> to be added to listeners
     *          list.
     */
    @Override
    public void addAuthenticationListener(AuthenticationListener l)
    {
        if (!authenticationListeners.contains(l))
        {
            authenticationListeners.add(l);
        }
    }

    /**
     * Unregisters from the list of <tt>AuthenticationListener</tt>s.
     * @param l the <tt>AuthenticationListener</tt> that will be removed from
     *          authentication listeners list.
     */
    @Override
    public void removeAuthenticationListener(AuthenticationListener l)
    {
        authenticationListeners.remove(l);
    }

    protected void notifyUserAuthenticated(String userJid, String identity)
    {
        logger.info("Jid " + userJid + " authenticated as: " + identity);

        for (AuthenticationListener l : authenticationListeners)
        {
            l.jidAuthenticated(userJid, identity);
        }
    }
}
