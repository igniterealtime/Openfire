/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.xmpp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;

import net.sf.kraken.avatars.Avatar;
import net.sf.kraken.protocols.xmpp.mechanisms.FacebookConnectSASLMechanism;
import net.sf.kraken.protocols.xmpp.mechanisms.MySASLDigestMD5Mechanism;
import net.sf.kraken.protocols.xmpp.packet.BuzzExtension;
import net.sf.kraken.protocols.xmpp.packet.GoogleMailBoxPacket;
import net.sf.kraken.protocols.xmpp.packet.GoogleMailNotifyExtension;
import net.sf.kraken.protocols.xmpp.packet.GoogleNewMailExtension;
import net.sf.kraken.protocols.xmpp.packet.GoogleUserSettingExtension;
import net.sf.kraken.protocols.xmpp.packet.IQWithPacketExtension;
import net.sf.kraken.protocols.xmpp.packet.ProbePacket;
import net.sf.kraken.protocols.xmpp.packet.VCardUpdateExtension;
import net.sf.kraken.registration.Registration;
import net.sf.kraken.session.TransportSession;
import net.sf.kraken.type.*;

import org.apache.log4j.Logger;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.Roster.SubscriptionMode;
import org.jivesoftware.smack.filter.OrFilter;
import org.jivesoftware.smack.filter.PacketExtensionFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Presence.Type;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.ChatState;
import org.jivesoftware.smackx.packet.ChatStateExtension;
import org.jivesoftware.smackx.packet.VCard;
import org.jivesoftware.util.Base64;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.NotFoundException;
import org.xmpp.packet.JID;

/**
 * Handles XMPP transport session.
 *
 * @author Daniel Henninger
 * @author Mehmet Ecevit
 */
public class XMPPSession extends TransportSession<XMPPBuddy> {

    static Logger Log = Logger.getLogger(XMPPSession.class);
    
    /**
     * Create an XMPP Session instance.
     *
     * @param registration Registration information used for logging in.
     * @param jid JID associated with this session.
     * @param transport Transport instance associated with this session.
     * @param priority Priority of this session.
     */
    public XMPPSession(Registration registration, JID jid, XMPPTransport transport, Integer priority) {
        super(registration, jid, transport, priority);
        setSupportedFeature(SupportedFeature.attention);
        setSupportedFeature(SupportedFeature.chatstates);

        Log.debug("Creating "+getTransport().getType()+" session for " + registration.getUsername());
        String connecthost;
        Integer connectport;
        String domain;

        connecthost = JiveGlobals.getProperty("plugin.gateway."+getTransport().getType()+".connecthost", (getTransport().getType().equals(TransportType.gtalk) ? "talk.google.com" : getTransport().getType().equals(TransportType.facebook) ? "chat.facebook.com" : "jabber.org"));
        connectport = JiveGlobals.getIntProperty("plugin.gateway."+getTransport().getType()+".connectport", 5222);

        if (getTransport().getType().equals(TransportType.gtalk)) {
            domain = "gmail.com";
        }
        else if (getTransport().getType().equals(TransportType.facebook)) {
            //if (connecthost.equals("www.facebook.com")) {
                connecthost = "chat.facebook.com";
            //}
            //if (connectport.equals(80)) {
                connectport = 5222;
            //}
            domain = "chat.facebook.com";
        }
        else if (getTransport().getType().equals(TransportType.renren)) {
            connecthost = "talk.renren.com";
            connectport = 5222;
            domain = "renren.com";
        }
        else {
            domain = connecthost;
        }

        // For different domains other than 'gmail.com', which is given with Google Application services
        if (registration.getUsername().indexOf("@") > -1) {
            domain = registration.getUsername().substring( registration.getUsername().indexOf("@")+1 );
        }

        // If administrator specified "*" for domain, allow user to connect to anything.
        if (connecthost.equals("*")) {
            connecthost = domain;
        }

        config = new ConnectionConfiguration(connecthost, connectport, domain);
        config.setCompressionEnabled(JiveGlobals.getBooleanProperty("plugin.gateway."+getTransport().getType()+".usecompression", false));

        if (getTransport().getType().equals(TransportType.facebook)) {
            //SASLAuthentication.supportSASLMechanism("PLAIN", 0);
            //config.setSASLAuthenticationEnabled(false);
            //config.setSecurityMode(ConnectionConfiguration.SecurityMode.enabled);
        }

        // instead, send the initial presence right after logging in. This
        // allows us to use a different presence mode than the plain old
        // 'available' as initial presence.
        config.setSendPresence(false); 

        if (getTransport().getType().equals(TransportType.gtalk) && JiveGlobals.getBooleanProperty("plugin.gateway.gtalk.mailnotifications", true)) {
            ProviderManager.getInstance().addIQProvider(GoogleMailBoxPacket.MAILBOX_ELEMENT, GoogleMailBoxPacket.MAILBOX_NAMESPACE, new GoogleMailBoxPacket.Provider());
            ProviderManager.getInstance().addExtensionProvider(GoogleNewMailExtension.ELEMENT_NAME, GoogleNewMailExtension.NAMESPACE, new GoogleNewMailExtension.Provider());
        }
    }
    
