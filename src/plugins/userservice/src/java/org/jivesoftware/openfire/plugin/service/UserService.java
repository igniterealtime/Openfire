package org.jivesoftware.openfire.plugin.service;

import javax.annotation.PostConstruct;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jivesoftware.openfire.entity.UserEntities;
import org.jivesoftware.openfire.entity.UserEntity;
import org.jivesoftware.openfire.exceptions.ServiceException;
import org.jivesoftware.openfire.plugin.UserServicePluginNG;

@Path("userService/users")
public class UserService {

    private UserServicePluginNG plugin;

    @PostConstruct
    public void init() {
        plugin = UserServicePluginNG.getInstance();
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public UserEntities getUsers(@QueryParam("search") String userSearch) {
        return plugin.getUserEntities(userSearch);
    }

    @POST
    public Response createUser(UserEntity userEntity) throws ServiceException {
        plugin.createUser(userEntity);
        return Response.status(Response.Status.CREATED).build();
    }

    @GET
    @Path("/{username}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public UserEntity getUser(@PathParam("username") String username) throws ServiceException {
        return plugin.getUserEntity(username);
    }

    @PUT
    @Path("/{username}")
    public Response updateUser(@PathParam("username") String username, UserEntity userEntity) throws ServiceException {
        plugin.updateUser(username, userEntity);
        return Response.status(Response.Status.OK).build();
    }

    @DELETE
    @Path("/{username}")
    public Response deleteUser(@PathParam("username") String username) throws ServiceException {
        plugin.deleteUser(username);
        return Response.status(Response.Status.OK).build();
    }
}
