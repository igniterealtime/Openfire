/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.yahoo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.sf.kraken.pseudoroster.PseudoRoster;
import net.sf.kraken.pseudoroster.PseudoRosterItem;
import net.sf.kraken.pseudoroster.PseudoRosterManager;
import net.sf.kraken.registration.Registration;
import net.sf.kraken.session.TransportSession;
import net.sf.kraken.type.*;

import org.apache.log4j.Logger;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.NotFoundException;
import org.openymsg.network.AccountLockedException;
import org.openymsg.network.AuthenticationState;
import org.openymsg.network.DirectConnectionHandler;
import org.openymsg.network.FailedLoginException;
import org.openymsg.network.LoginRefusedException;
import org.openymsg.network.Session;
import org.openymsg.network.Status;
import org.openymsg.network.YahooProtocol;
import org.openymsg.network.YahooUser;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

/**
 * Represents a Yahoo session.
 * 
 * This is the interface with which the base transport functionality will
 * communicate with Yahoo.
 *
 * @author Daniel Henninger
 * Heavily inspired by Noah Campbell's work.
 */
public class YahooSession extends TransportSession<YahooBuddy> {

	/**
	 * Yahoo requires every contact to be in at least one group. If no groups
	 * are supplied by XMPP, we'll add the user to a group with this name.
	 */
	public static final String DEFAULT_GROUPNAME = "Friends";

	static Logger Log = Logger.getLogger(YahooSession.class);

    /**
     * Create a Yahoo Session instance.
     *
     * @param registration Registration informationed used for logging in.
     * @param jid JID associated with this session.
     * @param transport Transport instance associated with this session
     * @param priority Priority of this session
     */
    public YahooSession(Registration registration, JID jid, YahooTransport transport, Integer priority) {
        super(registration, jid, transport, priority);
        setSupportedFeature(SupportedFeature.attention);
        setSupportedFeature(SupportedFeature.chatstates);

        pseudoRoster = PseudoRosterManager.getInstance().getPseudoRoster(registration);
    }

    /**
     * Our pseudo roster.
     *
     * We only really use it for nickname tracking.
     */
    private PseudoRoster pseudoRoster;

    public PseudoRoster getPseudoRoster() {
        return pseudoRoster;
    }

    /**
     * Run thread.
     */
    private Thread runThread;

    /**
     * Yahoo session
     */
    private Session yahooSession;

    /**
     * Yahoo session listener.
     */
    private YahooListener yahooListener;

    /**
     * @see net.sf.kraken.session.TransportSession#logIn(net.sf.kraken.type.PresenceType, String)
     */
    @Override
    public void logIn(PresenceType presenceType, String verboseStatus) {
        setPendingPresenceAndStatus(presenceType, verboseStatus);
        if (!isLoggedIn()) {
            yahooSession = new Session(new DirectConnectionHandler(
                    JiveGlobals.getProperty("plugin.gateway.yahoo.connecthost", "scs.msg.yahoo.com"),
                    JiveGlobals.getIntProperty("plugin.gateway.yahoo.connectport", 5050)
            ));
            yahooListener = new YahooListener(this);
            yahooSession.addSessionListener(yahooListener);

            runThread = new Thread() {
                @Override
                public void run() {
                    try {
                        yahooSession.setStatus(Status.AVAILABLE);
                        yahooSession.login(registration.getUsername(), registration.getPassword());
                        setLoginStatus(TransportLoginStatus.LOGGED_IN);

//                        yahooSession.setStatus(((YahooTransport)getTransport()).convertXMPPStatusToYahoo(getPresence()));
//
//                        syncUsers();
                    }
                    catch (FailedLoginException e) {
                        yahooSession.reset();
                        String reason = LocaleUtils.getLocalizedString("gateway.yahoo.loginrefused", "kraken");
                        Log.debug("Yahoo login failure for "+getJID()+": "+reason);

                        getTransport().sendMessage(
                                getJID(),
                                getTransport().getJID(),
                                reason,
                                Message.Type.error
                        );
                        setLoginStatus(TransportLoginStatus.LOGGED_OUT);
                        setFailureStatus(ConnectionFailureReason.CAN_NOT_CONNECT);
                        sessionDisconnected(reason);
                    }
                    catch (LoginRefusedException e) {
                        yahooSession.reset();
                        String reason = LocaleUtils.getLocalizedString("gateway.yahoo.loginrefused", "kraken");
                        AuthenticationState state = e.getStatus();
                        if (state == AuthenticationState.BADUSERNAME) {
                            reason = LocaleUtils.getLocalizedString("gateway.yahoo.unknownuser", "kraken");
                            setFailureStatus(ConnectionFailureReason.USERNAME_OR_PASSWORD_INCORRECT);
                            sessionDisconnectedNoReconnect(reason);
                        }
                        else if (state == AuthenticationState.BAD) {
                            reason = LocaleUtils.getLocalizedString("gateway.yahoo.badpassword", "kraken");
                            setFailureStatus(ConnectionFailureReason.USERNAME_OR_PASSWORD_INCORRECT);
                            sessionDisconnectedNoReconnect(reason);
                        }
                        else if (state == AuthenticationState.LOCKED) {
                            AccountLockedException e2 = (AccountLockedException)e;
                            if(e2.getWebPage() != null) {
                                reason = LocaleUtils.getLocalizedString("gateway.yahoo.accountlockedwithurl", "kraken", Arrays.asList(e2.getWebPage().toString()));
                            }
                            else {
                                reason = LocaleUtils.getLocalizedString("gateway.yahoo.accountlocked", "kraken");
                            }
                            setFailureStatus(ConnectionFailureReason.LOCKED_OUT);
                            sessionDisconnectedNoReconnect(reason);
                        }

                        Log.debug("Yahoo login refused for "+getJID()+": "+reason);

                        getTransport().sendMessage(
                                getJID(),
                                getTransport().getJID(),
                                reason,
                                Message.Type.error
                        );
                        setLoginStatus(TransportLoginStatus.LOGGED_OUT);
                    }
                    catch (IOException e) {
                        Log.debug("Yahoo login caused IO exception:", e);

                        getTransport().sendMessage(
                                getJID(),
                                getTransport().getJID(),
                                LocaleUtils.getLocalizedString("gateway.yahoo.unknownerror", "kraken"),
                                Message.Type.error
                        );
                        setLoginStatus(TransportLoginStatus.LOGGED_OUT);
                        setFailureStatus(ConnectionFailureReason.CAN_NOT_CONNECT);
                        sessionDisconnected(LocaleUtils.getLocalizedString("gateway.yahoo.unknownerror", "kraken"));
                    }
                }
            };
            runThread.start();
        }
    }

