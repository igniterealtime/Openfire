/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.sametime;

import java.util.ArrayList;

import net.sf.kraken.registration.Registration;
import net.sf.kraken.session.TransportSession;
import net.sf.kraken.type.ChatStateType;
import net.sf.kraken.type.ConnectionFailureReason;
import net.sf.kraken.type.PresenceType;

import org.apache.log4j.Logger;
import org.jivesoftware.util.JiveGlobals;
import org.xmpp.packet.JID;

import com.lotus.sametime.awareness.AwarenessService;
import com.lotus.sametime.awareness.WatchList;
import com.lotus.sametime.buddylist.BLService;
import com.lotus.sametime.community.CommunityService;
import com.lotus.sametime.core.comparch.DuplicateObjectException;
import com.lotus.sametime.core.comparch.STSession;
import com.lotus.sametime.core.constants.ImTypes;
import com.lotus.sametime.im.Im;
import com.lotus.sametime.im.InstantMessagingService;

/**
 * Represents a SameTime session.
 *
 * This is the interface with which the base transport functionality will
 * communicate with SameTime.
 *
 * @author Daniel Henninger
 */
public class SameTimeSession extends TransportSession<SameTimeBuddy> {

    static Logger Log = Logger.getLogger(SameTimeSession.class);

    /**
     * Create a SameTime Session instance.
     *
     * @param registration Registration information used for logging in.
     * @param jid JID associated with this session.
     * @param transport Transport instance associated with this session.
     * @param priority Priority of this session.
     */
    public SameTimeSession(Registration registration, JID jid, SameTimeTransport transport, Integer priority) {
        super(registration, jid, transport, priority);
    }

    /* Session instance */
    private STSession stSession;
    
    /* Community service instance */
    private CommunityService communityService;
    
    /* Instant Messaging service instance */
    private InstantMessagingService imService;
    
    /* Buddy List service instance */
    private BLService blService;
    
    /* Awareness service instance */
    private AwarenessService awarenessService;
    
    /* Awareness watch list instance */
    private WatchList watchList;

    /* Listener */
    private SameTimeListener listener;

    /**
     * @see net.sf.kraken.session.TransportSession#logIn(net.sf.kraken.type.PresenceType, String)
     */
    @Override
    public void logIn(PresenceType presenceType, String verboseStatus) {
        setPendingPresenceAndStatus(presenceType, verboseStatus);
        if (!isLoggedIn()) {  
            try {
                stSession = new STSession("Kraken - "+jid);
                stSession.loadSemanticComponents();
                stSession.start();
                
                listener = new SameTimeListener(this);
                
                communityService = (CommunityService) stSession.getCompApi(CommunityService.COMP_NAME);
                communityService.addLoginListener(listener);
                communityService.disableAutomaticReconnect();
                
                imService = (InstantMessagingService) stSession.getCompApi(InstantMessagingService.COMP_NAME);
                imService.addImServiceListener(listener);
                imService.registerImType(ImTypes.IM_TYPE_CHAT);
                
                blService = (BLService) stSession.getCompApi(BLService.COMP_NAME);
                blService.addBLServiceListener(listener);
                
                awarenessService = (AwarenessService) stSession.getCompApi(AwarenessService.COMP_NAME);
                awarenessService.addAwarenessServiceListener(listener);
                
                watchList = awarenessService.createWatchList();
                watchList.addStatusListener(listener);
                
                communityService.loginByPassword(
                        JiveGlobals.getProperty("plugin.gateway.sametime.connecthost", "stdemo3.dfw.ibm.com"),
                        getRegistration().getUsername(),
                        getRegistration().getPassword()
                );
            }
            catch (DuplicateObjectException e) {
                Log.error("SameTime: Tried to start up duplicate session for: "+jid);
                setFailureStatus(ConnectionFailureReason.UNKNOWN);
                sessionDisconnected("Duplicate session.");
            }
         }
    }

    /**
     * @see net.sf.kraken.session.TransportSession#logOut()
     */
    @Override
    public void logOut() {
        communityService.logout();
        cleanUp();
        sessionDisconnectedNoReconnect(null);
    }

    /**
     * @see net.sf.kraken.session.TransportSession#cleanUp()
     */
    @Override
    public void cleanUp() {
        // Shut down services
        if (watchList != null) {
            try {
                watchList.close();
            }
            catch (Exception e) {
                // Ignore
            }
        }
        watchList = null;
        awarenessService = null;
        blService = null;
        imService = null;
        try {
            stSession.stop();
            stSession.unloadSession();
        }
        catch (Exception e) {
            // Ignore
        }
        if (listener != null) {
            try {
            }
            catch (Exception e) {
                // Ignore
            }
                   
            listener = null;
        }
        stSession = null;
    }

    /**
     * @see net.sf.kraken.session.TransportSession#addContact(org.xmpp.packet.JID, String, java.util.ArrayList)
     */
    @Override
    public void addContact(JID jid, String nickname, ArrayList<String> groups) {
    }

    /**
     * @see net.sf.kraken.session.TransportSession#removeContact(net.sf.kraken.roster.TransportBuddy)
     */
    @Override
    public void removeContact(SameTimeBuddy contact) {
    }

    /**
     * @see net.sf.kraken.session.TransportSession#updateContact(net.sf.kraken.roster.TransportBuddy)
     */
    @Override
    public void updateContact(SameTimeBuddy contact) {
    }
    
    /**
     * @see net.sf.kraken.session.TransportSession#acceptAddContact(JID)
     */
    @Override
    public void acceptAddContact(JID jid) {
        final String userID = getTransport().convertJIDToID(jid);
        Log.debug("SameTime: accept-adding is currently not implemented."
                + " Cannot accept-add: " + userID);
        // TODO: Currently unimplemented
    }

    /**
     * @see net.sf.kraken.session.TransportSession#sendMessage(org.xmpp.packet.JID, String)
     */
    @Override
    public void sendMessage(JID jid, String message) {
        Im im = listener.getIMSession(jid);
        if (im != null) {
            im.sendText(false, message);
        }
    }

    /**
     * @see net.sf.kraken.session.TransportSession#sendChatState(org.xmpp.packet.JID,net.sf.kraken.type.ChatStateType)
     */
    @Override
    public void sendChatState(JID jid, ChatStateType chatState) {
    }

    /**
     * @see net.sf.kraken.session.TransportSession#sendBuzzNotification(org.xmpp.packet.JID, String)
     */
    @Override
    public void sendBuzzNotification(JID jid, String message) {
    }

    /**
     * @see net.sf.kraken.session.TransportSession#updateLegacyAvatar(String, byte[])
     */
    @Override
    public void updateLegacyAvatar(String type, byte[] data) {
    }

    /**
     * @see net.sf.kraken.session.TransportSession#updateStatus(net.sf.kraken.type.PresenceType, String)
     */
    @Override
    public void updateStatus(PresenceType presenceType, String verboseStatus) {
    }

}
