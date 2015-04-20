/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.protocol.xmpp;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.*;

import net.java.sip.communicator.service.protocol.globalstatus.*;
import org.jitsi.protocol.xmpp.*;
import org.jitsi.util.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smackx.muc.*;
import org.jivesoftware.smackx.packet.*;

/**
 * Stripped Smack implementation of {@link ChatRoomMember}.
 *
 * @author Pawel Domas
 */
public class ChatMemberImpl
    implements XmppChatMember
{
    /**
     * The logger.
     */
    private final static Logger logger = Logger.getLogger(ChatMemberImpl.class);

    /**
     * The MUC nickname used by this member.
     */
    private final String nickname;

    private final ChatRoomImpl chatRoom;

    /**
     * Full MUC address:
     * room_name@muc.server.net/nickname
     */
    private final String address;

    /**
     * Connection Jabber ID used to connect to the service. It is sent in
     * MUC presence as jid attribute of item element:
     *
     * <x xmlns='http://jabber.org/protocol/muc#user'>
     *    <item affiliation='none'
     *          jid='hag66@shakespeare.lit/pda'
     *          role='participant'/>
     * </x>
     *
     * In this example 'hag66@shakespeare.lit/pda' is the jabber ID.
     * Note that by default only moderators are allowed to see it for
     * all participants(see room configuration form and muc#roomconfig_whois
     * for more details).
     */
    private String jabberId;

    private ChatRoomMemberRole role;

    public ChatMemberImpl(String participant, ChatRoomImpl chatRoom)
    {
        this.address = participant;
        this.nickname = participant.substring(participant.lastIndexOf("/")+1);
        this.chatRoom = chatRoom;
    }

    @Override
    public ChatRoom getChatRoom()
    {
        return chatRoom;
    }

    @Override
    public ProtocolProviderService getProtocolProvider()
    {
        return chatRoom.getParentProvider();
    }

    @Override
    public String getContactAddress()
    {
        return address;
    }

    @Override
    public String getName()
    {
        return nickname;
    }

    @Override
    public byte[] getAvatar()
    {
        return new byte[0];
    }

    @Override
    public Contact getContact()
    {
        return null;
    }

    @Override
    public ChatRoomMemberRole getRole()
    {
        if(this.role == null)
        {
            Occupant o = chatRoom.getOccupant(this);

            if(o == null)
                return ChatRoomMemberRole.GUEST;
            else
                this.role = ChatRoomJabberImpl
                    .smackRoleToScRole(o.getRole(), o.getAffiliation());
        }
        return this.role;
    }

    /**
     * Reset cached user role so that it will be refreshed when {@link
     * #getRole()} is called.
     */
    void resetCachedRole()
    {
        this.role = null;
    }

    void processPresence(Presence presence)
    {
        MUCUser mucUser
            = (MUCUser) presence.getExtension(
                    "x", "http://jabber.org/protocol/muc#user");

        String jid = mucUser.getItem().getJid();

        if (StringUtils.isNullOrEmpty(jabberId))
        {
            logger.info(Thread.currentThread()+
                "JID: " + jid + " received for: " + getContactAddress());

            jabberId = mucUser.getItem().getJid();
        }
        else if(!jid.equals(jabberId))
        {
            logger.warn(
                "Different jid received in presence: " + presence.toXML());
        }
    }

    @Override
    public void setRole(ChatRoomMemberRole role)
    {
        throw new RuntimeException("Not implemented yet.");
    }

    @Override
    public PresenceStatus getPresenceStatus()
    {
        return GlobalStatusEnum.ONLINE;
    }

    @Override
    public String getJabberID()
    {
        return jabberId;
    }
}
