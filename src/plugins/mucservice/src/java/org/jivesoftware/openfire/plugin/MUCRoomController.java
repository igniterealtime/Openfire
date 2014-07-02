package org.jivesoftware.openfire.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.entity.MUCChannelType;
import org.jivesoftware.openfire.entity.MUCRoomEntities;
import org.jivesoftware.openfire.entity.MUCRoomEntity;
import org.jivesoftware.openfire.entity.ParticipantEntities;
import org.jivesoftware.openfire.entity.ParticipantEntity;
import org.jivesoftware.openfire.exception.MUCServiceException;
import org.jivesoftware.openfire.muc.ConflictException;
import org.jivesoftware.openfire.muc.ForbiddenException;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.NotAllowedException;
import org.xmpp.packet.JID;

// TODO: Auto-generated Javadoc
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
	 *             the MUC service exception
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
	 *             the MUC service exception
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
	 *            the MUC room entity
	 * @throws MUCServiceException
	 *             the mUC service exception
	 */
	public void createChatRoom(String serviceName, String owner, MUCRoomEntity mucRoomEntity)
			throws MUCServiceException {
		try {
			createRoom(mucRoomEntity, serviceName, owner);
		} catch (NotAllowedException e) {
			throw new MUCServiceException("Could not create the channel", mucRoomEntity.getRoomName(),
					"NotAllowedException");
		} catch (ForbiddenException e) {
			throw new MUCServiceException("Could not create the channel", mucRoomEntity.getRoomName(),
					"ForbiddenException");
		} catch (ConflictException e) {
			throw new MUCServiceException("Could not create the channel", mucRoomEntity.getRoomName(),
					"ConflictException");
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
	 *            the MUC room entity
	 * @throws MUCServiceException
	 *             the mUC service exception
	 */
	public void updateChatRoom(String roomName, String serviceName, String owner, MUCRoomEntity mucRoomEntity)
			throws MUCServiceException {
		try {
			// If the room name is different throw exception
			if (!roomName.equals(mucRoomEntity.getRoomName())) {
				throw new MUCServiceException(
						"Could not update the channel. The room name is different to the entity room name.", roomName,
						"IllegalArgumentException");
			}

			// Set modification date
			mucRoomEntity.setModificationDate(new Date());

			createRoom(mucRoomEntity, serviceName, owner);
		} catch (NotAllowedException e) {
			throw new MUCServiceException("Could not update the channel", roomName, "NotAllowedException");
		} catch (ForbiddenException e) {
			throw new MUCServiceException("Could not update the channel", roomName, "ForbiddenException");
		} catch (ConflictException e) {
			throw new MUCServiceException("Could not update the channel", roomName, "ConflictException");
		}
	}

	/**
	 * Creates the room.
	 *
	 * @param mucRoomEntity
	 *            the MUC room entity
	 * @param serviceName
	 *            the service name
	 * @param owner
	 *            the owner
	 * @throws NotAllowedException
	 *             the not allowed exception
	 * @throws ForbiddenException
	 *             the forbidden exception
	 * @throws ConflictException
	 *             the conflict exception
	 */
	public void createRoom(MUCRoomEntity mucRoomEntity, String serviceName, String owner) throws NotAllowedException,
			ForbiddenException, ConflictException {
		MUCRoom room = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(serviceName)
				.getChatRoom(mucRoomEntity.getRoomName(), XMPPServer.getInstance().createJID(owner, null));

		// Set values
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
		room.setModificationDate(mucRoomEntity.getModificationDate());
		room.setLogEnabled(mucRoomEntity.isLogEnabled());
		room.setLoginRestrictedToNickname(mucRoomEntity.isLoginRestrictedToNickname());
		room.setMaxUsers(mucRoomEntity.getMaxUsers());
		room.setMembersOnly(mucRoomEntity.isMembersOnly());
		room.setModerated(mucRoomEntity.isModerated());

		room.setRolesToBroadcastPresence(mucRoomEntity.getBroadcastPresenceRoles());

		// Set all roles
		room.addAdmins(convertStringsToJIDs(mucRoomEntity.getAdmins()), room.getRole());
		room.addOwners(convertStringsToJIDs(mucRoomEntity.getOwners()), room.getRole());

		for (String memberJid : mucRoomEntity.getMembers()) {
			room.addMember(new JID(memberJid), null, room.getRole());
		}

		for (String outcastJid : mucRoomEntity.getOutcasts()) {
			room.addOutcast(new JID(outcastJid), null, room.getRole());
		}

		// Set creation date
		if (mucRoomEntity.getCreationDate() != null) {
			room.setCreationDate(mucRoomEntity.getCreationDate());
		} else {
			room.setCreationDate(new Date());
		}
	}

	/**
	 * Gets the room participants.
	 *
	 * @param roomName
	 *            the room name
	 * @param serviceName
	 *            the service name
	 * @return the room participants
	 */
	public ParticipantEntities getRoomParticipants(String roomName, String serviceName) {
		ParticipantEntities participantEntities = new ParticipantEntities();
		List<ParticipantEntity> participants = new ArrayList<ParticipantEntity>();

		Collection<MUCRole> serverParticipants = XMPPServer.getInstance().getMultiUserChatManager()
				.getMultiUserChatService(serviceName).getChatRoom(roomName).getParticipants();

		for (MUCRole role : serverParticipants) {
			ParticipantEntity participantEntity = new ParticipantEntity();
			participantEntity.setJid(role.getRoleAddress().toFullJID());
			participantEntity.setRole(role.getRole().name());
			participantEntity.setAffiliation(role.getAffiliation().name());

			participants.add(participantEntity);
		}

		participantEntities.setParticipants(participants);
		return participantEntities;
	}

	/**
	 * Convert to MUC room entity.
	 * 
	 * @param room
	 *            the room
	 * @return the MUC room entity
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

		mucRoomEntity.setOwners(convertJIDsToStringList(room.getOwners()));
		mucRoomEntity.setAdmins(convertJIDsToStringList(room.getAdmins()));
		mucRoomEntity.setMembers(convertJIDsToStringList(room.getMembers()));
		mucRoomEntity.setOutcasts(convertJIDsToStringList(room.getOutcasts()));

		mucRoomEntity.setBroadcastPresenceRoles(room.getRolesToBroadcastPresence());

		mucRoomEntity.setCreationDate(room.getCreationDate());
		mucRoomEntity.setModificationDate(room.getModificationDate());

		return mucRoomEntity;
	}

	/**
	 * Convert jids to string list.
	 *
	 * @param jids
	 *            the jids
	 * @return the array list< string>
	 */
	private ArrayList<String> convertJIDsToStringList(Collection<JID> jids) {
		ArrayList<String> result = new ArrayList<String>();

		for (JID jid : jids) {
			result.add(jid.toBareJID());
		}
		return result;
	}

	/**
	 * Convert strings to jids.
	 *
	 * @param jids
	 *            the jids
	 * @return the list<jid>
	 */
	private List<JID> convertStringsToJIDs(List<String> jids) {
		List<JID> result = new ArrayList<JID>();

		for (String jidString : jids) {
			result.add(new JID(jidString));
		}
		return result;
	}
}