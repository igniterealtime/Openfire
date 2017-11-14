package org.jivesoftware.openfire.service;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.jivesoftware.openfire.exception.MUCServiceException;
import org.jivesoftware.openfire.plugin.MUCRoomController;

@Path("mucservice/chatrooms/{roomName}/owners")
public class MUCRoomOwnersService {

    @POST
    @Path("/{jid}")
    public Response addMUCRoomOwner(@DefaultValue("conference") @QueryParam("servicename") String serviceName,
            @PathParam("jid") String jid, @PathParam("roomName") String roomName) throws MUCServiceException {
        MUCRoomController.getInstance().addOwner(serviceName, roomName, jid);
        return Response.status(Status.CREATED).build();
    }

    @DELETE
    @Path("/{jid}")
    public Response deleteMUCRoomOwner(@PathParam("jid") String jid,
            @DefaultValue("conference") @QueryParam("servicename") String serviceName,
            @PathParam("roomName") String roomName) throws MUCServiceException {
        MUCRoomController.getInstance().deleteAffiliation(serviceName, roomName, jid);
        return Response.status(Status.OK).build();
    }
}
