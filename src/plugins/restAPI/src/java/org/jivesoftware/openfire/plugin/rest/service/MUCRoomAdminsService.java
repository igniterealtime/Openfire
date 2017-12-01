package org.jivesoftware.openfire.plugin.rest.service;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.jivesoftware.openfire.plugin.rest.exceptions.ServiceException;
import org.jivesoftware.openfire.plugin.rest.controller.MUCRoomController;

@Path("restapi/v1/chatrooms/{roomName}/admins")
public class MUCRoomAdminsService {

    @POST
    @Path("/{jid}")
    public Response addMUCRoomAdmin(@DefaultValue("conference") @QueryParam("servicename") String serviceName,
            @PathParam("jid") String jid, @PathParam("roomName") String roomName) throws ServiceException {
        MUCRoomController.getInstance().addAdmin(serviceName, roomName, jid);
        return Response.status(Status.CREATED).build();
    }

    @POST
    @Path("/group/{groupname}")
    public Response addMUCRoomAdminGroup(@DefaultValue("conference") @QueryParam("servicename") String serviceName,
            @PathParam("groupname") String groupname, @PathParam("roomName") String roomName) throws ServiceException {
        MUCRoomController.getInstance().addAdmin(serviceName, roomName, groupname);
        return Response.status(Status.CREATED).build();
    }

    @DELETE
    @Path("/{jid}")
    public Response deleteMUCRoomAdmin(@PathParam("jid") String jid,
            @DefaultValue("conference") @QueryParam("servicename") String serviceName,
            @PathParam("roomName") String roomName) throws ServiceException {
        MUCRoomController.getInstance().deleteAffiliation(serviceName, roomName, jid);
        return Response.status(Status.OK).build();
    }

    @DELETE
    @Path("/group/{groupname}")
    public Response deleteMUCRoomAdminGroup(@PathParam("groupname") String groupname,
            @DefaultValue("conference") @QueryParam("servicename") String serviceName,
            @PathParam("roomName") String roomName) throws ServiceException {
        MUCRoomController.getInstance().deleteAffiliation(serviceName, roomName, groupname);
        return Response.status(Status.OK).build();
    }
}
