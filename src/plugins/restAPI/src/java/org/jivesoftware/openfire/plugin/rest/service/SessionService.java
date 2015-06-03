package org.jivesoftware.openfire.plugin.rest.service;

import javax.annotation.PostConstruct;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.jivesoftware.openfire.plugin.rest.controller.SessionController;
import org.jivesoftware.openfire.plugin.rest.entity.SessionEntities;
import org.jivesoftware.openfire.plugin.rest.exceptions.ServiceException;

@Path("restapi/v1/sessions")
public class SessionService {

	private SessionController sessionController;

	@PostConstruct
	public void init() {
		sessionController = SessionController.getInstance();
	}

	@GET
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	public SessionEntities getAllSessions() throws ServiceException {
		return sessionController.getAllSessions();
	}
	
	@GET
	@Path("/{username}")
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	public SessionEntities getUserSessions(@PathParam("username") String username) throws ServiceException {
		return sessionController.getUserSessions(username);
	}
	
}
