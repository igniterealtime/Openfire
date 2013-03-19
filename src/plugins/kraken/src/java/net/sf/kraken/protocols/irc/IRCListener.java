/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.irc;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

import net.sf.kraken.muc.BaseMUCTransport;
import net.sf.kraken.muc.MUCTransportRoom;
import net.sf.kraken.muc.MUCTransportRoomMember;
import net.sf.kraken.muc.MUCTransportSession;
import net.sf.kraken.roster.TransportBuddy;
import net.sf.kraken.type.PresenceType;

import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.NotFoundException;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;

import f00f.net.irc.martyr.GenericAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.State;
import f00f.net.irc.martyr.clientstate.Channel;
import f00f.net.irc.martyr.clientstate.Member;
import f00f.net.irc.martyr.commands.ChannelModeCommand;
import f00f.net.irc.martyr.commands.CtcpMessage;
import f00f.net.irc.martyr.commands.CtcpNotice;
import f00f.net.irc.martyr.commands.InviteCommand;
import f00f.net.irc.martyr.commands.IsonCommand;
import f00f.net.irc.martyr.commands.JoinCommand;
import f00f.net.irc.martyr.commands.KickCommand;
import f00f.net.irc.martyr.commands.MessageCommand;
import f00f.net.irc.martyr.commands.ModeCommand;
import f00f.net.irc.martyr.commands.NickCommand;
import f00f.net.irc.martyr.commands.NoticeCommand;
import f00f.net.irc.martyr.commands.PartCommand;
import f00f.net.irc.martyr.commands.QuitCommand;
import f00f.net.irc.martyr.commands.TopicCommand;
import f00f.net.irc.martyr.replies.AwayReply;
import f00f.net.irc.martyr.replies.ListEndReply;
import f00f.net.irc.martyr.replies.ListReply;
import f00f.net.irc.martyr.replies.ListStartReply;
import f00f.net.irc.martyr.replies.NamesEndReply;
import f00f.net.irc.martyr.replies.NamesReply;
import f00f.net.irc.martyr.replies.NowAwayReply;
import f00f.net.irc.martyr.replies.TopicInfoReply;
import f00f.net.irc.martyr.replies.UnAwayReply;
import f00f.net.irc.martyr.util.IRCStringUtils;

/**
 * @author Daniel Henninger
 */
public class IRCListener extends GenericAutoService {

    static Logger Log = Logger.getLogger(IRCListener.class);

    public IRCListener(IRCSession session) {
        super(session.getConnection());
        ircSessionRef = new WeakReference<IRCSession>(session);
    }

    WeakReference<IRCSession> ircSessionRef;

    public IRCSession getSession() {
        return ircSessionRef.get();
    }