    /**
     * @see net.sf.kraken.session.TransportSession#logOut()
     */
    @Override
    public void logOut() {
        cleanUp();
        sessionDisconnectedNoReconnect(null);
    }

    /**
     * @see net.sf.kraken.session.TransportSession#cleanUp()
     */
    @Override
    public void cleanUp() {
        if (yahooSession != null) {
            if (yahooListener != null) {
                yahooSession.removeSessionListener(yahooListener);
                yahooListener = null;
            }
            try {
                yahooSession.logout();
            }
            catch (IOException e) {
                Log.debug("Failed to log out from Yahoo.");
            }
            catch (IllegalStateException e) {
                // Not logged in, well then no problem.
            }
            try {
                yahooSession.reset();
            }
            catch (Exception e) {
                // If this fails it's ok, move on
            }
            yahooSession = null;
        }
        if (runThread != null) {
            try {
                runThread.interrupt();
            }
            catch (Exception e) {
                // Ignore
            }
            runThread = null;
        }
    }

    /**
     * Syncs up the yahoo roster with the jabber roster.
     */
    public void syncUsers() {
        // Run through the entire list of users and set up our sync group.
        for (Object userObj : yahooSession.getRoster().toArray()) {
            YahooUser user = (YahooUser)userObj;
            PseudoRosterItem rosterItem = pseudoRoster.getItem(user.getId());
            String nickname = null;
            if (rosterItem != null) {
                nickname = rosterItem.getNickname();
            }
            if (nickname == null) {
                nickname = user.getId();
            }
            getBuddyManager().storeBuddy(new YahooBuddy(this.getBuddyManager(), user, nickname, user.getGroupIds(), rosterItem));
        }
        // Lets try the actual sync.
        try {
            getTransport().syncLegacyRoster(getJID(), getBuddyManager().getBuddies());
        }
        catch (UserNotFoundException e) {
            Log.debug("Unable to sync yahoo contact list for " + getJID());
        }

        getBuddyManager().activate();
    }

    /**
     * @see net.sf.kraken.session.TransportSession#addContact(org.xmpp.packet.JID, String, java.util.ArrayList)
     */
    @Override
    public void addContact(JID jid, String nickname, ArrayList<String> groups) {
    	// OpenYMSG requires a user to be in at least one group.
    	if (groups == null ) {
    		groups = new ArrayList<String>();
    	}
    	if (groups.isEmpty()) {
    		// add the default Yahoo group
    		groups.add(DEFAULT_GROUPNAME);
    	}
    	
        // Syncing will take care of add.
        String contact = getTransport().convertJIDToID(jid);
        PseudoRosterItem rosterItem;
        if (pseudoRoster.hasItem(contact)) {
            rosterItem = pseudoRoster.getItem(contact);
            rosterItem.setNickname(nickname);
        }
        else {
            rosterItem = pseudoRoster.createItem(contact, nickname, groups);
        }
        YahooUser yUser = new YahooUser(contact);
        for (String grp : groups) {
            yUser.addGroupId(grp);
        }
        yahooSession.getRoster().add(yUser); 
        YahooBuddy yBuddy = new YahooBuddy(getBuddyManager(), yUser, nickname, groups, rosterItem);
        getBuddyManager().storeBuddy(yBuddy);
    }

