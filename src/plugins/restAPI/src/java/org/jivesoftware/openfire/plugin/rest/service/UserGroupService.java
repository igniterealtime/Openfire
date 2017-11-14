package org.jivesoftware.openfire.plugin.rest.service;

import javax.annotation.PostConstruct;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jivesoftware.openfire.plugin.rest.controller.UserServiceController;
import org.jivesoftware.openfire.plugin.rest.entity.UserGroupsEntity;
import org.jivesoftware.openfire.plugin.rest.exceptions.ServiceException;

@Path("restapi/v1/users/{username}/groups")
public class UserGroupService {

    private UserServiceController plugin;

    @PostConstruct
    public void init() {
        plugin = UserServiceController.getInstance();
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public UserGroupsEntity getUserGroups(@PathParam("username") String username) throws ServiceException {
        return new UserGroupsEntity(plugin.getUserGroups(username));
    }

    @POST
    public Response addUserToGroups(@PathParam("username") String username, UserGroupsEntity userGroupsEntity)
            throws ServiceException {
        plugin.addUserToGroups(username, userGroupsEntity);
        return Response.status(Response.Status.CREATED).build();
    }
    
    @POST
    @Path("/{groupName}")
    public Response addUserToGroup(@PathParam("username") String username, @PathParam("groupName") String groupName)
            throws ServiceException {
        plugin.addUserToGroup(username, groupName);
        return Response.status(Response.Status.CREATED).build();
    }
    
    @DELETE
    @Path("/{groupName}")
    public Response deleteUserFromGroup(@PathParam("username") String username, @PathParam("groupName") String groupName)
            throws ServiceException {
        plugin.deleteUserFromGroup(username, groupName);
        return Response.status(Response.Status.OK).build();
    }

    @DELETE
    public Response deleteUserFromGroups(@PathParam("username") String username, UserGroupsEntity userGroupsEntity)
            throws ServiceException {
        plugin.deleteUserFromGroups(username, userGroupsEntity);
        return Response.status(Response.Status.OK).build();
    }
}