    @Override
    protected void updateState(State state) {
        Log.debug("IRC: Received incoming state:"+state);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void updateCommand(InCommand inCommand) {
        Log.debug("IRC: Received incoming command:"+inCommand);
        if (inCommand instanceof CtcpMessage) {
            CtcpMessage cm = (CtcpMessage)inCommand;
            if (cm.getAction().equals("VERSION")) {
                getSession().getConnection().sendCommand(new CtcpNotice(cm.getSource().getNick(), "VERSION", "IMGateway"+getSession().getTransport().getVersionString()+":Java:-"));
            }
            else if (cm.getAction().equals("PING")) {
                String timestamp = cm.getMessage();
                getSession().getConnection().sendCommand(new CtcpNotice(cm.getSource().getNick(), "PING", timestamp));
            }
            else if (cm.getAction().equals("TIME")) {
                Date current = new Date();
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ZZZZZ");
                getSession().getConnection().sendCommand(new CtcpNotice(cm.getSource().getNick(), "TIME", format.format(current)));
            }
            else if (cm.getAction().equals("ACTION")) {
                if (cm.isPrivateToUs(getSession().getConnection().getClientState())) {
                    getSession().getTransport().sendMessage(
                            getSession().getJID(),
                            getSession().getTransport().convertIDToJID(cm.getSource().getNick()),
                            "/me "+cm.getMessage()
                    );
                }
                else {
                    getSession().getTransport().sendMessage(
                            getSession().getJID(),
                            getSession().getTransport().getMUCTransport().convertIDToJID(cm.getDest(), cm.getSource().getNick()),
                            "/me "+cm.getMessage(),
                            Message.Type.groupchat
                    );
                }
            }
        }
        else if (inCommand instanceof MessageCommand) {
            MessageCommand mc = (MessageCommand)inCommand;
            if (mc.isPrivateToUs(getSession().getConnection().getClientState())) {
                getSession().getTransport().sendMessage(
                        getSession().getJID(),
                        getSession().getTransport().convertIDToJID(mc.getSource().getNick()),
                        IRCStringUtils.stripControlChars(mc.getMessage())
                );
            }
            else {
                getSession().getTransport().sendMessage(
                        getSession().getJID(),
                        getSession().getTransport().getMUCTransport().convertIDToJID(mc.getDest(), mc.getSource().getNick()),
                        IRCStringUtils.stripControlChars(mc.getMessage()),
                        Message.Type.groupchat
                );
            }
        }
        else if (inCommand instanceof NoticeCommand) {
            NoticeCommand nc = (NoticeCommand)inCommand;
            if (nc.getFrom() != null) {
                getSession().getTransport().sendMessage(
                        getSession().getJID(),
                        getSession().getTransport().convertIDToJID(nc.getFrom().getNick()),
                        IRCStringUtils.stripControlChars(nc.getNotice())
                );
            }
        }
        else if (inCommand instanceof JoinCommand) {
            JoinCommand jc = (JoinCommand)inCommand;
            try {
                IRCMUCSession mucSession = (IRCMUCSession)getSession().getMUCSessionManager().getSession(jc.getChannel());
                mucSession.getContacts().add(jc.getUser().getNick());

                Presence p = new Presence();
                p.setFrom(getSession().getTransport().getMUCTransport().convertIDToJID(jc.getChannel(), jc.getUser().getNick()));
                p.setTo(getSession().getJID());
                Element elem = p.addChildElement("x", "http://jabber.org/protocol/muc#user");
                Element item = elem.addElement("item");
                item.addAttribute("affiliation", "member");
                item.addAttribute("role", "participant");
                getSession().getTransport().sendPacket(p);
            }
            catch (NotFoundException e) {
                Log.debug("Received information for IRC session that doesn't exist.");
            }
        }
        else if (inCommand instanceof PartCommand) {
            PartCommand pc = (PartCommand)inCommand;
            try {
                IRCMUCSession mucSession = (IRCMUCSession)getSession().getMUCSessionManager().getSession(pc.getChannel());
                mucSession.getContacts().remove(pc.getUser().getNick());

                Presence p = new Presence();
                p.setType(Presence.Type.unavailable);
                p.setFrom(getSession().getTransport().getMUCTransport().convertIDToJID(pc.getChannel(), pc.getUser().getNick()));
                p.setTo(getSession().getJID());
                if (pc.getReason() != null && !pc.getReason().equals("")) {
                    p.setStatus(pc.getReason());
                }
                Element elem = p.addChildElement("x", "http://jabber.org/protocol/muc#user");
                Element item = elem.addElement("item");
                item.addAttribute("affiliation", "none");
                item.addAttribute("role", "none");
                getSession().getTransport().sendPacket(p);
            }
            catch (NotFoundException e) {
                Log.debug("Received information for IRC session that doesn't exist.");
            }
        }
        else if (inCommand instanceof QuitCommand) {
            QuitCommand qc = (QuitCommand)inCommand;
            for (MUCTransportSession session : getSession().getMUCSessionManager().getSessions()) {
                if (((IRCMUCSession)session).getContacts().contains(qc.getUser().getNick())) {
                    ((IRCMUCSession)session).getContacts().remove(qc.getUser().getNick());
                    Presence p = new Presence();
                    p.setType(Presence.Type.unavailable);
                    p.setFrom(getSession().getTransport().getMUCTransport().convertIDToJID(((IRCMUCSession)session).roomname, qc.getUser().getNick()));
                    p.setTo(getSession().getJID());
                    if (qc.getReason() != null && !qc.getReason().equals("")) {
                        p.setStatus(qc.getReason());
                    }
                    Element elem = p.addChildElement("x", "http://jabber.org/protocol/muc#user");
                    Element item = elem.addElement("item");
                    item.addAttribute("affiliation", "none");
                    item.addAttribute("role", "none");
                    getSession().getTransport().sendPacket(p);
                }
            }
        }
        else if (inCommand instanceof InviteCommand) {
            InviteCommand ic = (InviteCommand)inCommand;
            BaseMUCTransport mucTransport = getSession().getTransport().getMUCTransport();
            Message m = new Message();
            m.setTo(getSession().getJID());
            m.setFrom(mucTransport.convertIDToJID(ic.getChannel(), null));
            Element x = m.addChildElement("x", "http://jabber.org/protocol/muc#user");
            Element invite = x.addElement("invite");
            invite.addAttribute("from", getSession().getTransport().convertIDToJID(ic.getSourceString()).toBareJID());
            getSession().getTransport().sendPacket(m);
        }
        else if (inCommand instanceof KickCommand) {
            KickCommand kc = (KickCommand)inCommand;
            BaseMUCTransport mucTransport = getSession().getTransport().getMUCTransport();
            try {
                IRCMUCSession mucSession = (IRCMUCSession)getSession().getMUCSessionManager().getSession(kc.getChannel());
                mucSession.getContacts().add(kc.getKicked().getNick());

                Presence p = new Presence();
                p.setType(Presence.Type.unavailable);
                p.setFrom(mucTransport.convertIDToJID(kc.getChannel(), kc.getKicked().getNick()));
                p.setTo(getSession().getJID());
                Element elem = p.addChildElement("x", "http://jabber.org/protocol/muc#user");
                Element item = elem.addElement("item");
                item.addAttribute("affiliation", "none");
                item.addAttribute("role", "none");
                Element actor = item.addElement("actor");
                actor.addAttribute("jid", getSession().getTransport().convertIDToJID(kc.getKicker().getNick()).toBareJID());
                Element reason = item.addElement("reason");
                reason.addText(kc.getComment());
                Element status = elem.addElement("status");
                status.addAttribute("code", "307");
                getSession().getTransport().sendPacket(p);
            }
            catch (NotFoundException e) {
                Log.debug("Received information for IRC session that doesn't exist.");
            }

            if (kc.kickedUs(getSession().getConnection().getClientState())) {
                getSession().getMUCSessionManager().removeSession(kc.getChannel());
            }
        }
        else if (inCommand instanceof ChannelModeCommand) {
            //ChannelModeCommand cmc = (ChannelModeCommand)inCommand;
            // TODO: Fix up martyr to handle this then handle it here
        }
        else if (inCommand instanceof NickCommand) {
            //NickCommand nc = (NickCommand)inCommand;
            // TODO: Map to MUC event  (someone's nick just changed)
        }
        else if (inCommand instanceof ModeCommand) {
            //ModeCommand mc = (ModeCommand)inCommand;
            // TODO: Map to MUC event  (your mode just changed)
        }
        else if (inCommand instanceof TopicCommand) {
            TopicCommand tc = (TopicCommand)inCommand;
            Channel channel = getSession().getConnection().getClientState().getChannel(tc.getChannel());
            if (channel != null) {
                BaseMUCTransport mucTransport = getSession().getTransport().getMUCTransport();
                Message m = new Message();
                m.setType(Message.Type.groupchat);
                m.setTo(getSession().getJID());
                m.setFrom(mucTransport.convertIDToJID(channel.getName(), channel.getTopicAuthor()));
                m.setSubject(net.sf.kraken.util.StringUtils.removeInvalidXMLCharacters(channel.getTopic()));
                mucTransport.sendPacket(m);
            }
        }
        else if (inCommand instanceof TopicInfoReply) {
            TopicInfoReply tir = (TopicInfoReply)inCommand;
            Channel channel = getSession().getConnection().getClientState().getChannel(tir.getChannel());
            if (channel != null) {
                BaseMUCTransport mucTransport = getSession().getTransport().getMUCTransport();
                Message m = new Message();
                m.setType(Message.Type.groupchat);
                m.setTo(getSession().getJID());
                m.setFrom(mucTransport.convertIDToJID(channel.getName(), channel.getTopicAuthor()));
                m.setSubject(net.sf.kraken.util.StringUtils.removeInvalidXMLCharacters(channel.getTopic()));
                mucTransport.sendPacket(m);
            }
        }
        else if (inCommand instanceof NamesReply) {
            NamesReply nr = (NamesReply)inCommand;

            String channelName = nr.getChannel();
            List<MUCTransportRoomMember> members = new ArrayList<MUCTransportRoomMember>();
            for (String nick : nr.getNames()) {
                members.add(new MUCTransportRoomMember(getSession().getTransport().getMUCTransport().convertIDToJID(channelName, nick)));
            }

            // This will be ignored if no one asked for it.
            getSession().getTransport().getMUCTransport().sendRoomMembers(getSession().getJID(), getSession().getTransport().getMUCTransport().convertIDToJID(channelName, null), members);
        }
        else if (inCommand instanceof NamesEndReply) {
            NamesEndReply ner = (NamesEndReply)inCommand;
            BaseMUCTransport mucTransport = getSession().getTransport().getMUCTransport();
            try {
                IRCMUCSession mucSession = (IRCMUCSession)getSession().getMUCSessionManager().getSession(ner.getChannel());
                mucSession.getContacts().clear();

                Member myMember = null;
                Channel channel = getSession().getConnection().getClientState().getChannel(ner.getChannel());
                if (channel != null) {
                    Enumeration members = channel.getMembers();
                    while (members.hasMoreElements()) {
                        Member member = (Member)members.nextElement();

                        if (member.getNick().getNick().equals(mucSession.getNickname()) || member.getNick().getNick().equals(getSession().getRegistration().getNickname())) {
                            // Aha, this is us, save for the end.
                            myMember = member;
                            continue;
                        }
                        Presence p = new Presence();
                        p.setTo(getSession().getJID());
                        if (member.hasOps()) {
                            // Moderator.
                            mucSession.getContacts().add(member.getNick().getNick());
                            p.setFrom(mucTransport.convertIDToJID(ner.getChannel(), member.getNick().getNick()));
                            Element elem = p.addChildElement("x", "http://jabber.org/protocol/muc#user");
                            Element item = elem.addElement("item");
                            item.addAttribute("affiliation", "admin");
                            item.addAttribute("role", "moderator");
                        }
                        else {
                            // Regular participant.
                            mucSession.getContacts().add(member.getNick().getNick());
                            p.setFrom(mucTransport.convertIDToJID(ner.getChannel(), member.getNick().getNick()));
                            Element elem = p.addChildElement("x", "http://jabber.org/protocol/muc#user");
                            Element item = elem.addElement("item");
                            item.addAttribute("affiliation", "member");
                            item.addAttribute("role", "participant");
                        }
                        mucTransport.sendPacket(p);
                    }
                }
                if (myMember != null) {
                    Presence p = new Presence();
                    p.setTo(getSession().getJID());
                    p.setFrom(mucTransport.convertIDToJID(ner.getChannel(), mucSession.getNickname()));
                    Element elem = p.addChildElement("x", "http://jabber.org/protocol/muc#user");
                    Element item = elem.addElement("item");
                    if (myMember.hasOps()) {
                        item.addAttribute("affiliation", "admin");
                        item.addAttribute("role", "moderator");
                    }
                    else {
                        item.addAttribute("affiliation", "member");
                        item.addAttribute("role", "participant");
                    }
                    Element status = elem.addElement("status");
                    status.addAttribute("code", "110");
                    mucTransport.sendPacket(p);
                }
            }
            catch (NotFoundException e) {
                Log.debug("Received information for IRC session that doesn't exist.");
            }
        }
        else if (inCommand instanceof ListStartReply) {
            // Do nothing.
        }
        else if (inCommand instanceof ListReply) {
            ListReply lr = (ListReply)inCommand;

            String channelName = lr.getChannel();
            MUCTransportRoom mucRoom = getSession().getTransport().getMUCTransport().getCachedRoom(channelName);
            if (mucRoom == null) {
                mucRoom = new MUCTransportRoom(getSession().getTransport().getMUCTransport().convertIDToJID(channelName, ""), channelName);
            }
            mucRoom.setTopic(lr.getTopic());
            mucRoom.setOccupant_count(lr.getMemberCount());
            getSession().getTransport().getMUCTransport().cacheRoom(mucRoom);

            // This will be ignored if no one asked for it.
            getSession().getTransport().getMUCTransport().sendRoomInfo(getSession().getJID(), getSession().getTransport().getMUCTransport().convertIDToJID(mucRoom.getName(), null), mucRoom);
        }
        else if (inCommand instanceof ListEndReply) {
            // This will be ignored if no one asked for it.
            getSession().getTransport().getMUCTransport().sendRooms(getSession().getJID(), getSession().getTransport().getMUCTransport().getCachedRooms());
        }
        else if (inCommand instanceof IsonCommand) {
            IsonCommand ic = (IsonCommand)inCommand;
            List<String> newNicks = new ArrayList<String>();
            for (String nick : ic.getNicks()) {
                newNicks.add(nick.toLowerCase());
            }
            Log.debug("IRC: Got ISON for "+ic.getDest()+" of: "+ic.getNicks());
            for (TransportBuddy buddy : getSession().getBuddyManager().getBuddies()) {
                if (!newNicks.contains(buddy.getName())) {
                    // Previously online nick went offline
                    buddy.setPresence(PresenceType.unavailable);
                }
                else {
                    // Previously online nick is still online
                    buddy.setPresence(PresenceType.available);
                }
            }
        }
        else if (inCommand instanceof UnAwayReply) {
            getSession().setPresence(PresenceType.available);
        }
        else if (inCommand instanceof NowAwayReply) {
            getSession().setPresence(PresenceType.away);
        }
        else if (inCommand instanceof AwayReply) {
            AwayReply ar = (AwayReply)inCommand;
            getSession().getTransport().sendMessage(
                    getSession().getJID(),
                    getSession().getTransport().convertIDToJID(ar.getNick()),
                    LocaleUtils.getLocalizedString("gateway.irc.autoreply", "kraken")+" "+ar.getMessage()
            );

        }
    }

}