    /**
     * @see net.sf.kraken.session.TransportSession#removeContact(net.sf.kraken.roster.TransportBuddy)
     */
    @Override
    public void removeContact(YahooBuddy contact) {
        String yahooContact = getTransport().convertJIDToID(contact.getJID());
        yahooSession.getRoster().remove((contact).yahooUser);
        pseudoRoster.removeItem(yahooContact);
    }

    /**
     * @see net.sf.kraken.session.TransportSession#updateContact(net.sf.kraken.roster.TransportBuddy)
     */
    @Override
    public void updateContact(YahooBuddy contact) {
    	// Yahoo requires each user to be in at least one group.
    	if (contact.getGroups() == null || contact.getGroups().isEmpty()) {
    		List<String> defaultGroup = new ArrayList<String>();
    		defaultGroup.add(DEFAULT_GROUPNAME);
    		contact.setGroups(defaultGroup);
    	}
    	
        String yahooContact = getTransport().convertJIDToID(contact.getJID());
        PseudoRosterItem rosterItem;
        if (pseudoRoster.hasItem(yahooContact)) {
            rosterItem = pseudoRoster.getItem(yahooContact);
            rosterItem.setNickname(contact.getNickname());
        }
        else {
            rosterItem = pseudoRoster.createItem(yahooContact, contact.getNickname(), null);
        }
        try {
            YahooBuddy yBuddy = getBuddyManager().getBuddy(contact.getJID());
            yBuddy.pseudoRosterItem = rosterItem;
            for (String newGroup : yBuddy.getGroups()) {
                if (!yBuddy.yahooUser.getGroupIds().contains(newGroup)) {
                    // Add new group to user
                    yBuddy.yahooUser.addGroupId(newGroup);
                }
            }
            for (String oldGroup : yBuddy.yahooUser.getGroupIds()) {
                if (!yBuddy.getGroups().contains(oldGroup)) {
                    // Remove group from user
                    // TODO: This needs to be implemented...
                    //yBuddy.yahooUser.removeGroupId(oldGroup);
                }
            }
        }
        catch (NotFoundException e) {
            Log.debug("Yahoo: Updated buddy not found in buddy manager: "+yahooContact);
        }
    }
    
    /**
     * @see net.sf.kraken.session.TransportSession#acceptAddContact(JID)
     */
    @Override
    public void acceptAddContact(JID jid) {
        final String userID = getTransport().convertJIDToID(jid);
        Log.debug("Yahoo: accept add contact " + userID);
        
        try {
            yahooSession.acceptFriendAuthorization(userID, YahooProtocol.YAHOO);
        } catch (IOException e) {
            Log.debug("Yahoo: Failed to accept add contact request.");
        }
    }
    
    
    /**
     * @see net.sf.kraken.session.TransportSession#sendMessage(org.xmpp.packet.JID, String)
     */
    @Override
    public void sendMessage(JID jid, String message) {
        try {
            yahooSession.sendMessage(getTransport().convertJIDToID(jid), message);
        }
        catch (IOException e) {
            Log.debug("Failed to send message to yahoo user.");
        }
    }

    /**
     * @see net.sf.kraken.session.TransportSession#sendChatState(org.xmpp.packet.JID,net.sf.kraken.type.ChatStateType)
     */
    @Override
    public void sendChatState(JID jid, ChatStateType chatState) { 
        try {
            if (chatState.equals(ChatStateType.composing)) {
                yahooSession.sendTypingNotification(getTransport().convertJIDToID(jid), true);
            }
            else {
                yahooSession.sendTypingNotification(getTransport().convertJIDToID(jid), false); 
            }
        }
        catch (IOException e) {
            Log.debug("Failed to send typing notification to yahoo user.");
        }
    }

    /**
     * @see net.sf.kraken.session.TransportSession#sendBuzzNotification(org.xmpp.packet.JID, String)
     */
    @Override
    public void sendBuzzNotification(JID jid, String message) {
        try {
            yahooSession.sendBuzz(getTransport().convertJIDToID(jid));
        }
        catch (IOException e) {
            Log.debug("Failed to send buzz notification to yahoo user.");
        }
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
        try {
            if (isLoggedIn()) {
                yahooSession.setStatus(((YahooTransport)getTransport()).convertXMPPStatusToYahoo(presenceType));
                setPresenceAndStatus(presenceType, verboseStatus);
            }
            else {
                // TODO: Should we consider auto-logging back in?
            }
        }
        catch (Exception e) {
            Log.debug("Unable to set Yahoo Status:", e);
        }
    }
    
    /**
     * Retrieve the actual Yahoo Session interface.
     */
    public Session getYahooSession() {
        return yahooSession;
    }

}
