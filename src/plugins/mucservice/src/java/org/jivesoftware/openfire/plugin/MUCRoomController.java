package org.jivesoftware.openfire.plugin;

import java.util.ArrayList;
import java.util.List;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.entity.MUCChannelType;
import org.jivesoftware.openfire.entity.MUCRoomEntities;
import org.jivesoftware.openfire.entity.MUCRoomEntity;
import org.jivesoftware.openfire.exception.MUCServiceException;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.NotAllowedException;

/**
 * The Class MUCRoomController.
 */
public class MUCRoomController {

	/** The Constant INSTANCE. */
	public static final MUCRoomController INSTANCE = new MUCRoomController();

	/**
	 * Gets the single instance of MUCRoomController.
	 * 
	 * @return single instance of MUCRoomController
	 */
	public static MUCRoomController getInstance() {
		return INSTANCE;
	}

	/**
	 * Gets the chat rooms.
	 * 
	 * @param serviceName
	 *            the service name
	 * @param channelType
	 *            the channel type
	 * @param roomSearch
	 *            the room search
	 * @return the chat rooms
	 */
	public MUCRoomEntities getChatRooms(String serviceName, String channelType, String roomSearch) {
		List<MUCRoom> rooms = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(serviceName)
				.getChatRooms();

		List<MUCRoomEntity> mucRoomEntities = new ArrayList<MUCRoomEntity>();

		for (MUCRoom chatRoom : rooms) {
			if (roomSearch != null) {
				if (!chatRoom.getName().contains(roomSearch)) {
					continue;
				}
			}

			if (channelType.equals(MUCChannelType.ALL)) {
				mucRoomEntities.add(convertToMUCRoomEntity(chatRoom));
			} else if (channelType.equals(MUCChannelType.PUBLIC) && chatRoom.isPublicRoom()) {
				mucRoomEntities.add(convertToMUCRoomEntity(chatRoom));
			}
		}

		return new MUCRoomEntities(mucRoomEntities);
	}

	/**
	 * Gets the chat room.
	 * 
	 * @param roomName
	 *            the room name
	 * @param serviceName
	 *            the service name
	 * @return the chat room
	 * @throws MUCServiceException
	 *             the mUC service exception
	 */
	public MUCRoomEntity getChatRoom(String roomName, String serviceName) throws MUCServiceException {
		MUCRoom chatRoom = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(serviceName)
				.getChatRoom(roomName);

		if (chatRoom == null) {
			throw new MUCServiceException("Could not fetch the channel", roomName, "Chat room could be not found");
		}

		MUCRoomEntity mucRoomEntity = convertToMUCRoomEntity(chatRoom);
		return mucRoomEntity;
	}

	/**
	 * Delete chat room.
	 * 
	 * @param roomName
	 *            the room name
	 * @param serviceName
	 *            the service name
	 * @throws MUCServiceException
	 *             the mUC service exception
	 */
	public void deleteChatRoom(String roomName, String serviceName) throws MUCServiceException {
		MUCRoom chatRoom = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(serviceName)
				.getChatRoom(roomName);

		if (chatRoom != null) {
			chatRoom.destroyRoom(null, null);
		} else {
			throw new MUCServiceException("Could not remove the channel", roomName, "Chat room could be not found");
		}
	}

	/**
	 * Creates the chat room.
	 * 
	 * @param serviceName
	 *            the service name
	 * @param owner
	 *            the owner
	 * @param mucRoomEntity
	 *            the muc room entity
	 * @throws MUCServiceException
	 *             the mUC service exception
	 */
	public void createChatRoom(String serviceName, String owner, MUCRoomEntity mucRoomEntity) throws MUCServiceException {
		try {
			createRoom(mucRoomEntity, serviceName, owner);
		} catch (NotAllowedException e) {
			throw new MUCServiceException("Could not create the channel", mucRoomEntity.getRoomName(),
					"NotAllowedException");
		}
	}

