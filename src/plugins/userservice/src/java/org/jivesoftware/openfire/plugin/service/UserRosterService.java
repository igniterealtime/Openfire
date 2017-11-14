package org.jivesoftware.openfire.plugin.service;

import javax.annotation.PostConstruct;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jivesoftware.openfire.SharedGroupException;
import org.jivesoftware.openfire.entity.RosterEntities;
import org.jivesoftware.openfire.entity.RosterItemEntity;
import org.jivesoftware.openfire.exceptions.ExceptionType;
import org.jivesoftware.openfire.exceptions.ServiceException;
import org.jivesoftware.openfire.plugin.UserServicePluginNG;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserNotFoundException;

@Path("userService/users/{username}/roster")
public class UserRosterService {

    private static final String COULD_NOT_UPDATE_THE_ROSTER = "Could not update the roster";

    private static final String COULD_NOT_CREATE_ROSTER_ITEM = "Could not create roster item";

    private UserServicePluginNG plugin;

    @PostConstruct
    public void init() {
        plugin = UserServicePluginNG.getInstance();
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public RosterEntities getUserRoster(@PathParam("username") String username) throws ServiceException {
        return plugin.getRosterEntities(username);
    }

    @POST
    public Response createRoster(@PathParam("username") String username, RosterItemEntity rosterItemEntity)
            throws ServiceException {
        try {
            plugin.addRosterItem(username, rosterItemEntity);
        } catch (UserNotFoundException e) {
            throw new ServiceException(COULD_NOT_CREATE_ROSTER_ITEM, "", ExceptionType.USER_NOT_FOUND_EXCEPTION,
                    Response.Status.NOT_FOUND, e);
        } catch (UserAlreadyExistsException e) {
            throw new ServiceException(COULD_NOT_CREATE_ROSTER_ITEM, "", ExceptionType.USER_ALREADY_EXISTS_EXCEPTION,
                    Response.Status.BAD_REQUEST, e);
        } catch (SharedGroupException e) {
            throw new ServiceException(COULD_NOT_CREATE_ROSTER_ITEM, "", ExceptionType.SHARED_GROUP_EXCEPTION,
                    Response.Status.BAD_REQUEST, e);
        }
        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @Path("/{rosterJid}")
    public Response deleteRoster(@PathParam("username") String username, @PathParam("rosterJid") String rosterJid)
            throws ServiceException {
        try {
            plugin.deleteRosterItem(username, rosterJid);
        } catch (SharedGroupException e) {
            throw new ServiceException("Could not delete the roster item", rosterJid,
                    ExceptionType.SHARED_GROUP_EXCEPTION, Response.Status.BAD_REQUEST, e);
        }
        return Response.status(Response.Status.OK).build();
    }

    @PUT
    @Path("/{rosterJid}")
    public Response updateRoster(@PathParam("username") String username, @PathParam("rosterJid") String rosterJid,
            RosterItemEntity rosterItemEntity) throws ServiceException {
        try {
            plugin.updateRosterItem(username, rosterJid, rosterItemEntity);
        } catch (UserNotFoundException e) {
            throw new ServiceException(COULD_NOT_UPDATE_THE_ROSTER, rosterJid, ExceptionType.USER_NOT_FOUND_EXCEPTION,
                    Response.Status.NOT_FOUND, e);
        } catch (SharedGroupException e) {
            throw new ServiceException(COULD_NOT_UPDATE_THE_ROSTER, rosterJid, ExceptionType.SHARED_GROUP_EXCEPTION,
                    Response.Status.BAD_REQUEST, e);
        } catch (UserAlreadyExistsException e) {
            throw new ServiceException(COULD_NOT_UPDATE_THE_ROSTER, rosterJid,
                    ExceptionType.USER_ALREADY_EXISTS_EXCEPTION, Response.Status.BAD_REQUEST, e);
        }
        return Response.status(Response.Status.OK).build();
    }
}