    /*
     * XMPP connection
     */
    public XMPPConnection conn = null;
    
    /**
     * XMPP listener
     */
    private XMPPListener listener = null;

    /**
     * Run thread.
     */
    private Thread runThread = null;

	/**
	 * Instance that will handle all presence stanzas sent from the legacy
	 * domain
	 */
	private XMPPPresenceHandler presenceHandler = null;
    
    /*
     * XMPP connection configuration
     */
    private final ConnectionConfiguration config;

    /**
     * Timer to check for online status.
     */
    public Timer timer = new Timer();

    /**
     * Interval at which status is checked.
     */
    private int timerInterval = 60000; // 1 minute

    /**
     * Mail checker
     */
    MailCheck mailCheck;

    /**
     * XMPP Resource - the resource we are using (randomly generated)
     */
    public String xmppResource = StringUtils.randomString(10);

    /**
     * Returns a full JID based off of a username passed in.
     *
     * If it already looks like a JID, returns what was passed in.
     *
     * @param username Username to turn into a JID.
     * @return Converted username.
     */
    public String generateFullJID(String username) {
        if (username.indexOf("@") > -1) {
            return username;
        }

        if (getTransport().getType().equals(TransportType.gtalk)) {
            return username+"@"+"gmail.com";
        }
        else if (getTransport().getType().equals(TransportType.facebook)) {
            return username+"@"+"chat.facebook.com";
        }
        else if (getTransport().getType().equals(TransportType.renren)) {
            return username+"@"+"renren.com";
        }
        else if (getTransport().getType().equals(TransportType.livejournal)) {
            return username+"@"+"livejournal.com";
        }
        else {
            String connecthost = JiveGlobals.getProperty("plugin.gateway."+getTransport().getType()+".connecthost", (getTransport().getType().equals(TransportType.gtalk) ? "talk.google.com" : getTransport().getType().equals(TransportType.facebook) ? "chat.facebook.com" : "jabber.org"));
            return username+"@"+connecthost;
        }
    }

    /**
     * Returns a username based off of a registered name (possible JID) passed in.
     *
     * If it already looks like a username, returns what was passed in.
     *
     * @param regName Registered name to turn into a username.
     * @return Converted registered name.
     */
    public String generateUsername(String regName) {
        if (regName.equals("{PLATFORM}")) {
            return JiveGlobals.getProperty("plugin.gateway.facebook.platform.apikey")+"|"+JiveGlobals.getProperty("plugin.gateway.facebook.platform.apisecret");
        }
        else if (regName.indexOf("@") > -1) {
            if (getTransport().getType().equals(TransportType.gtalk)) {
                return regName;
            }
            else {
                return regName.substring(0, regName.indexOf("@"));
            }
        }
        else {
            if (getTransport().getType().equals(TransportType.gtalk)) {
                return regName+"@gmail.com";
            }
            else {
                return regName;
            }
        }
    }

