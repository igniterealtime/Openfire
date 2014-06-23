package org.jivesoftware.openfire.service;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.jivesoftware.openfire.entity.MUCChannelType;
import org.jivesoftware.openfire.entity.MUCRoomEntities;
import org.jivesoftware.openfire.entity.MUCRoomEntity;
import org.jivesoftware.openfire.exception.MUCServiceException;
import org.jivesoftware.openfire.plugin.MUCRoomController;

@Path("mucservice")
public class MUCRoomService {

	@GET
	@Path("/chatrooms")
	@Produces(MediaType.APPLICATION_XML)
	public MUCRoomEntities getMUCRooms(@DefaultValue("conference") @QueryParam("servicename") String serviceName,
			@DefaultValue(MUCChannelType.PUBLIC) @QueryParam("type") String channelType,
			@QueryParam("search") String roomSearch) {
		return MUCRoomController.getInstance().getChatRooms(serviceName, channelType, roomSearch);
	}

	@GET
	@Path("/chatrooms/{roomName}")
	@Produces(MediaType.APPLICATION_XML)
	public MUCRoomEntity getMUCRoom(@PathParam("roomName") String roomName,
			@DefaultValue("conference") @QueryParam("servicename") String serviceName) throws MUCServiceException {
		return MUCRoomController.getInstance().getChatRoom(roomName, serviceName);
	}

	@DELETE
	@Path("/chatrooms/{roomName}")
	public void deleteMUCRoom(@PathParam("roomName") String roomName,
			@DefaultValue("conference") @QueryParam("servicename") String serviceName) throws MUCServiceException {
		MUCRoomController.getInstance().deleteChatRoom(roomName, serviceName);
	}

	@POST
	@Path("/chatrooms")
	public void createMUCRoom(@DefaultValue("conference") @QueryParam("servicename") String serviceName,
			@DefaultValue("admin") @QueryParam("owner") String owner, MUCRoomEntity mucRoomEntity)
			throws MUCServiceException {
		MUCRoomController.getInstance().createChatRoom(serviceName, owner, mucRoomEntity);
	}

	@PUT
	@Path("/chatrooms/{roomName}")
	public void udpateMUCRoom(@PathParam("roomName") String roomName,
			@DefaultValue("conference") @QueryParam("servicename") String serviceName,
			@DefaultValue("admin") @QueryParam("owner") String owner, MUCRoomEntity mucRoomEntity)
			throws MUCServiceException {
		MUCRoomController.getInstance().updateChatRoom(roomName, serviceName, owner, mucRoomEntity);
	}

}
