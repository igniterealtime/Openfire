/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.protocol.xmpp;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.Message;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.Logger;

import org.jitsi.protocol.xmpp.*;
import org.jitsi.util.*;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smackx.*;
import org.jivesoftware.smackx.muc.*;
import org.jivesoftware.smackx.packet.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Stripped implementation of <tt>ChatRoom</tt> using Smack library.
 *
 * @author Pawel Domas
 */
public class ChatRoomImpl
    extends AbstractChatRoom
{
    /**
     * The logger used by this class.
     */
    private final static Logger logger = Logger.getLogger(ChatRoomImpl.class);

    /**
     * Parent MUC operation set.
     */
    private final OperationSetMultiUserChatImpl opSet;

    /**
     * Chat room name.
     */
    private final String roomName;

    /**
     * Smack multi user chat backend instance.
     */
    private MultiUserChat muc;

    /**
     * Our nickname.
     */
    private String myNickName;

    /**
     * Member presence listeners.
     */
    private CopyOnWriteArrayList<ChatRoomMemberPresenceListener> listeners
        = new CopyOnWriteArrayList<ChatRoomMemberPresenceListener>();

    /**
     * Local user role listeners.
     */
    private CopyOnWriteArrayList<ChatRoomLocalUserRoleListener>
        localUserRoleListeners
            = new CopyOnWriteArrayList<ChatRoomLocalUserRoleListener>();

    /**
     * Nickname to member impl class map.
     */
    private final Map<String, ChatMemberImpl> members
        = new HashMap<String, ChatMemberImpl>();

    /**
     * Local user role.
     */
    private ChatRoomMemberRole role;

    /**
     * Stores our last MUC presence packet for future update.
     */
    private Presence lastPresenceSent;

    /**
     * Creates new instance of <tt>ChatRoomImpl</tt>.
     *
     * @param parentChatOperationSet parent multi user chat operation set.
     * @param roomName the name of the chat room that will be handled by
     *                 new <tt>ChatRoomImpl</tt>instance.
     */
    public ChatRoomImpl(OperationSetMultiUserChatImpl parentChatOperationSet,
                        String roomName)
    {
        this.opSet = parentChatOperationSet;
        this.roomName = roomName;

        muc = new MultiUserChat(
                parentChatOperationSet.getConnection(), roomName);

        muc.addParticipantStatusListener(new MemberListener());
        muc.addParticipantListener(new ParticipantListener());
    }

    @Override
    public String getName()
    {
        return roomName;
    }

    @Override
    public String getIdentifier()
    {
        return null;
    }

    @Override
    public void join()
        throws OperationFailedException
    {
		logger.info("before join as");
        joinAs(getParentProvider().getAccountID().getAccountDisplayName());
		logger.info("after join as");
    }

    @Override
    public void join(byte[] password)
        throws OperationFailedException
    {
        join();
    }

    @Override
    public void joinAs(String nickname)
        throws OperationFailedException
    {
        try
        {
            muc.addPresenceInterceptor(new PacketInterceptor()
            {
                @Override
                public void interceptPacket(Packet packet)
                {
                    if (packet instanceof Presence)
                    {
                        lastPresenceSent = (Presence) packet;
                    }
                }
            });

			logger.info("joinAs " + nickname);

			try {
            	muc.join(nickname);
			} catch (Exception e) {
            	muc.create(nickname);		// BAO
			}
            this.myNickName = nickname;

            // Make the room non-anonymous, so that others can
            // recognize focus JID
            Form config = muc.getConfigurationForm();
            /*Iterator<FormField> fields = config.getFields();
            while (fields.hasNext())
            {
                FormField field = fields.next();
                logger.info("FORM: " + field.toXML());
            }*/
            Form answer = config.createAnswerForm();
            FormField whois = new FormField("muc#roomconfig_whois");
            whois.addValue("anyone");
            answer.addField(whois);

            muc.sendConfigurationForm(answer);
        }
        catch (XMPPException e)
        {
            throw new OperationFailedException(
                "Failed to join the room",
                OperationFailedException.GENERAL_ERROR, e);
        }
    }

    @Override
    public void joinAs(String nickname, byte[] password)
        throws OperationFailedException
    {
        joinAs(nickname);
    }

    @Override
    public boolean isJoined()
    {
        return muc.isJoined();
    }

    @Override
    public void leave()
    {
        muc.leave();
    }

    @Override
    public String getSubject()
    {
        return muc.getSubject();
    }

    @Override
    public void setSubject(String subject)
        throws OperationFailedException
    {

    }

    @Override
    public String getUserNickname()
    {
        return myNickName;
    }

    @Override
    public ChatRoomMemberRole getUserRole()
    {
        if(this.role == null)
        {
            Occupant o = muc.getOccupant(
                muc.getRoom() + "/" + muc.getNickname());

            if(o == null)
                return ChatRoomMemberRole.GUEST;
            else
                this.role = ChatRoomJabberImpl.smackRoleToScRole(
                    o.getRole(), o.getAffiliation());
        }

        return this.role;
    }

    @Override
    public void setLocalUserRole(ChatRoomMemberRole role)
        throws OperationFailedException
    {

    }

    /**
     * Creates the corresponding ChatRoomLocalUserRoleChangeEvent and notifies
     * all <tt>ChatRoomLocalUserRoleListener</tt>s that local user's role has
     * been changed in this <tt>ChatRoom</tt>.
     *
     * @param previousRole the previous role that local user had
     * @param newRole the new role the local user gets
     * @param isInitial if <tt>true</tt> this is initial role set.
     */
    private void fireLocalUserRoleEvent(ChatRoomMemberRole previousRole,
                                        ChatRoomMemberRole newRole,
                                        boolean isInitial)
    {
        ChatRoomLocalUserRoleChangeEvent evt
            = new ChatRoomLocalUserRoleChangeEvent(
            this, previousRole, newRole, isInitial);

        if (logger.isTraceEnabled())
            logger.trace("Will dispatch the following ChatRoom event: " + evt);

        for (ChatRoomLocalUserRoleListener listener : localUserRoleListeners)
            listener.localUserRoleChanged(evt);
    }

    /**
     * Sets the new rolefor the local user in the context of this chatroom.
     *
     * @param role the new role to be set for the local user
     * @param isInitial if <tt>true</tt> this is initial role set.
     */
    public void setLocalUserRole(ChatRoomMemberRole role, boolean isInitial)
    {
        fireLocalUserRoleEvent(getUserRole(), role, isInitial);
        this.role = role;
    }

    @Override
    public void setUserNickname(String nickname)
        throws OperationFailedException
    {

    }

    @Override
    public void addMemberPresenceListener(
        ChatRoomMemberPresenceListener listener)
    {
        listeners.add(listener);
    }

    @Override
    public void removeMemberPresenceListener(
        ChatRoomMemberPresenceListener listener)
    {
        listeners.remove(listener);
    }

    @Override
    public void addLocalUserRoleListener(ChatRoomLocalUserRoleListener listener)
    {
        localUserRoleListeners.add(listener);
    }

    @Override
    public void removelocalUserRoleListener(
        ChatRoomLocalUserRoleListener listener)
    {
        localUserRoleListeners.remove(listener);
    }

    @Override
    public void addMemberRoleListener(ChatRoomMemberRoleListener listener)
    {

    }

    @Override
    public void removeMemberRoleListener(ChatRoomMemberRoleListener listener)
    {

    }

    @Override
    public void addPropertyChangeListener(
        ChatRoomPropertyChangeListener listener)
    {

    }

    @Override
    public void removePropertyChangeListener(
        ChatRoomPropertyChangeListener listener)
    {

    }

    @Override
    public void addMemberPropertyChangeListener(
        ChatRoomMemberPropertyChangeListener listener)
    {

    }

    @Override
    public void removeMemberPropertyChangeListener(
        ChatRoomMemberPropertyChangeListener listener)
    {

    }

    @Override
    public void invite(String userAddress, String reason)
    {

    }

    @Override
    public List<ChatRoomMember> getMembers()
    {
        return new ArrayList<ChatRoomMember>(members.values());
    }

    @Override
    public int getMembersCount()
    {
        return muc.getOccupantsCount();
    }

    @Override
    public void addMessageListener(ChatRoomMessageListener listener)
    {

    }

    @Override
    public void removeMessageListener(ChatRoomMessageListener listener)
    {

    }

    @Override
    public Message createMessage(byte[] content, String contentType,
                                 String contentEncoding, String subject)
    {
        return null;
    }

    @Override
    public Message createMessage(String messageText)
    {
        return null;
    }

    @Override
    public void sendMessage(Message message)
        throws OperationFailedException
    {

    }

    @Override
    public ProtocolProviderService getParentProvider()
    {
        return opSet.getProtocolProvider();
    }

    @Override
    public Iterator<ChatRoomMember> getBanList()
        throws OperationFailedException
    {
        return null;
    }

    @Override
    public void banParticipant(ChatRoomMember chatRoomMember, String reason)
        throws OperationFailedException
    {

    }

    @Override
    public void kickParticipant(ChatRoomMember chatRoomMember, String reason)
        throws OperationFailedException
    {

    }

    @Override
    public ChatRoomConfigurationForm getConfigurationForm()
        throws OperationFailedException
    {
        return null;
    }

    @Override
    public boolean isSystem()
    {
        return false;
    }

    @Override
    public boolean isPersistent()
    {
        return false;
    }

    @Override
    public Contact getPrivateContactByNickname(String name)
    {
        return null;
    }

    @Override
    public void grantAdmin(String address)
    {
        try
        {
            muc.grantAdmin(address);
        }
        catch (XMPPException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void grantMembership(String address)
    {
        try
        {
            muc.grantMembership(address);
        }
        catch (XMPPException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void grantModerator(String nickname)
    {
        try
        {
            muc.grantModerator(nickname);
        }
        catch (XMPPException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void grantOwnership(String address)
    {
        try
        {
            muc.grantOwnership(address);
        }
        catch (XMPPException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void grantVoice(String nickname)
    {

    }

    @Override
    public void revokeAdmin(String address)
    {

    }

    @Override
    public void revokeMembership(String address)
    {

    }

    @Override
    public void revokeModerator(String nickname)
    {

    }

    @Override
    public void revokeOwnership(String address)
    {

    }

    @Override
    public void revokeVoice(String nickname)
    {

    }

    @Override
    public ConferenceDescription publishConference(ConferenceDescription cd,
                                                   String name)
    {
        return null;
    }

    @Override
    public void updatePrivateContactPresenceStatus(String nickname)
    {

    }

    @Override
    public void updatePrivateContactPresenceStatus(Contact contact)
    {

    }

    @Override
    public boolean destroy(String reason, String alternateAddress)
    {
        try
        {
            muc.destroy(reason, alternateAddress);
        }
        catch (XMPPException e)
        {
            //FIXME: should not be runtime, but OperationFailed and included in
            // interface signature(see also other methods catching XMPPException
            // in this class)
            throw new RuntimeException(e);
        }
        return false;
    }

    @Override
    public List<String> getMembersWhiteList()
    {
        return null;
    }

    @Override
    public void setMembersWhiteList(List<String> members)
    {

    }

    private void notifyParticipantJoined(ChatMemberImpl member)
    {
        ChatRoomMemberPresenceChangeEvent event
            = new ChatRoomMemberPresenceChangeEvent(
                    this, member,
                    ChatRoomMemberPresenceChangeEvent.MEMBER_JOINED, null);

        for (ChatRoomMemberPresenceListener l : listeners)
        {
            l.memberPresenceChanged(event);
        }
    }

    private void notifyParticipantLeft(ChatMemberImpl member)
    {
        ChatRoomMemberPresenceChangeEvent event
            = new ChatRoomMemberPresenceChangeEvent(
                    this, member,
                    ChatRoomMemberPresenceChangeEvent.MEMBER_LEFT, null);

        for (ChatRoomMemberPresenceListener l : listeners)
        {
            l.memberPresenceChanged(event);
        }
    }

    private void notifyParticipantKicked(ChatMemberImpl member)
    {
        ChatRoomMemberPresenceChangeEvent event
            = new ChatRoomMemberPresenceChangeEvent(
                    this, member,
                    ChatRoomMemberPresenceChangeEvent.MEMBER_KICKED, null);

        for (ChatRoomMemberPresenceListener l : listeners)
        {
            l.memberPresenceChanged(event);
        }
    }

    public Occupant getOccupant(ChatMemberImpl chatMemeber)
    {
        return muc.getOccupant(chatMemeber.getContactAddress());
    }

    /**
     * Returns the MUCUser packet extension included in the packet or
     * <tt>null</tt> if none.
     *
     * @param packet the packet that may include the MUCUser extension.
     * @return the MUCUser found in the packet.
     */
    private MUCUser getMUCUserExtension(Packet packet)
    {
        if (packet != null)
        {
            // Get the MUC User extension
            return (MUCUser) packet.getExtension(
                "x", "http://jabber.org/protocol/muc#user");
        }
        return null;
    }

    public void sendPresenceExtension(PacketExtension extension)
    {
        if (lastPresenceSent == null)
        {
            logger.error("No presence packet obtained yet");
            return;
        }

        XmppProtocolProvider xmppProtocolProvider
            = (XmppProtocolProvider) getParentProvider();

        // Remove old
        PacketExtension old
            = lastPresenceSent.getExtension(
                    extension.getElementName(), extension.getNamespace());
        if (old != null)
        {
            lastPresenceSent.removeExtension(old);
        }

        // Add new
        lastPresenceSent.addExtension(extension);

        XmppConnection connection = xmppProtocolProvider.getConnectionAdapter();
        if (connection == null)
        {
            logger.error("Failed to send presence extension - no connection");
            return;
        }

        connection.sendPacket(lastPresenceSent);
    }

    class MemberListener
        implements ParticipantStatusListener
    {
        @Override
        public void joined(String participant)
        {
            ChatMemberImpl member;

            synchronized (members)
            {
                member = addMember(participant);
            }

            if (member != null)
                notifyParticipantJoined(member);
        }

        private ChatMemberImpl addMember(String participant)
        {
            if (members.containsKey(participant))
            {
                logger.error(participant + " already in " + roomName);
                return null;
            }

            ChatMemberImpl newMember
                = new ChatMemberImpl(participant, ChatRoomImpl.this);

            members.put(participant, newMember);

            return newMember;
        }

        private ChatMemberImpl removeMember(String participant)
        {
            ChatMemberImpl removed = members.remove(participant);

            if (removed == null)
                logger.error(participant + " not in " + roomName);

            return removed;
        }

        @Override
        public void left(String participant)
        {
            ChatMemberImpl member;

            synchronized (members)
            {
                member = removeMember(participant);
            }

            if (member != null)
            {
                notifyParticipantLeft(member);
            }
        }

        @Override
        public void kicked(String participant, String s2, String s3)
        {
            if (logger.isTraceEnabled())
                logger.trace("Kicked: " + participant + ", " + s2 +", " + s3);

            ChatMemberImpl member = members.get(participant);
            if (member == null)
            {
                logger.error(
                    "Kicked participant does not exist: " + participant);
                return;
            }

            notifyParticipantKicked(member);
        }

        @Override
        public void voiceGranted(String s)
        {
            if (logger.isTraceEnabled())
                logger.trace("Voice granted: " + s);
        }

        @Override
        public void voiceRevoked(String s)
        {
            if (logger.isTraceEnabled())
                logger.trace("Voice revoked: " + s);
        }

        @Override
        public void banned(String s, String s2, String s3)
        {
            if (logger.isTraceEnabled())
                logger.trace("Banned: " + s + ", " + s2 + ", " + s3);
        }

        @Override
        public void membershipGranted(String s)
        {
            if (logger.isTraceEnabled())
                logger.trace("Membership granted: " + s);
        }

        @Override
        public void membershipRevoked(String s)
        {
            if (logger.isTraceEnabled())
                logger.trace("Membership revoked: " + s);
        }

        @Override
        public void moderatorGranted(String s)
        {
            if (logger.isTraceEnabled())
                logger.trace("Moderator granted: " + s);
        }

        @Override
        public void moderatorRevoked(String s)
        {
            if (logger.isTraceEnabled())
                logger.trace("Moderator revoked: " + s);
        }

        @Override
        public void ownershipGranted(String s)
        {
            if (logger.isTraceEnabled())
                logger.trace("Ownership granted: " + s);
        }

        @Override
        public void ownershipRevoked(String s)
        {
            if (logger.isTraceEnabled())
                logger.trace("Ownership revoked: " + s);
        }

        @Override
        public void adminGranted(String s)
        {
            if (logger.isTraceEnabled())
                logger.trace("Admin granted: " + s);
        }

        @Override
        public void adminRevoked(String s)
        {
            if (logger.isTraceEnabled())
                logger.trace("Admin revoked: " + s);
        }

        @Override
        public void nicknameChanged(String oldNickname, String newNickname)
        {
            logger.error("nicknameChanged - NOT IMPLEMENTED");
            /*synchronized (members)
            {
                removeMember(oldNickname);

                addMember(newNickname);
            }*/
        }
    }

    class ParticipantListener
        implements PacketListener
    {

        /**
         * Processes an incoming presence packet.
         * @param packet the incoming packet.
         */
        @Override
        public void processPacket(Packet packet)
        {
            if (packet == null
                || !(packet instanceof Presence)
                || packet.getError() != null)
            {
                logger.warn("Unable to handle packet: " + packet);
                return;
            }

            Presence presence = (Presence) packet;
            String ourOccupantJid = muc.getRoom() + "/" + muc.getNickname();

            if (logger.isDebugEnabled())
            {
                logger.debug("Presence received " + presence.toXML());
            }

            if (ourOccupantJid.equals(presence.getFrom()))
                processOwnPresence(presence);
            else
                processOtherPresence(presence);
        }

        /**
         * Processes a <tt>Presence</tt> packet addressed to our own occupant
         * JID.
         * @param presence the packet to process.
         */
        private void processOwnPresence(Presence presence)
        {
            MUCUser mucUser = getMUCUserExtension(presence);

            if (mucUser != null)
            {
                String affiliation = mucUser.getItem().getAffiliation();
                String role = mucUser.getItem().getRole();

                // this is the presence for our member initial role and
                // affiliation, as smack do not fire any initial
                // events lets check it and fire events
                ChatRoomMemberRole jitsiRole =
                    ChatRoomJabberImpl.smackRoleToScRole(role, affiliation);

                /*if(jitsiRole == ChatRoomMemberRole.MODERATOR
                    || jitsiRole == ChatRoomMemberRole.OWNER
                    || jitsiRole == ChatRoomMemberRole.ADMINISTRATOR)
                {*/
                setLocalUserRole(jitsiRole, true);
                //}

                /*if(!presence.isAvailable()
                    && "none".equalsIgnoreCase(affiliation)
                    && "none".equalsIgnoreCase(role))
                {
                    MUCUser.Destroy destroy = mucUser.getDestroy();
                    if(destroy == null)
                    {
                        // the room is unavailable to us, there is no
                        // message we will just leave
                        leave();
                    }
                    else
                    {
                        leave(destroy.getReason(), destroy.getJid());
                    }
                }*/
            }
        }

        /**
         * Process a <tt>Presence</tt> packet sent by one of the other room
         * occupants.
         */
        private void processOtherPresence(Presence presence)
        {
            MUCUser mucUser
                = (MUCUser) presence.getExtension(
                    "x", "http://jabber.org/protocol/muc#user");

            ChatMemberImpl member = members.get(presence.getFrom());
            if (member == null)
            {
                logger.warn(
                    "Received presence for non-existing member: "
                        + presence.toXML());
                return;
            }

            String jid = mucUser.getItem().getJid();

            if (StringUtils.isNullOrEmpty(member.getJabberID()))
            {
                logger.info(
                    "JID: " + jid + " received for: "
                        + member.getContactAddress());

                member.setJabberID(mucUser.getItem().getJid());
            }
            else if(!jid.equals(member.getJabberID()))
            {
                logger.warn(
                    "New jid received in presence: " + presence.toXML());
            }
        }
    }
}
