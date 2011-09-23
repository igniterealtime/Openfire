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

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Date;
import java.util.Collection;

import net.sf.kraken.BaseTransport;
import net.sf.kraken.protocols.xmpp.packet.AttentionExtension;
import net.sf.kraken.protocols.xmpp.packet.GoogleMailBoxPacket;
import net.sf.kraken.protocols.xmpp.packet.GoogleMailNotifyExtension;
import net.sf.kraken.protocols.xmpp.packet.GoogleMailSender;
import net.sf.kraken.protocols.xmpp.packet.GoogleMailThread;
import net.sf.kraken.protocols.xmpp.packet.GoogleNewMailExtension;
import net.sf.kraken.protocols.xmpp.packet.IQWithPacketExtension;
import net.sf.kraken.protocols.xmpp.packet.ProbePacket;
import net.sf.kraken.type.ChatStateType;
import net.sf.kraken.type.ConnectionFailureReason;
import net.sf.kraken.type.NameSpace;
import net.sf.kraken.type.TransportType;

import org.apache.log4j.Logger;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManagerListener;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.Message.Type;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterListener;
import org.jivesoftware.smackx.packet.ChatStateExtension;
import org.jivesoftware.smackx.packet.DelayInformation;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

/**
 * Handles incoming events from XMPP server.
 *
 * @author Daniel Henninger
 * @author Mehmet Ecevit
 */
public class XMPPListener implements MessageListener, ConnectionListener, ChatManagerListener, PacketListener, RosterListener {

    static Logger Log = Logger.getLogger(XMPPListener.class);

    /**
     * Creates an XMPP listener instance and ties to session.
     *
     * @param session Session this listener is associated with.
     */
    public XMPPListener(XMPPSession session) {
        this.xmppSessionRef = new WeakReference<XMPPSession>(session);
    }
    
    /**
     * Session instance that the listener is associated with.
     */
    public WeakReference<XMPPSession> xmppSessionRef = null;

    /**
     * Last google mail thread id we saw.
     */
    public Long lastGMailThreadId = null;

    /**
     * Last google mail thread date we saw.
     */
    public Date lastGMailThreadDate = null;

    /**
     * Returns the XMPP session this listener is attached to.
     *
     * @return XMPP session we are attached to.
     */
    public XMPPSession getSession() {
        return xmppSessionRef.get();
    }

    /**
     * Handles incoming messages.
     *
     * @param chat Chat instance this message is associated with.
     * @param message Message received.
     */
    public void processMessage(Chat chat, org.jivesoftware.smack.packet.Message message) {
        Log.debug("Received "+getSession().getTransport().getType().name()+" message: "+message.toXML());
        try {
            final BaseTransport<XMPPBuddy> transport = getSession().getTransport();
            final JID legacyJID = transport.convertIDToJID(message.getFrom());
            final JID localJID = getSession().getJID();
            final PacketExtension pe = message.getExtension("x", NameSpace.X_DELAY);
            final PacketExtension attExt = message.getExtension(AttentionExtension.ELEMENT_NAME, AttentionExtension.NAMESPACE);
            
            if (pe != null && pe instanceof DelayInformation) {
                DelayInformation di = (DelayInformation)pe;
                transport.sendOfflineMessage(
                        localJID,
                        legacyJID,
                        message.getBody(),
                        di.getStamp(),
                        di.getReason()
                );
            } else if (attExt != null && (attExt instanceof AttentionExtension)) {
                transport.sendAttentionNotification(localJID, legacyJID, message.getBody());
            }
            else {
                // see if we got sent chat state notifications
                final PacketExtension cse = message
                        .getExtension("http://jabber.org/protocol/chatstates");
                if (cse != null && cse instanceof ChatStateExtension) {
                    final String chatState = cse.getElementName();
                    try {
                        final ChatStateType cst = ChatStateType.valueOf(
                                ChatStateType.class, chatState);
                        switch (cst) {
                            case active:
                                // only send a 'stand alone' active state if the
                                // message stanza does not include a text
                                // message. If it does, the 'active' state is
                                // included with that chat message below.
                                if (message.getBody() == null
                                        || message.getBody().trim().length() == 0) {
                                    transport.sendChatActiveNotification(
                                            localJID, legacyJID);
                                }
                                break;

                            case composing:
                                transport.sendComposingNotification(localJID,
                                        legacyJID);
                                break;

                            case gone:
                                transport.sendChatGoneNotification(localJID,
                                        legacyJID);
                                break;

                            case inactive:
                                transport.sendChatInactiveNotification(
                                        localJID, legacyJID);
                                break;

                            case paused:
                                transport.sendComposingPausedNotification(
                                        localJID, legacyJID);
                                break;

                            default:
                                Log.debug("Unexpected chat state recieved: " + cst);
                                break;

                        }
                    }
                    catch (IllegalArgumentException ex) {
                        Log.warn("Illegal chat state notification "
                                + "received from legacy domain: " + chatState);
                    }

                }
                if (message.getType() == Type.error) {
                    Log.debug("Received an error message! Message: " + message.toXML());
                    transport.sendMessage(localJID, legacyJID, message.getBody(), Message.Type.error);
                } else {
                    transport.sendMessage(localJID, legacyJID, message.getBody());
                }
            }
//            if (message.getProperty("time") == null || message.getProperty("time").equals("")) {
//            }
//            else {
//                getSession().getTransport().sendOfflineMessage(
//                        getSession().getJID(),
//                        getSession().getTransport().convertIDToJID(message.getFrom()),
//                        message.getBody(),
//                        Message.Type.chat,
//                        message.getProperty("time").toString()
//                );
//            }
            
        }
        catch (Exception ex) {
            Log.debug("E001:"+ ex.getMessage(), ex);
        }
    }

