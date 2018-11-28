package org.jivesoftware.openfire.plugin.rest.service;

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

import org.jivesoftware.openfire.plugin.rest.entity.*;
import org.jivesoftware.openfire.plugin.rest.exceptions.ServiceException;
import org.jivesoftware.openfire.plugin.rest.controller.MUCRoomController;

@Path("restapi/v1/chatrooms")
public class MUCRoomService {

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public MUCRoomEntities getMUCRooms(@DefaultValue("conference") @QueryParam("servicename") String serviceName,
            @DefaultValue(MUCChannelType.PUBLIC) @QueryParam("type") String channelType,
            @QueryParam("search") String roomSearch,
            @DefaultValue("false") @QueryParam("expandGroups") Boolean expand) {
        return MUCRoomController.getInstance().getChatRooms(serviceName, channelType, roomSearch, expand);
    }

    @GET
    @Path("/{roomName}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public MUCRoomEntity getMUCRoomJSON2(@PathParam("roomName") String roomName,
            @DefaultValue("conference") @QueryParam("servicename") String serviceName,
            @DefaultValue("false") @QueryParam("expandGroups") Boolean expand) throws ServiceException {
        return MUCRoomController.getInstance().getChatRoom(roomName, serviceName, expand);
    }


    @DELETE
    @Path("/{roomName}")
    public Response deleteMUCRoom(@PathParam("roomName") String roomName,
            @DefaultValue("conference") @QueryParam("servicename") String serviceName) throws ServiceException {
        MUCRoomController.getInstance().deleteChatRoom(roomName, serviceName);
        return Response.status(Status.OK).build();
    }

    @POST
    public Response createMUCRoom(@DefaultValue("conference") @QueryParam("servicename") String serviceName,
            MUCRoomEntity mucRoomEntity) throws ServiceException {
        MUCRoomController.getInstance().createChatRoom(serviceName, mucRoomEntity);
        return Response.status(Status.CREATED).build();
    }

    @PUT
    @Path("/{roomName}")
    public Response udpateMUCRoom(@PathParam("roomName") String roomName,
            @DefaultValue("conference") @QueryParam("servicename") String serviceName, MUCRoomEntity mucRoomEntity)
            throws ServiceException {
        MUCRoomController.getInstance().updateChatRoom(roomName, serviceName, mucRoomEntity);
        return Response.status(Status.OK).build();
    }

    @GET
    @Path("/{roomName}/participants")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public ParticipantEntities getMUCRoomParticipants(@PathParam("roomName") String roomName,
            @DefaultValue("conference") @QueryParam("servicename") String serviceName) {
        return MUCRoomController.getInstance().getRoomParticipants(roomName, serviceName);
    }

    @GET
    @Path("/{roomName}/occupants")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public OccupantEntities getMUCRoomOccupants(@PathParam("roomName") String roomName,
            @DefaultValue("conference") @QueryParam("servicename") String serviceName) {
        return MUCRoomController.getInstance().getRoomOccupants(roomName, serviceName);
    }

    @GET
    @Path("/{roomName}/chathistory")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public MUCRoomMessageEntities getMUCRoomHistory(@PathParam("roomName") String roomName,
                                                    @DefaultValue("conference") @QueryParam("servicename") String serviceName) throws ServiceException {
        return MUCRoomController.getInstance().getRoomHistory(roomName, serviceName);
    }

    @POST
    @Path("/{roomName}/invite/{jid}")
    public Response inviteUserToMUCRoom(@PathParam("roomName") String roomName, @PathParam("jid") String jid,
            @DefaultValue("conference") @QueryParam("servicename") String serviceName,
            MUCInvitationEntity mucInvitationEntity) throws ServiceException {
        MUCRoomController.getInstance().inviteUser(serviceName, roomName, jid, mucInvitationEntity);
        return Response.status(Status.OK).build();
    }

}