    /**
     * @see net.sf.kraken.session.TransportSession#logIn(net.sf.kraken.type.PresenceType, String)
     */
    @Override
    public void logIn(PresenceType presenceType, String verboseStatus) {
        final org.jivesoftware.smack.packet.Presence presence = new org.jivesoftware.smack.packet.Presence(org.jivesoftware.smack.packet.Presence.Type.available);
        if (JiveGlobals.getBooleanProperty("plugin.gateway."+getTransport().getType()+".avatars", true) && getAvatar() != null) {
            Avatar avatar = getAvatar();
            // Same thing in this case, so lets go ahead and set them.
            avatar.setLegacyIdentifier(avatar.getXmppHash());
            VCardUpdateExtension ext = new VCardUpdateExtension();
            ext.setPhotoHash(avatar.getLegacyIdentifier());
            presence.addExtension(ext);
        }
        final Presence.Mode pMode = ((XMPPTransport) getTransport())
                .convertGatewayStatusToXMPP(presenceType);
        if (pMode != null) {
            presence.setMode(pMode);
        }
        if (verboseStatus != null && verboseStatus.trim().length() > 0) {
            presence.setStatus(verboseStatus);
        }
        setPendingPresenceAndStatus(presenceType, verboseStatus);
        
        if (!this.isLoggedIn()) {
            listener = new XMPPListener(this);
            presenceHandler = new XMPPPresenceHandler(this);
            runThread = new Thread() {
                @Override
                public void run() {
                    String userName = generateUsername(registration.getUsername());
                    conn = new XMPPConnection(config);
                    try {
                        conn.getSASLAuthentication().registerSASLMechanism("DIGEST-MD5", MySASLDigestMD5Mechanism.class);
                        if (getTransport().getType().equals(TransportType.facebook) && registration.getUsername().equals("{PLATFORM}")) {
                            conn.getSASLAuthentication().registerSASLMechanism("X-FACEBOOK-PLATFORM", FacebookConnectSASLMechanism.class);
                            conn.getSASLAuthentication().supportSASLMechanism("X-FACEBOOK-PLATFORM", 0);
                        }

                        Roster.setDefaultSubscriptionMode(SubscriptionMode.manual);
                        conn.connect();
                        conn.addConnectionListener(listener);
                        try {
                            conn.addPacketListener(presenceHandler, new PacketTypeFilter(org.jivesoftware.smack.packet.Presence.class));
                            // Use this to filter out anything we don't care about
                            conn.addPacketListener(listener, new OrFilter(
                                    new PacketTypeFilter(GoogleMailBoxPacket.class),
                                    new PacketExtensionFilter(GoogleNewMailExtension.ELEMENT_NAME, GoogleNewMailExtension.NAMESPACE)
                            ));
                            conn.login(userName, registration.getPassword(), xmppResource);
                            conn.sendPacket(presence); // send initial presence.
                            conn.getChatManager().addChatListener(listener);
                            conn.getRoster().addRosterListener(listener);

                            if (JiveGlobals.getBooleanProperty("plugin.gateway."+getTransport().getType()+".avatars", !TransportType.facebook.equals(getTransport().getType())) && getAvatar() != null) {
                                new Thread() {
                                    @Override
                                    public void run() {
                                        Avatar avatar = getAvatar();

                                        VCard vCard = new VCard();
                                        try {
                                            vCard.load(conn);
                                            vCard.setAvatar(Base64.decode(avatar.getImageData()), avatar.getMimeType());
                                            vCard.save(conn);
                                        }
                                        catch (XMPPException e) {
                                            Log.debug("XMPP: Error while updating vcard for avatar change.", e);
                                        }
                                        catch (NotFoundException e) {
                                            Log.debug("XMPP: Unable to find avatar while setting initial.", e);
                                        }
                                    }
                                }.start();
                            }

                            setLoginStatus(TransportLoginStatus.LOGGED_IN);
                            syncUsers();

                            if (getTransport().getType().equals(TransportType.gtalk) && JiveGlobals.getBooleanProperty("plugin.gateway.gtalk.mailnotifications", true)) {
                                conn.sendPacket(new IQWithPacketExtension(generateFullJID(getRegistration().getUsername()), new GoogleUserSettingExtension(null, true, null), IQ.Type.SET));
                                conn.sendPacket(new IQWithPacketExtension(generateFullJID(getRegistration().getUsername()), new GoogleMailNotifyExtension()));
                                mailCheck = new MailCheck();
                                timer.schedule(mailCheck, timerInterval, timerInterval);
                            }
                        }
                        catch (XMPPException e) {
                            Log.debug(getTransport().getType()+" user's login/password does not appear to be correct: "+getRegistration().getUsername(), e);
                            setFailureStatus(ConnectionFailureReason.USERNAME_OR_PASSWORD_INCORRECT);
                            sessionDisconnectedNoReconnect(LocaleUtils.getLocalizedString("gateway.xmpp.passwordincorrect", "kraken"));
                        }
                    }
                    catch (XMPPException e) {
                        Log.debug(getTransport().getType()+" user is not able to connect: "+getRegistration().getUsername(), e);
                        setFailureStatus(ConnectionFailureReason.CAN_NOT_CONNECT);                        
                        sessionDisconnected(LocaleUtils.getLocalizedString("gateway.xmpp.connectionfailed", "kraken"));
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
        if (timer != null) {
            try {
                timer.cancel();
            }
            catch (Exception e) {
                // Ignore
            }
            timer = null;
        }
        if (mailCheck != null) {
            try {
                mailCheck.cancel();
            }
            catch (Exception e) {
                // Ignore
            }
            mailCheck = null;
        }
        if (conn != null) {
            try {
                conn.removeConnectionListener(listener);
            }
            catch (Exception e) {
                // Ignore
            }
            
            try {
                conn.removePacketListener(listener);
            }
            catch (Exception e) {
                // Ignore
            }
            try {
            	conn.removePacketListener(presenceHandler);
            } catch (Exception e) {
            	// Ignore
            }
            try {
                conn.getChatManager().removeChatListener(listener);
            }
            catch (Exception e) {
                // Ignore
            }
            try {
                conn.getRoster().removeRosterListener(listener);
            }
            catch (Exception e) {
                // Ignore
            }
            try {
                conn.disconnect();
            }
            catch (Exception e) {
                // Ignore
            }
        }
        conn = null;
        listener = null;
        presenceHandler = null;
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
     * @see net.sf.kraken.session.TransportSession#updateStatus(net.sf.kraken.type.PresenceType, String)
     */
    @Override
    public void updateStatus(PresenceType presenceType, String verboseStatus) {
        setPresenceAndStatus(presenceType, verboseStatus);
        final org.jivesoftware.smack.packet.Presence presence = constructCurrentLegacyPresencePacket();

        try {
            conn.sendPacket(presence);
        }
        catch (IllegalStateException e) {
            Log.debug("XMPP: Not connected while trying to change status.");
        }
    }

    /**
     * @see net.sf.kraken.session.TransportSession#addContact(org.xmpp.packet.JID, String, java.util.ArrayList)
     */
    @Override
    public void addContact(JID jid, String nickname, ArrayList<String> groups) {
        String mail = getTransport().convertJIDToID(jid);
        try {
            conn.getRoster().createEntry(mail, nickname, groups.toArray(new String[groups.size()]));
            RosterEntry entry = conn.getRoster().getEntry(mail);

            getBuddyManager().storeBuddy(new XMPPBuddy(getBuddyManager(), mail, nickname, entry.getGroups(), entry));
        }
        catch (XMPPException ex) {
            Log.debug("XMPP: unable to add:"+ mail);
        }
    }

    /**
     * @see net.sf.kraken.session.TransportSession#removeContact(net.sf.kraken.roster.TransportBuddy)
     */
    @Override
    public void removeContact(XMPPBuddy contact) {
        RosterEntry user2remove;
        String mail = getTransport().convertJIDToID(contact.getJID());
        user2remove =  conn.getRoster().getEntry(mail);
        try {
            conn.getRoster().removeEntry(user2remove);
        }
        catch (XMPPException ex) {
            Log.debug("XMPP: unable to remove:"+ mail);
        }
    }

    /**
     * @see net.sf.kraken.session.TransportSession#updateContact(net.sf.kraken.roster.TransportBuddy)
     */
    @Override
    public void updateContact(XMPPBuddy contact) {
        RosterEntry user2Update;
        String mail = getTransport().convertJIDToID(contact.getJID());
        user2Update =  conn.getRoster().getEntry(mail);
        user2Update.setName(contact.getNickname());
        Collection<String> newgroups = contact.getGroups();
        if (newgroups == null) {
            newgroups = new ArrayList<String>();
        }
        for (RosterGroup group : conn.getRoster().getGroups()) {
            if (newgroups.contains(group.getName())) {
                if (!group.contains(user2Update)) {
                    try {
                        group.addEntry(user2Update);
                    }
                    catch (XMPPException e) {
                        Log.debug("XMPP: Unable to add roster item to group.");
                    }
                }
                newgroups.remove(group.getName());
            }
            else {
                if (group.contains(user2Update)) {
                    try {
                        group.removeEntry(user2Update);
                    }
                    catch (XMPPException e) {
                        Log.debug("XMPP: Unable to delete roster item from group.");
                    }
                }
            }
        }
        for (String group : newgroups) {
            RosterGroup newgroup = conn.getRoster().createGroup(group);
            try {
                newgroup.addEntry(user2Update);
            }
            catch (XMPPException e) {
                Log.debug("XMPP: Unable to add roster item to new group.");
            }
        }
    }
    
    /**
     * @see net.sf.kraken.session.TransportSession#acceptAddContact(JID)
     */
    @Override
    public void acceptAddContact(JID jid) {
        final String userID = getTransport().convertJIDToID(jid);
        Log.debug("XMPP: accept-add contact: " + userID);
        
        final Presence accept = new Presence(Type.subscribed);
        accept.setTo(userID);
        conn.sendPacket(accept);
    }
    
    /**
     * @see net.sf.kraken.session.TransportSession#sendMessage(org.xmpp.packet.JID, String)
     */
    @Override
    public void sendMessage(JID jid, String message) {
        Chat chat = conn.getChatManager().createChat(getTransport().convertJIDToID(jid), listener);
        try {
            chat.sendMessage(message);
        }
        catch (XMPPException e) {
            // Ignore
        }
    }

    /**
     * @see net.sf.kraken.session.TransportSession#sendChatState(org.xmpp.packet.JID, net.sf.kraken.type.ChatStateType)
     */
    @Override
    public void sendChatState(JID jid, ChatStateType chatState) {
        final Presence presence = conn.getRoster().getPresence(jid.toString());
        if (presence == null  || presence.getType().equals(Presence.Type.unavailable)) {
            // don't send chat state to contacts that are offline.
            return;
        }
        Chat chat = conn.getChatManager().createChat(getTransport().convertJIDToID(jid), listener);
        try {
            ChatState state = ChatState.active;
            switch (chatState) {
                case active:    state = ChatState.active;    break;
                case composing: state = ChatState.composing; break;
                case paused:    state = ChatState.paused;    break;
                case inactive:  state = ChatState.inactive;  break;
                case gone:      state = ChatState.gone;      break;
            }

            Message message = new Message();
            message.addExtension(new ChatStateExtension(state));
            chat.sendMessage(message);
        }
        catch (XMPPException e) {
            // Ignore
        }
    }

    /**
     * @see net.sf.kraken.session.TransportSession#sendBuzzNotification(org.xmpp.packet.JID, String)
     */
    @Override
    public void sendBuzzNotification(JID jid, String message) {
        Chat chat = conn.getChatManager().createChat(getTransport().convertJIDToID(jid), listener);
        try {
            Message m = new Message();
            m.setTo(getTransport().convertJIDToID(jid));
            m.addExtension(new BuzzExtension());
            chat.sendMessage(m);
        }
        catch (XMPPException e) {
            // Ignore
        }
    }

    /**
     * Returns a (legacy/Smack-based) Presence stanza that represents the
     * current presence of this session. The Presence includes relevant Mode,
     * Status and VCardUpdate information.
     * 
     * This method uses the fields {@link TransportSession#presence} and
     * {@link TransportSession#verboseStatus} to generate the result.
     * 
     * @return A Presence packet representing the current presence state of this
     *         session.
     */
    public Presence constructCurrentLegacyPresencePacket() {
        final org.jivesoftware.smack.packet.Presence presence = new org.jivesoftware.smack.packet.Presence(
                org.jivesoftware.smack.packet.Presence.Type.available);
        final Presence.Mode pMode = ((XMPPTransport) getTransport())
                .convertGatewayStatusToXMPP(this.presence);
        if (pMode != null) {
            presence.setMode(pMode);
        }
        if (verboseStatus != null && verboseStatus.trim().length() > 0) {
            presence.setStatus(verboseStatus);
        }
        final Avatar avatar = getAvatar();
        if (avatar != null) {
            final VCardUpdateExtension ext = new VCardUpdateExtension();
            ext.setPhotoHash(avatar.getLegacyIdentifier());
            presence.addExtension(ext);
        }
        return presence;
    }
    
    /**
     * @see net.sf.kraken.session.TransportSession#updateLegacyAvatar(String, byte[])
     */
    @Override
    public void updateLegacyAvatar(String type, final byte[] data) {
        new Thread() {
            @Override
            public void run() {
                Avatar avatar = getAvatar();

                VCard vCard = new VCard();
                try {
                    vCard.load(conn);
                    vCard.setAvatar(data, avatar.getMimeType());
                    vCard.save(conn);

                    avatar.setLegacyIdentifier(avatar.getXmppHash());
                    
                    // Same thing in this case, so lets go ahead and set them.
                    final org.jivesoftware.smack.packet.Presence presence = constructCurrentLegacyPresencePacket();
                    conn.sendPacket(presence);
                }
                catch (XMPPException e) {
                    Log.debug("XMPP: Error while updating vcard for avatar change.", e);
                }
            }
        }.start();
    }
    
    private void syncUsers() {
        for (RosterEntry entry : conn.getRoster().getEntries()) {
            getBuddyManager().storeBuddy(new XMPPBuddy(getBuddyManager(), entry.getUser(), entry.getName(), entry.getGroups(), entry));
            // Facebook does not support presence probes in their XMPP implementation. See http://developers.facebook.com/docs/chat#features
            if (!TransportType.facebook.equals(getTransport().getType())) {
                //ProbePacket probe = new ProbePacket(this.getJID()+"/"+xmppResource, entry.getUser());
                ProbePacket probe = new ProbePacket(null, entry.getUser());
                Log.debug("XMPP: Sending the following probe packet: "+probe.toXML());
                try {
                    conn.sendPacket(probe);
                }
                catch (IllegalStateException e) {
                    Log.debug("XMPP: Not connected while trying to send probe.");
                }
            }
        }

        try {
            getTransport().syncLegacyRoster(getJID(), getBuddyManager().getBuddies());
        }
        catch (UserNotFoundException ex) {
            Log.error("XMPP: User not found while syncing legacy roster: ", ex);
        }

        getBuddyManager().activate();

        // lets repoll the roster since smack seems to get out of sync...
        // we'll let the roster listener take care of this though.
        conn.getRoster().reload();
    }

    private class MailCheck extends TimerTask {
        /**
         * Check GMail for new mail.
         */
        @Override
        public void run() {
            if (getTransport().getType().equals(TransportType.gtalk) && JiveGlobals.getBooleanProperty("plugin.gateway.gtalk.mailnotifications", true)) {
                GoogleMailNotifyExtension gmne = new GoogleMailNotifyExtension();
                gmne.setNewerThanTime(listener.getLastGMailThreadDate());
                gmne.setNewerThanTid(listener.getLastGMailThreadId());
                conn.sendPacket(new IQWithPacketExtension(generateFullJID(getRegistration().getUsername()), gmne));
            }
        }
    }
    
}