	/**
	 * Update chat room.
	 * 
	 * @param roomName
	 *            the room name
	 * @param serviceName
	 *            the service name
	 * @param owner
	 *            the owner
	 * @param mucRoomEntity
	 *            the muc room entity
	 * @throws MUCServiceException
	 *             the mUC service exception
	 */
	public void updateChatRoom(String roomName, String serviceName, String owner, MUCRoomEntity mucRoomEntity)
			throws MUCServiceException {
		try {
			// If the roomname is different throw exception
			if (!roomName.equals(mucRoomEntity.getRoomName())) {
				throw new MUCServiceException("Could not update the channel", roomName,
						"The roomname is different to the entity room name");
			}
			createRoom(mucRoomEntity, serviceName, owner);
		} catch (NotAllowedException e) {
			throw new MUCServiceException("Could not update the channel", roomName, "NotAllowedException");
		}
	}

	/**
	 * Creates the room.
	 * 
	 * @param mucRoomEntity
	 *            the muc room entity
	 * @param serviceName
	 *            the service name
	 * @param owner
	 *            the owner
	 * @throws NotAllowedException
	 *             the not allowed exception
	 */
	public void createRoom(MUCRoomEntity mucRoomEntity, String serviceName, String owner) throws NotAllowedException {
		MUCRoom room = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(serviceName)
				.getChatRoom(mucRoomEntity.getRoomName(), XMPPServer.getInstance().createJID(owner, null));

		room.setNaturalLanguageName(mucRoomEntity.getNaturalName());
		room.setSubject(mucRoomEntity.getSubject());
		room.setDescription(mucRoomEntity.getDescription());
		room.setPassword(mucRoomEntity.getPassword());
		room.setPersistent(mucRoomEntity.isPersistent());
		room.setPublicRoom(mucRoomEntity.isPublicRoom());
		room.setRegistrationEnabled(mucRoomEntity.isRegistrationEnabled());
		room.setCanAnyoneDiscoverJID(mucRoomEntity.isCanAnyoneDiscoverJID());
		room.setCanOccupantsChangeSubject(mucRoomEntity.isCanOccupantsChangeSubject());
		room.setCanOccupantsInvite(mucRoomEntity.isCanOccupantsInvite());
		room.setChangeNickname(mucRoomEntity.isCanChangeNickname());
		room.setCreationDate(mucRoomEntity.getCreationDate());
		room.setModificationDate(mucRoomEntity.getModificationDate());
		room.setLogEnabled(mucRoomEntity.isLogEnabled());
		room.setLoginRestrictedToNickname(mucRoomEntity.isLoginRestrictedToNickname());
		room.setMaxUsers(mucRoomEntity.getMaxUsers());
		room.setMembersOnly(mucRoomEntity.isMembersOnly());
		room.setModerated(mucRoomEntity.isModerated());

		room.setRolesToBroadcastPresence(mucRoomEntity.getBroadcastPresenceRoles());
	}

	/**
	 * Convert to muc room entity.
	 * 
	 * @param room
	 *            the room
	 * @return the mUC room entity
	 */
	public MUCRoomEntity convertToMUCRoomEntity(MUCRoom room) {
		MUCRoomEntity mucRoomEntity = new MUCRoomEntity(room.getNaturalLanguageName(), room.getName(),
				room.getDescription());
		mucRoomEntity.setCanAnyoneDiscoverJID(room.canAnyoneDiscoverJID());
		mucRoomEntity.setCanChangeNickname(room.canChangeNickname());
		mucRoomEntity.setCanOccupantsChangeSubject(room.canOccupantsChangeSubject());
		mucRoomEntity.setCanOccupantsInvite(room.canOccupantsInvite());

		mucRoomEntity.setPublicRoom(room.isPublicRoom());
		mucRoomEntity.setPassword(room.getPassword());
		mucRoomEntity.setPersistent(room.isPersistent());
		mucRoomEntity.setRegistrationEnabled(room.isRegistrationEnabled());
		mucRoomEntity.setLogEnabled(room.isLogEnabled());
		mucRoomEntity.setLoginRestrictedToNickname(room.isLoginRestrictedToNickname());
		mucRoomEntity.setMaxUsers(room.getMaxUsers());
		mucRoomEntity.setMembersOnly(room.isMembersOnly());
		mucRoomEntity.setModerated(room.isModerated());

		mucRoomEntity.setCreationDate(room.getCreationDate());
		mucRoomEntity.setModificationDate(room.getModificationDate());

		mucRoomEntity.setBroadcastPresenceRoles(room.getRolesToBroadcastPresence());

		return mucRoomEntity;
	}

}