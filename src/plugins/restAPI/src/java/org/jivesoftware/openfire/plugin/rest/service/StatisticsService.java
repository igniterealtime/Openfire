package org.jivesoftware.openfire.plugin.rest.service;

import javax.annotation.PostConstruct;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.jivesoftware.openfire.plugin.rest.controller.StatisticsController;
import org.jivesoftware.openfire.plugin.rest.entity.SessionsCount;
import org.jivesoftware.openfire.plugin.rest.exceptions.ServiceException;

@Path("restapi/v1/system/statistics")
public class StatisticsService {

    private StatisticsController controller;

    @PostConstruct
    public void init() {
        controller = StatisticsController.getInstance();
    }

    @GET
    @Path("/sessions")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public SessionsCount getCCS() throws ServiceException {
        return controller.getConcurentSessions();
    }
}
