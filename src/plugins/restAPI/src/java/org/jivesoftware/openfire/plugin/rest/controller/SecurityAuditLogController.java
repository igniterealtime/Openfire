package org.jivesoftware.openfire.plugin.rest.controller;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.ws.rs.core.Response;

import org.jivesoftware.openfire.plugin.rest.entity.SecurityAuditLog;
import org.jivesoftware.openfire.plugin.rest.entity.SecurityAuditLogs;
import org.jivesoftware.openfire.plugin.rest.exceptions.ServiceException;
import org.jivesoftware.openfire.security.AuditWriteOnlyException;
import org.jivesoftware.openfire.security.SecurityAuditEvent;
import org.jivesoftware.openfire.security.SecurityAuditManager;

public class SecurityAuditLogController {
	public static final SecurityAuditLogController INSTANCE = new SecurityAuditLogController();

	public static SecurityAuditLogController getInstance() {
		return INSTANCE;
	}

	public SecurityAuditLogs getSecurityAuditLogs(String username, int offset, int limit, long startTimeTimeStamp, 
			long endTimeTimeStamp) throws ServiceException {
		Date startTime = null;
		Date endTime = null;
		
		if (startTimeTimeStamp != 0) {
			startTime = new Date(startTimeTimeStamp * 1000);
		}
		
		if (endTimeTimeStamp != 0) {
			endTime = new Date(endTimeTimeStamp * 1000);
		}
		
		List<SecurityAuditEvent> events = new ArrayList<SecurityAuditEvent>();
		
		try {
			events = SecurityAuditManager.getInstance().getEvents(username, offset, limit, startTime, endTime);
		} catch (AuditWriteOnlyException e) {
			throw new ServiceException("Could not get security audit logs, because the permission is set to write only",
					"SecurityLogs", "AuditWriteOnlyException", Response.Status.FORBIDDEN);
		}
		
		List<SecurityAuditLog> securityAuditLogs = new ArrayList<SecurityAuditLog>();
		for (SecurityAuditEvent event : events) {
			SecurityAuditLog log = new SecurityAuditLog(event.getMsgID(), event.getUsername(), event.getEventStamp().getTime() / 1000, event.getSummary(), event.getNode(), event.getDetails());
			securityAuditLogs.add(log);
		}
		
		return new SecurityAuditLogs(securityAuditLogs);
	}
}
