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
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.jivesoftware.openfire.entity.MUCChannelType;
import org.jivesoftware.openfire.entity.MUCRoomEntities;
import org.jivesoftware.openfire.entity.MUCRoomEntity;
import org.jivesoftware.openfire.entity.ParticipantEntities;
import org.jivesoftware.openfire.exception.MUCServiceException;
import org.jivesoftware.openfire.plugin.MUCRoomController;

@Path("mucservice/chatrooms")
public class MUCRoomService {

    @GET
    @Produces(MediaType.APPLICATION_XML)
    public MUCRoomEntities getMUCRooms(@DefaultValue("conference") @QueryParam("servicename") String serviceName,
            @DefaultValue(MUCChannelType.PUBLIC) @QueryParam("type") String channelType,
            @QueryParam("search") String roomSearch) {
        return MUCRoomController.getInstance().getChatRooms(serviceName, channelType, roomSearch);
    }

    @GET
    @Path("/{roomName}")
    @Produces(MediaType.APPLICATION_XML)
    public MUCRoomEntity getMUCRoom(@PathParam("roomName") String roomName,
            @DefaultValue("conference") @QueryParam("servicename") String serviceName) throws MUCServiceException {
        return MUCRoomController.getInstance().getChatRoom(roomName, serviceName);
    }

    @DELETE
    @Path("/{roomName}")
    public Response deleteMUCRoom(@PathParam("roomName") String roomName,
            @DefaultValue("conference") @QueryParam("servicename") String serviceName) throws MUCServiceException {
        MUCRoomController.getInstance().deleteChatRoom(roomName, serviceName);
        return Response.status(Status.OK).build();
    }

    @POST
    public Response createMUCRoom(@DefaultValue("conference") @QueryParam("servicename") String serviceName,
            MUCRoomEntity mucRoomEntity) throws MUCServiceException {
        MUCRoomController.getInstance().createChatRoom(serviceName, mucRoomEntity);
        return Response.status(Status.CREATED).build();
    }

    @PUT
    @Path("/{roomName}")
    public Response udpateMUCRoom(@PathParam("roomName") String roomName,
            @DefaultValue("conference") @QueryParam("servicename") String serviceName, MUCRoomEntity mucRoomEntity)
            throws MUCServiceException {
        MUCRoomController.getInstance().updateChatRoom(roomName, serviceName, mucRoomEntity);
        return Response.status(Status.OK).build();
    }

    @GET
    @Path("/{roomName}/participants")
    @Produces(MediaType.APPLICATION_XML)
    public ParticipantEntities getMUCRoomParticipants(@PathParam("roomName") String roomName,
            @DefaultValue("conference") @QueryParam("servicename") String serviceName) {
        return MUCRoomController.getInstance().getRoomParticipants(roomName, serviceName);
    }
}
