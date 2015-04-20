/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.protocol.xmpp;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.*;

import org.jivesoftware.smack.*;

import java.util.*;

/**
 * Multi user chat implementation stripped to the minimum required by the focus
 * of Jitsi Meet conference. Uses Smack backend.
 *
 * @author Pawel Domas
 */
public class OperationSetMultiUserChatImpl
    extends AbstractOperationSetMultiUserChat
{
    /**
     * The logger.
     */
    private Logger logger = Logger.getLogger(OperationSetMultiUserChatImpl.class);

    /**
     * Parent protocol provider.
     */
    private final XmppProtocolProvider protocolProvider;

    /**
     * The map of active chat rooms mapped by their names.
     */
    private Map<String, ChatRoomImpl> rooms
            = new HashMap<String, ChatRoomImpl>();

    /**
     * Creates new instance of {@link OperationSetMultiUserChatImpl}.
     *
     * @param protocolProvider parent protocol provider service.
     */
    OperationSetMultiUserChatImpl(XmppProtocolProvider protocolProvider)
    {
        this.protocolProvider = protocolProvider;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getExistingChatRooms()
        throws OperationFailedException, OperationNotSupportedException
    {
        return new ArrayList<String>(rooms.keySet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ChatRoom> getCurrentlyJoinedChatRooms()
    {
        throw new RuntimeException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getCurrentlyJoinedChatRooms(
        ChatRoomMember chatRoomMember)
        throws OperationFailedException, OperationNotSupportedException
    {
        throw new RuntimeException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChatRoom createChatRoom(String roomName,
                                   Map<String, Object> roomProperties)
        throws OperationFailedException, OperationNotSupportedException
    {
        if (rooms.containsKey(roomName))
        {
            throw new OperationFailedException(
                "Room '" + roomName + "' exists",
                OperationFailedException.GENERAL_ERROR);
        }

        ChatRoomImpl newRoom = new ChatRoomImpl(this, roomName);

        rooms.put(roomName, newRoom);

        return newRoom;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChatRoom findRoom(String roomName)
        throws OperationFailedException, OperationNotSupportedException
    {
        ChatRoom room = rooms.get(roomName);
        if (room == null)
        {
            room = createChatRoom(roomName, null);
        }
        return room;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rejectInvitation(ChatRoomInvitation invitation,
                                 String rejectReason)
    {
        throw new RuntimeException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isMultiChatSupportedByContact(Contact contact)
    {
        throw new RuntimeException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isPrivateMessagingContact(String contactAddress)
    {
        throw new RuntimeException("Not implemented");
    }

    /**
     * Returns Smack connection object used by parent protocol provider service.
     */
    public Connection getConnection()
    {
        return protocolProvider.getConnection();
    }

    /**
     * Returns parent protocol provider service.
     */
    public XmppProtocolProvider getProtocolProvider()
    {
        return protocolProvider;
    }
}
