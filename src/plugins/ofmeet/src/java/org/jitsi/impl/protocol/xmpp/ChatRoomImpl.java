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

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smackx.*;
import org.jivesoftware.smackx.muc.*;
import org.jivesoftware.smackx.packet.*;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.MUCRoom;

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
     * Openfire MUCRoom that is hosting Jitsi Meet conference.		BAO
     */
    private MUCRoom room = null;

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
     * HACK:
     * We need to have participant presence received, before firing
     * participant "joined" event to know MUC participant real JID from the
     * start. However Smack seems to be unpredictable(or XMPP server - not
     * sure) on the order of "member joined" and "presence packet" events.
     * So if "member joined" is fired before presence then we cache
     * participant in {@link #earlyParticipant}. If opposite order takes
     * place that is presence is received before "member joined" event
     * we cache Presence in <tt>onJoinPresence</tt>. {@link
     * ChatRoomMemberPresenceChangeEvent#MEMBER_JOINED} is fired when we have
     * first Presence packet and Smack "member joined" event has been fired.
     */
    private final Map<String, Presence> onJoinPresence
        = new HashMap<String,Presence>();

    private final List<String> earlyParticipant
        = new ArrayList<String>();

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

    private void joinAs(String nickname, String password) throws OperationFailedException
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

			String roomPassword = null;

			if (room != null &&  room.isPasswordProtected())
			{
				roomPassword = room.getPassword();
			}

			logger.info("joinAs " + nickname + " " + password + " " + roomPassword);

			try {
				if (password == null)
				{
					if (roomPassword != null)
            			muc.join(nickname, roomPassword);
            		else
            			muc.join(nickname);
				}
            	else
            		muc.join(nickname, password);

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
            // Room non-anonymous
            FormField whois = new FormField("muc#roomconfig_whois");
            whois.addValue("anyone");
            answer.addField(whois);
            // Room moderated
            //FormField roomModerated
            //    = new FormField("muc#roomconfig_moderatedroom");
            //roomModerated.addValue("true");
            //answer.addField(roomModerated);
            // Only participants can send private messages
            //FormField onlyParticipantsPm
            //        = new FormField("muc#roomconfig_allowpm");
            //onlyParticipantsPm.addValue("participants");
            //answer.addField(onlyParticipantsPm);
            // Presence broadcast
            //FormField presenceBroadcast
            //        = new FormField("muc#roomconfig_presencebroadcast");
            //presenceBroadcast.addValue("participant");
            //answer.addField(presenceBroadcast);
            // Get member list
            //FormField getMemberList
            //        = new FormField("muc#roomconfig_getmemberlist");
            //getMemberList.addValue("participant");
            //answer.addField(getMemberList);
            // Public logging
            //FormField publicLogging
            //        = new FormField("muc#roomconfig_enablelogging");
            //publicLogging.addValue("false");
            //answer.addField(publicLogging);

            muc.sendConfigurationForm(answer);
        }
        catch (XMPPException e)
        {
            throw new OperationFailedException(
                "Failed to join the room",
                OperationFailedException.GENERAL_ERROR, e);
        }
    }


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

		String roomId = roomName;
		int pos = roomId.indexOf("@");
		if (pos > -1) roomId = roomId.substring(0, pos);
        this.room = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService("conference").getChatRoom(roomId);
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
    public void join(byte[] password) throws OperationFailedException
    {
		try {
			String pass = new String(password, "UTF-8");
        	joinAs(getParentProvider().getAccountID().getAccountDisplayName(), pass);

		} catch (Exception e) {

		}
    }

    public void join(String password)
    {
		try {
        	joinAs(getParentProvider().getAccountID().getAccountDisplayName(), password);

		} catch (Exception e) {

		}
    }

    @Override
    public void joinAs(String nickname, byte[] password) throws OperationFailedException
    {
		try {
			String pass = new String(password, "UTF-8");
        	joinAs(nickname, pass);

		} catch (Exception e) {

		}
    }

    @Override
    public void joinAs(String nickname) throws OperationFailedException
    {
        joinAs(nickname, (String)null);
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

    /**
     * Resets cached role instance so that it will be refreshed when {@link
     * #getUserRole()} is called.
     */
    private void resetCachedUserRole()
    {
        role = null;
    }

    /**
     * Resets cached role instance for given participant.
     * @param participant full mucJID of the participant for whom we want to
     *                    reset cached role instance.
     */
    private void resetRoleForParticipant(String participant)
    {
        if (participant.endsWith("/" + myNickName))
        {
            resetCachedUserRole();
        }
        else
        {
            ChatMemberImpl member = members.get(participant);
            if (member != null)
            {
                member.resetCachedRole();
            }
            else
            {
                logger.error(
                    "Role reset for: " + participant + " who does not exist");
            }
        }
    }

    @Override
    public void setLocalUserRole(ChatRoomMemberRole role)
        throws OperationFailedException
    {
        // Method not used but log error just in case to spare debugging
        logger.error("setLocalUserRole not implemented");
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
     * Sets the new role for the local user in the context of this chat room.
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
        logger.info("Grant owner to " + address);

        // Have to construct the IQ manually as Smack version used here seems
        // to be using wrong namespace(muc#owner instead of muc#admin)
        // which does not work with the Prosody.
        MUCAdmin admin = new MUCAdmin();
        admin.setType(IQ.Type.SET);
        admin.setTo(roomName);

        MUCAdmin.Item item = new MUCAdmin.Item("owner", null);
        item.setJid(address);
        admin.addItem(item);

        XmppProtocolProvider provider
                = (XmppProtocolProvider) getParentProvider();
        XmppConnection connection
                = provider.getConnectionAdapter();

        IQ reply = (IQ) connection.sendPacketAndGetReply(admin);
        if (reply.getType() != IQ.Type.RESULT)
        {
            // FIXME: we should have checked exceptions for all operations in
            // ChatRoom interface which are expected to fail.
            // OperationFailedException maybe ?
            throw new RuntimeException(
                    "Failed to grant owner: " + reply.getError());
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

    private ChatMemberImpl addMember(String participant)
    {
        ChatMemberImpl newMember;

        synchronized (members)
        {
            if (members.containsKey(participant))
            {
                logger.error(participant + " already in " + roomName);
                return null;
            }

            newMember = new ChatMemberImpl(participant, ChatRoomImpl.this);

            members.put(participant, newMember);
        }

        return newMember;
    }

    class MemberListener
        implements ParticipantStatusListener
    {
        @Override
        public void joined(String participant)
        {
            //logger.info(Thread.currentThread()+"JOINED ROOM: "+participant);

            Presence peerPresence = onJoinPresence.get(participant);
            if (peerPresence == null)
            {
                earlyParticipant.add(participant);
                return;
            }

            onJoinPresence.remove(participant);

            ChatMemberImpl member = addMember(participant);
            if (member != null)
            {
                member.processPresence(peerPresence);
                notifyParticipantJoined(member);
            }
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

            ChatMemberImpl member;

            synchronized (members)
            {
                member = removeMember(participant);
            }

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

            // We do not fire events - not required for now
            resetRoleForParticipant(s);
        }

        @Override
        public void voiceRevoked(String s)
        {
            if (logger.isTraceEnabled())
                logger.trace("Voice revoked: " + s);

            // We do not fire events - not required for now
            resetRoleForParticipant(s);
        }

        @Override
        public void banned(String s, String s2, String s3)
        {
            if (logger.isTraceEnabled())
                logger.trace("Banned: " + s + ", " + s2 + ", " + s3);

            // We do not fire events - not required for now
            resetRoleForParticipant(s);
        }

        @Override
        public void membershipGranted(String s)
        {
            if (logger.isTraceEnabled())
                logger.trace("Membership granted: " + s);

            // We do not fire events - not required for now
            resetRoleForParticipant(s);
        }

        @Override
        public void membershipRevoked(String s)
        {
            if (logger.isTraceEnabled())
                logger.trace("Membership revoked: " + s);

            // We do not fire events - not required for now
            resetRoleForParticipant(s);
        }

        @Override
        public void moderatorGranted(String s)
        {
            if (logger.isTraceEnabled())
                logger.trace("Moderator granted: " + s);

            // We do not fire events - not required for now
            resetRoleForParticipant(s);
        }

        @Override
        public void moderatorRevoked(String s)
        {
            if (logger.isTraceEnabled())
                logger.trace("Moderator revoked: " + s);

            // We do not fire events - not required for now
            resetRoleForParticipant(s);
        }

        @Override
        public void ownershipGranted(String s)
        {
            if (logger.isTraceEnabled())
                logger.trace("Ownership granted: " + s);

            // We do not fire events - not required for now
            resetRoleForParticipant(s);
        }

        @Override
        public void ownershipRevoked(String s)
        {
            if (logger.isTraceEnabled())
                logger.trace("Ownership revoked: " + s);

            // We do not fire events - not required for now
            resetRoleForParticipant(s);
        }

        @Override
        public void adminGranted(String s)
        {
            if (logger.isTraceEnabled())
                logger.trace("Admin granted: " + s);

            // We do not fire events - not required for now
            resetRoleForParticipant(s);
        }

        @Override
        public void adminRevoked(String s)
        {
            if (logger.isTraceEnabled())
                logger.trace("Admin revoked: " + s);

            // We do not fire events - not required for now
            resetRoleForParticipant(s);
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
            String participant = presence.getFrom();

            ChatMemberImpl member = members.get(presence.getFrom());
            if (member == null)
            {
                logger.warn(
                    "Received presence for non-existing member: "
                        + presence.toXML());
                if (earlyParticipant.contains(participant))
                {
                    earlyParticipant.remove(participant);

                    member = addMember(participant);
                    if (member != null)
                    {
                        member.processPresence(presence);
                        notifyParticipantJoined(member);
                    }
                }
                else
                {
                    onJoinPresence.put(presence.getFrom(), presence);
                }
                return;
            }

            member.processPresence(presence);
        }
    }
}
