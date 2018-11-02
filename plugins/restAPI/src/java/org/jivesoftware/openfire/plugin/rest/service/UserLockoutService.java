package org.jivesoftware.openfire.plugin.rest.service;

import javax.annotation.PostConstruct;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import org.jivesoftware.openfire.plugin.rest.controller.UserServiceController;
import org.jivesoftware.openfire.plugin.rest.exceptions.ServiceException;

@Path("restapi/v1/lockouts")
public class UserLockoutService {

    private UserServiceController plugin;

    @PostConstruct
    public void init() {
        plugin = UserServiceController.getInstance();
    }

    @POST
    @Path("/{username}")
    public Response disableUser(@PathParam("username") String username) throws ServiceException {
        plugin.disableUser(username);
        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @Path("/{username}")
    public Response enableUser(@PathParam("username") String username) throws ServiceException {
        plugin.enableUser(username);
        return Response.status(Response.Status.OK).build();
    }
}
