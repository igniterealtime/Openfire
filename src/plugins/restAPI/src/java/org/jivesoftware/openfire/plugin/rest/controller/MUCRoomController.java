package org.jivesoftware.openfire.plugin.rest.controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.ws.rs.core.Response;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.plugin.rest.entity.MUCChannelType;
import org.jivesoftware.openfire.plugin.rest.entity.MUCRoomEntities;
import org.jivesoftware.openfire.plugin.rest.entity.MUCRoomEntity;
import org.jivesoftware.openfire.plugin.rest.entity.ParticipantEntities;
import org.jivesoftware.openfire.plugin.rest.entity.ParticipantEntity;
import org.jivesoftware.openfire.plugin.rest.exceptions.ExceptionType;
import org.jivesoftware.openfire.plugin.rest.exceptions.ServiceException;
import org.jivesoftware.openfire.muc.ConflictException;
import org.jivesoftware.openfire.muc.ForbiddenException;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.NotAllowedException;
import org.jivesoftware.openfire.plugin.rest.utils.MUCRoomUtils;
import org.jivesoftware.openfire.plugin.rest.utils.UserUtils;
import org.jivesoftware.util.AlreadyExistsException;
import org.xmpp.packet.JID;

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
	 * @throws ServiceException
	 *             the service exception
	 */
	public MUCRoomEntity getChatRoom(String roomName, String serviceName) throws ServiceException {
		MUCRoom chatRoom = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(serviceName)
				.getChatRoom(roomName);

		if (chatRoom == null) {
			throw new ServiceException("Could not find the chat room", roomName, ExceptionType.ROOM_NOT_FOUND, Response.Status.NOT_FOUND);
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
	 * @throws ServiceException
	 *             the service exception
	 */
	public void deleteChatRoom(String roomName, String serviceName) throws ServiceException {
		MUCRoom chatRoom = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(serviceName)
				.getChatRoom(roomName.toLowerCase());

		if (chatRoom != null) {
			chatRoom.destroyRoom(null, null);
		} else {
			throw new ServiceException("Could not remove the channel", roomName, ExceptionType.ROOM_NOT_FOUND, Response.Status.NOT_FOUND);
		}
	}

	/**
	 * Creates the chat room.
	 *
	 * @param serviceName
	 *            the service name
	 * @param mucRoomEntity
	 *            the MUC room entity
	 * @throws ServiceException
	 *             the service exception
	 */
	public void createChatRoom(String serviceName, MUCRoomEntity mucRoomEntity) throws ServiceException {
		try {
			createRoom(mucRoomEntity, serviceName);
		} catch (NotAllowedException e) {
			throw new ServiceException("Could not create the channel", mucRoomEntity.getRoomName(),
					ExceptionType.NOT_ALLOWED, Response.Status.FORBIDDEN, e);
		} catch (ForbiddenException e) {
			throw new ServiceException("Could not create the channel", mucRoomEntity.getRoomName(),
					ExceptionType.NOT_ALLOWED, Response.Status.FORBIDDEN, e);
		} catch (ConflictException e) {
			throw new ServiceException("Could not create the channel", mucRoomEntity.getRoomName(),
					ExceptionType.NOT_ALLOWED, Response.Status.CONFLICT, e);
		} catch (AlreadyExistsException e) {
			throw new ServiceException("Could not create the channel", mucRoomEntity.getRoomName(),
					ExceptionType.ALREADY_EXISTS, Response.Status.CONFLICT, e);
		}
	}

	/**
	 * Update chat room.
	 *
	 * @param roomName
	 *            the room name
	 * @param serviceName
	 *            the service name
	 * @param mucRoomEntity
	 *            the MUC room entity
	 * @throws ServiceException
	 *             the service exception
	 */
	public void updateChatRoom(String roomName, String serviceName, MUCRoomEntity mucRoomEntity)
			throws ServiceException {
		try {
			// If the room name is different throw exception
			if (!roomName.equals(mucRoomEntity.getRoomName())) {
				throw new ServiceException(
						"Could not update the channel. The room name is different to the entity room name.", roomName,
						ExceptionType.ILLEGAL_ARGUMENT_EXCEPTION, Response.Status.BAD_REQUEST);
			}
			createRoom(mucRoomEntity, serviceName);
		} catch (NotAllowedException e) {
			throw new ServiceException("Could not update the channel", roomName, ExceptionType.NOT_ALLOWED, Response.Status.FORBIDDEN, e);
		} catch (ForbiddenException e) {
			throw new ServiceException("Could not update the channel", roomName, ExceptionType.NOT_ALLOWED, Response.Status.FORBIDDEN, e);
		} catch (ConflictException e) {
			throw new ServiceException("Could not update the channel", roomName, ExceptionType.NOT_ALLOWED, Response.Status.CONFLICT, e);
		} catch (AlreadyExistsException e) {
			throw new ServiceException("Could not update the channel", mucRoomEntity.getRoomName(),
					ExceptionType.ALREADY_EXISTS, Response.Status.CONFLICT, e);
		}
	}

	/**
	 * Creates the room.
	 *
	 * @param mucRoomEntity
	 *            the MUC room entity
	 * @param serviceName
	 *            the service name
	 * @throws NotAllowedException
	 *             the not allowed exception
	 * @throws ForbiddenException
	 *             the forbidden exception
	 * @throws ConflictException
	 *             the conflict exception
	 * @throws AlreadyExistsException 
	 */
	private void createRoom(MUCRoomEntity mucRoomEntity, String serviceName) throws NotAllowedException,
			ForbiddenException, ConflictException, AlreadyExistsException {

		// Set owner
		JID owner = XMPPServer.getInstance().createJID("admin", null);
		if (mucRoomEntity.getOwners() != null && mucRoomEntity.getOwners().size() > 0) {
			owner = new JID(mucRoomEntity.getOwners().get(0));
		} else {
			List<String> owners = new ArrayList<String>();
			owners.add(owner.toBareJID());
			mucRoomEntity.setOwners(owners);
		}

		//	Check if chat service is available, if not create a new one
		boolean serviceRegistered = XMPPServer.getInstance().getMultiUserChatManager().isServiceRegistered(serviceName);
		if(!serviceRegistered) {
			XMPPServer.getInstance().getMultiUserChatManager().createMultiUserChatService(serviceName, serviceName, false);
		}
		
		MUCRoom room = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(serviceName)
				.getChatRoom(mucRoomEntity.getRoomName().toLowerCase(), owner);

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

		// Set broadcast presence roles
		if (mucRoomEntity.getBroadcastPresenceRoles() != null) {
			room.setRolesToBroadcastPresence(mucRoomEntity.getBroadcastPresenceRoles());
		} else {
			room.setRolesToBroadcastPresence(new ArrayList<String>());
		}
		// Set all roles
		setRoles(room, mucRoomEntity);

		// Set creation date
		if (mucRoomEntity.getCreationDate() != null) {
			room.setCreationDate(mucRoomEntity.getCreationDate());
		} else {
			room.setCreationDate(new Date());
		}

		// Set modification date
		if (mucRoomEntity.getModificationDate() != null) {
			room.setModificationDate(mucRoomEntity.getModificationDate());
		} else {
			room.setModificationDate(new Date());
		}
		
		// Unlock the room, because the default configuration lock the room.  		
		room.unlock(room.getRole());

		// Save the room to the DB if the room should be persistant
		if (room.isPersistent()) {
			room.saveToDB();
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

		mucRoomEntity.setSubject(room.getSubject());
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

		mucRoomEntity.setOwners(MUCRoomUtils.convertJIDsToStringList(room.getOwners()));
		mucRoomEntity.setAdmins(MUCRoomUtils.convertJIDsToStringList(room.getAdmins()));
		mucRoomEntity.setMembers(MUCRoomUtils.convertJIDsToStringList(room.getMembers()));
		mucRoomEntity.setOutcasts(MUCRoomUtils.convertJIDsToStringList(room.getOutcasts()));

		mucRoomEntity.setBroadcastPresenceRoles(room.getRolesToBroadcastPresence());

		mucRoomEntity.setCreationDate(room.getCreationDate());
		mucRoomEntity.setModificationDate(room.getModificationDate());

		return mucRoomEntity;
	}

	/**
	 * Reset roles.
	 *
	 * @param room
	 *            the room
	 * @param mucRoomEntity
	 *            the muc room entity
	 * @throws ForbiddenException
	 *             the forbidden exception
	 * @throws NotAllowedException
	 *             the not allowed exception
	 * @throws ConflictException
	 *             the conflict exception
	 */
	private void setRoles(MUCRoom room, MUCRoomEntity mucRoomEntity) throws ForbiddenException, NotAllowedException,
			ConflictException {
		List<JID> roles = new ArrayList<JID>();
		Collection<JID> owners = new ArrayList<JID>();
		Collection<JID> existingOwners = new ArrayList<JID>();

		List<JID> mucRoomEntityOwners = MUCRoomUtils.convertStringsToJIDs(mucRoomEntity.getOwners());
		owners.addAll(room.getOwners());

		// Find same owners
		for (JID jid : owners) {
			if (mucRoomEntityOwners.contains(jid)) {
				existingOwners.add(jid);
			}
		}

		// Don't delete the same owners
		owners.removeAll(existingOwners);
		room.addOwners(MUCRoomUtils.convertStringsToJIDs(mucRoomEntity.getOwners()), room.getRole());

		// Collect all roles to reset
		roles.addAll(owners);
		roles.addAll(room.getAdmins());
		roles.addAll(room.getMembers());
		roles.addAll(room.getOutcasts());

		for (JID jid : roles) {
			room.addNone(jid, room.getRole());
		}

		room.addOwners(MUCRoomUtils.convertStringsToJIDs(mucRoomEntity.getOwners()), room.getRole());
		if (mucRoomEntity.getAdmins() != null) {
			room.addAdmins(MUCRoomUtils.convertStringsToJIDs(mucRoomEntity.getAdmins()), room.getRole());
		}
		if (mucRoomEntity.getMembers() != null) {
			for (String memberJid : mucRoomEntity.getMembers()) {
				room.addMember(new JID(memberJid), null, room.getRole());
			}
		}
		if (mucRoomEntity.getOutcasts() != null) {
			for (String outcastJid : mucRoomEntity.getOutcasts()) {
				room.addOutcast(new JID(outcastJid), null, room.getRole());
			}
		}
	}
	
	/**
	 * Adds the admin.
	 *
	 * @param serviceName
	 *            the service name
	 * @param roomName
	 *            the room name
	 * @param jid
	 *            the jid
	 * @throws ServiceException
	 *             the service exception
	 */
	public void addAdmin(String serviceName, String roomName, String jid) throws ServiceException {
		MUCRoom room = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(serviceName)
				.getChatRoom(roomName.toLowerCase());
		try {
			room.addAdmin(UserUtils.checkAndGetJID(jid), room.getRole());
		} catch (ForbiddenException e) {
			throw new ServiceException("Could not add admin", jid, ExceptionType.NOT_ALLOWED, Response.Status.FORBIDDEN, e);
		} catch (ConflictException e) {
			throw new ServiceException("Could not add admin", jid, ExceptionType.NOT_ALLOWED, Response.Status.CONFLICT, e);
		}
	}

	/**
	 * Adds the owner.
	 *
	 * @param serviceName
	 *            the service name
	 * @param roomName
	 *            the room name
	 * @param jid
	 *            the jid
	 * @throws ServiceException
	 *             the service exception
	 */
	public void addOwner(String serviceName, String roomName, String jid) throws ServiceException {
		MUCRoom room = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(serviceName)
				.getChatRoom(roomName.toLowerCase());
		try {
			room.addOwner(UserUtils.checkAndGetJID(jid), room.getRole());
		} catch (ForbiddenException e) {
			throw new ServiceException("Could not add owner", jid, ExceptionType.NOT_ALLOWED, Response.Status.FORBIDDEN, e);
		}
	}

	/**
	 * Adds the member.
	 *
	 * @param serviceName
	 *            the service name
	 * @param roomName
	 *            the room name
	 * @param jid
	 *            the jid
	 * @throws ServiceException
	 *             the service exception
	 */
	public void addMember(String serviceName, String roomName, String jid) throws ServiceException {
		MUCRoom room = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(serviceName)
				.getChatRoom(roomName.toLowerCase());
		try {
			room.addMember(UserUtils.checkAndGetJID(jid), null, room.getRole());
		} catch (ForbiddenException e) {
			throw new ServiceException("Could not add member", jid, ExceptionType.NOT_ALLOWED, Response.Status.FORBIDDEN, e);
		} catch (ConflictException e) {
			throw new ServiceException("Could not add member", jid, ExceptionType.NOT_ALLOWED, Response.Status.FORBIDDEN, e);
		}
	}

	/**
	 * Adds the outcast.
	 *
	 * @param serviceName
	 *            the service name
	 * @param roomName
	 *            the room name
	 * @param jid
	 *            the jid
	 * @throws ServiceException
	 *             the service exception
	 */
	public void addOutcast(String serviceName, String roomName, String jid) throws ServiceException {
		MUCRoom room = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(serviceName)
				.getChatRoom(roomName.toLowerCase());
		try {
			room.addOutcast(UserUtils.checkAndGetJID(jid), null, room.getRole());
		} catch (NotAllowedException e) {
			throw new ServiceException("Could not add outcast", jid, ExceptionType.NOT_ALLOWED, Response.Status.FORBIDDEN, e);
		} catch (ForbiddenException e) {
			throw new ServiceException("Could not add outcast", jid, ExceptionType.NOT_ALLOWED, Response.Status.FORBIDDEN, e);
		} catch (ConflictException e) {
			throw new ServiceException("Could not add outcast", jid, ExceptionType.NOT_ALLOWED, Response.Status.CONFLICT, e);
		}
	}

	/**
	 * Delete affiliation.
	 *
	 * @param serviceName
	 *            the service name
	 * @param roomName
	 *            the room name
	 * @param jid
	 *            the jid
	 * @throws ServiceException
	 *             the service exception
	 */
	public void deleteAffiliation(String serviceName, String roomName, String jid) throws ServiceException {
		MUCRoom room = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(serviceName)
				.getChatRoom(roomName.toLowerCase());
		try {
			room.addNone(UserUtils.checkAndGetJID(jid), room.getRole());
		} catch (ForbiddenException e) {
			throw new ServiceException("Could not delete affiliation", jid, ExceptionType.NOT_ALLOWED, Response.Status.FORBIDDEN, e);
		} catch (ConflictException e) {
			throw new ServiceException("Could not delete affiliation", jid, ExceptionType.NOT_ALLOWED, Response.Status.CONFLICT, e);
		}
	}
}