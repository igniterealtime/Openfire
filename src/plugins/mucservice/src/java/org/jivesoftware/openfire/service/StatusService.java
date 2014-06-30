package org.jivesoftware.openfire.service;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.entity.StatusEntity;

@Path("mucservice/status")
public class StatusService {

	@GET
	@Produces(MediaType.APPLICATION_XML)
	public StatusEntity getMUCRooms() {
		StatusEntity statusEntity = new StatusEntity();
		
		statusEntity.setActiveSessions(SessionManager.getInstance().getConnectionsCount(false));
		statusEntity.setXmppDomain(XMPPServer.getInstance().getServerInfo().getHostname());
		
		return statusEntity;
	}
}
