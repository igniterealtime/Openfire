/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.protocols.msn;

import net.sf.cindy.SessionAdapter;
import net.sf.cindy.Session;
import net.sf.cindy.Message;
import org.jivesoftware.util.Log;

/**
 * MSN Session Listener Interface.
 *
 * This handles listening to session activities.
 *
 * @author lionheart@clansk.org
 * @author Daniel Henninger
 */
public class MsnSessionListener extends SessionAdapter {

    public MsnSessionListener(MSNSession msnSession) {
        this.msnSession = msnSession;
    }

    /**
     * The session this listener is associated with.
     */
    public MSNSession msnSession = null;

    public void exceptionCaught(Session arg0, Throwable t) throws Exception{
        Log.debug("MSN: Session exceptionCaught for "+msnSession.getRegistration().getUsername()+" : "+t);
    }

    public void messageReceived(Session arg0, Message message) throws Exception {
        Log.debug("MSN: Session messageReceived for "+msnSession.getRegistration().getUsername()+" : "+message);
    }

    public void messageSent(Session arg0, Message message) throws Exception {
        Log.debug("MSN: Session messageSent for "+msnSession.getRegistration().getUsername()+" : "+message);
    }

    public void sessionIdle(Session session) throws Exception {
    }

    public void sessionEstablished(Session session) {
        Log.debug("MSN: Session established for "+msnSession.getRegistration().getUsername());
    }

    public void sessionTimeout(Session session) {
        Log.debug("MSN: Session timeout for "+msnSession.getRegistration().getUsername());
    }

    public void sessionClosed(Session session) {
        Log.debug("MSN: Session closed for "+msnSession.getRegistration().getUsername());
    }

}