    public void connectionClosed() {
        getSession().sessionDisconnectedNoReconnect(null);
    }

    public void connectionClosedOnError(Exception exception) {
        getSession().setFailureStatus(ConnectionFailureReason.UNKNOWN);        
        getSession().sessionDisconnected(LocaleUtils.getLocalizedString("gateway.xmpp.connectionclosed", "kraken"));
    }

    public void reconnectingIn(int i) {
        //Ignoring for now
    }

    public void reconnectionSuccessful() {
        //Ignoring for now
    }

    public void reconnectionFailed(Exception exception) {
        //Ignoring for now
    }

    public void chatCreated(Chat chat, boolean b) {
        chat.addMessageListener(this);
    }

    public void processPacket(Packet packet) {
        if (packet instanceof GoogleMailBoxPacket) {
            if (JiveGlobals.getBooleanProperty("plugin.gateway.gtalk.mailnotifications", true)) {
                GoogleMailBoxPacket mbp = (GoogleMailBoxPacket)packet;
                this.setLastGMailThreadDate(mbp.getResultTime());
                Integer newMailCount = 0;
                String mailList = "";
                for (GoogleMailThread mail : mbp.getMailThreads()) {
                    newMailCount++;
                    if (this.getLastGMailThreadId() == null || mail.getThreadId() > this.getLastGMailThreadId()) {
                        this.setLastGMailThreadId(mail.getThreadId());
                    }
                    String senderList = "";
                    for (GoogleMailSender sender : mail.getSenders()) {
                        if (!senderList.equals("")) {
                            senderList += ", ";
                        }
                        String name = sender.getName();
                        if (name != null) {
                            senderList += name + " <";
                        }
                        senderList += sender.getAddress();
                        if (name != null) {
                            senderList += ">";
                        }
                    }
                    mailList += "\n   "+senderList+" sent "+mail.getSubject();
                }
                if (newMailCount > 0) {
                    getSession().getTransport().sendMessage(
                            getSession().getJID(),
                            getSession().getTransport().getJID(),
                            LocaleUtils.getLocalizedString("gateway.gtalk.mail", "kraken", Arrays.asList(newMailCount))+mailList,
                            Message.Type.headline
                    );
                }
            }
        }
        else if (packet instanceof IQ) {
            Log.debug("XMPP: Got google mail notification");
            GoogleNewMailExtension gnme = (GoogleNewMailExtension)packet.getExtension(GoogleNewMailExtension.ELEMENT_NAME, GoogleNewMailExtension.NAMESPACE);
            if (gnme != null) {
                Log.debug("XMPP: Sending google mail request");
                getSession().conn.sendPacket(new IQWithPacketExtension(new GoogleMailNotifyExtension()));
            }
        }
    }

    public Long getLastGMailThreadId() {
        return lastGMailThreadId;
    }

    public void setLastGMailThreadId(Long lastGMailThreadId) {
        this.lastGMailThreadId = lastGMailThreadId;
    }

    public Date getLastGMailThreadDate() {
        return lastGMailThreadDate;
    }

    public void setLastGMailThreadDate(Date lastGMailThreadDate) {
        this.lastGMailThreadDate = lastGMailThreadDate;
    }

    public void entriesAdded(Collection<String> addresses) {
        for (String addr : addresses) {
            RosterEntry entry = getSession().conn.getRoster().getEntry(addr);
            getSession().getBuddyManager().storeBuddy(new XMPPBuddy(getSession().getBuddyManager(), entry.getUser(), entry.getName(), entry.getGroups(), entry));

            // Facebook does not support presence probes in their XMPP implementation. See http://developers.facebook.com/docs/chat#features
            if (!TransportType.facebook.equals(getSession().getTransport().getType())) {
                //ProbePacket probe = new ProbePacket(getSession().getJID()+"/"+getSession().xmppResource, entry.getUser());
                ProbePacket probe = new ProbePacket(null, entry.getUser());
                Log.debug("XMPP: Sending the following probe packet: "+probe.toXML());
                try {
                    getSession().conn.sendPacket(probe);
                }
                catch (IllegalStateException e) {
                    Log.debug("XMPP: Not connected while trying to send probe.");
                }
            }
        }
    }

    public void entriesUpdated(Collection<String> addresses) {
        // TODO: Check if we need to do something with this later
    }

    public void entriesDeleted(Collection<String> addresses) {
        for (String addr : addresses) {
            getSession().getBuddyManager().removeBuddy(addr);
        }
    }

    public void presenceChanged(org.jivesoftware.smack.packet.Presence presence) {
        // Uhm why do we need this given that we have a presence listener
    }

}
