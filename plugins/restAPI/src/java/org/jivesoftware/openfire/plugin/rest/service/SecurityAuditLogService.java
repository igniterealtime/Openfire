package org.jivesoftware.openfire.plugin.rest.service;

import javax.annotation.PostConstruct;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.jivesoftware.openfire.plugin.rest.controller.SecurityAuditLogController;
import org.jivesoftware.openfire.plugin.rest.entity.SecurityAuditLogs;
import org.jivesoftware.openfire.plugin.rest.exceptions.ServiceException;

@Path("restapi/v1/logs/security")
public class SecurityAuditLogService {

	private SecurityAuditLogController securityAuditLogController;

	@PostConstruct
	public void init() {
		securityAuditLogController = SecurityAuditLogController.getInstance();
	}

	@GET
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	public SecurityAuditLogs getSecurityAuditLogs(@QueryParam("username") String username,
			@QueryParam("offset") int offset,
			@DefaultValue("100") @QueryParam("limit") int limit, @QueryParam("startTime") long startTime,
			@QueryParam("endTime") long endTime) throws ServiceException {

		return securityAuditLogController.getSecurityAuditLogs(username, offset, limit, startTime, endTime);
	}
}
