package org.jivesoftware.openfire.service;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.entity.StatusEntity;
import org.logicalcobwebs.proxool.ProxoolException;
import org.logicalcobwebs.proxool.ProxoolFacade;
import org.logicalcobwebs.proxool.admin.SnapshotIF;

@Path("mucservice/status")
public class StatusService {

	@GET
	@Produces(MediaType.APPLICATION_XML)
	public StatusEntity getMUCRooms() {
		StatusEntity statusEntity = new StatusEntity();

		statusEntity.setActiveSessions(SessionManager.getInstance().getConnectionsCount(false));
		statusEntity.setXmppDomain(XMPPServer.getInstance().getServerInfo().getXMPPDomain());
		statusEntity.setHostname(XMPPServer.getInstance().getServerInfo().getHostname());
		statusEntity.setOpenfireVersion(XMPPServer.getInstance().getServerInfo().getVersion().getVersionString());

		if (DbConnectionManager.getConnectionProvider().isPooled()) {
			SnapshotIF poolStats;
			try {
				poolStats = ProxoolFacade.getSnapshot("openfire", true);
				statusEntity.setActiveDatabaseConnections(poolStats.getActiveConnectionCount());
				statusEntity.setMaxDatabaseConnections(poolStats.getMaximumConnectionCount());
			} catch (ProxoolException e) {
			}
		}

		return statusEntity;
	}
}
