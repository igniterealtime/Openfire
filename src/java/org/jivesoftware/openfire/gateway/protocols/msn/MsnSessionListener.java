/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.gateway.protocols.msn;

import net.sf.cindy.SessionAdapter;
import net.sf.cindy.Session;
import net.sf.cindy.Message;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.LocaleUtils;

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
        // TODO: Kinda hacky, would like to improve on this later.
        if (message.toString().startsWith("OUT OTH")) {
            // Forced disconnect because account logged in elsewhere
            msnSession.getTransport().sendMessage(
                    msnSession.getJIDWithHighestPriority(),
                    msnSession.getTransport().getJID(),
                    LocaleUtils.getLocalizedString("gateway.msn.otherloggedin", "gateway"),
                    org.xmpp.packet.Message.Type.error
            );
            msnSession.logOut();
        }
        else if (message.toString().startsWith("OUT SDH")) {
            // Forced disconnect from server for maintenance
            msnSession.getTransport().sendMessage(
                    msnSession.getJIDWithHighestPriority(),
                    msnSession.getTransport().getJID(),
                    LocaleUtils.getLocalizedString("gateway.msn.disconnect", "gateway"),
                    org.xmpp.packet.Message.Type.error
            );
            msnSession.logOut();
        }
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
        // This is used to handle regular pings to the MSN server.  No need to mention it.
    }

    public void sessionClosed(Session session) {
        Log.debug("MSN: Session closed for "+msnSession.getRegistration().getUsername());
    }

}
