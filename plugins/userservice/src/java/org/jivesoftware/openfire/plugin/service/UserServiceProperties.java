package org.jivesoftware.openfire.plugin.service;

import javax.annotation.PostConstruct;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.jivesoftware.openfire.entity.UserEntities;
import org.jivesoftware.openfire.exceptions.ServiceException;
import org.jivesoftware.openfire.plugin.UserServicePluginNG;

@Path("userService/properties")
public class UserServiceProperties {

    private UserServicePluginNG plugin;

    @PostConstruct
    public void init() {
        plugin = UserServicePluginNG.getInstance();
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{key}")
    public UserEntities getUsersByPropertyKey(@PathParam("key") String key) throws ServiceException {
        return plugin.getUserEntitiesByProperty(key, null);
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{key}/{value}")
    public UserEntities getUsersByPropertyKeyValue(@PathParam("key") String key, @PathParam("value") String value)
            throws ServiceException {
        return plugin.getUserEntitiesByProperty(key, value);
    }
}
