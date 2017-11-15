package org.jivesoftware.openfire.plugin.rest.service;

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

import org.jivesoftware.openfire.plugin.rest.RESTServicePlugin;
import org.jivesoftware.openfire.plugin.rest.entity.SystemProperties;
import org.jivesoftware.openfire.plugin.rest.entity.SystemProperty;
import org.jivesoftware.openfire.plugin.rest.exceptions.ServiceException;

@Path("restapi/v1/system/properties")
public class RestAPIService {

    private RESTServicePlugin plugin;

    @PostConstruct
    public void init() {
        plugin = RESTServicePlugin.getInstance();
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public SystemProperties getSystemProperties() {
        return plugin.getSystemProperties();
    }
    
    @GET
    @Path("/{propertyKey}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public SystemProperty getSystemProperty(@PathParam("propertyKey") String propertyKey) throws ServiceException {
        return plugin.getSystemProperty(propertyKey);
    }

    @POST
    public Response createSystemProperty(SystemProperty systemProperty) throws ServiceException {
        plugin.createSystemProperty(systemProperty);
        return Response.status(Response.Status.CREATED).build();
    }

    @PUT
    @Path("/{propertyKey}")
    public Response updateUser(@PathParam("propertyKey") String propertyKey, SystemProperty systemProperty) throws ServiceException {
        plugin.updateSystemProperty(propertyKey, systemProperty);
        return Response.status(Response.Status.OK).build();
    }

    @DELETE
    @Path("/{propertyKey}")
    public Response deleteUser(@PathParam("propertyKey") String propertyKey) throws ServiceException {
        plugin.deleteSystemProperty(propertyKey);
        return Response.status(Response.Status.OK).build();
    }
}
