<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2005-2008 Jive Software. All rights reserved.
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
--%>

<%@ page
	import="org.jivesoftware.openfire.PresenceManager,
                 org.jivesoftware.openfire.group.Group,
                 org.jivesoftware.openfire.group.GroupManager,
                 org.jivesoftware.openfire.security.SecurityAuditManager,
                 org.jivesoftware.openfire.user.User,
                 org.jivesoftware.openfire.user.UserManager,
                 org.jivesoftware.openfire.user.UserNotFoundException"%>
<%@ page import="gnu.inet.encoding.Stringprep"%>
<%@ page import="org.jivesoftware.util.LocaleUtils"%>
<%@ page import="org.jivesoftware.util.Log"%>
<%@ page import="org.jivesoftware.util.ParamUtils"%>
<%@ page import="org.jivesoftware.util.StringUtils"%>
<%@ page import="org.xmpp.packet.JID"%>
<%@ page import="org.xmpp.packet.Presence"%>
<%@ page import="java.io.UnsupportedEncodingException"%>
<%@ page import="java.net.URLDecoder"%>
<%@ page import="java.net.URLEncoder"%>
<%@ page import="java.util.*"%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt"%>
<!-- Define Administration Bean -->
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<%
	webManager.init(pageContext);
%>

<%
	// Get parameters
	boolean cancel = request.getParameter("cancel") != null;
	boolean update = request.getParameter("update") != null;
	boolean success = ParamUtils.getBooleanParameter(request,"updatesuccess");
	String username = ParamUtils.getParameter(request, "username");
	Map<String, String> errors = new HashMap<String, String>();

	JID jid = webManager.getXMPPServer().createJID(username, null);
	GroupManager groupManager = webManager.getGroupManager();
	Collection<Group> groups = webManager.getGroupManager().getGroups();
	
	
    // Handle a cancel
    if (cancel) {
        response.sendRedirect("user-properties.jsp?username=" + URLEncoder.encode(username, "UTF-8"));
        return;
    }
    
    if(success) { %>
	    <div class="jive-success">
	    <table cellpadding="0" cellspacing="0" border="0">
	    <tbody>
	        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
	        <td class="jive-icon-label">
	        <fmt:message key="user.groups.form.update" />
	        </td></tr>
	    </tbody>
	    </table>
	    </div><br> <% 
    }
    
    // Handle a save/update
    if(update) {
    	for (Group group : groups) {
    		String role = ParamUtils.getParameter(request, URLEncoder.encode(group.getName(), "UTF-8"));
    		if("admin".equals(role)) {
    			if(!group.getAdmins().contains(jid)) {
    				group.getAdmins().add(jid);
    			}
    		} else if("member".equals(role)) {
    			if(!group.getMembers().contains(jid)) {
    				group.getMembers().add(jid);
    			}
    		} else {
    			if(group.getMembers().contains(jid)) {
    				group.getMembers().remove(jid);
    			}
    			if(group.getAdmins().contains(jid)) {
    				group.getAdmins().remove(jid);
    			}
    		}
    	}
        response.sendRedirect("user-groups.jsp?username=" + URLEncoder.encode(username, "UTF-8") + "&updatesuccess=true");
        return;
    }
%>

<html>
<head>
	<title><fmt:message key="user.groups.title" /></title>
	<meta name="subPageID" content="user-groups" />
	<meta name="extraParams" content="<%= "username="+URLEncoder.encode(username, "UTF-8") %>"/>
</head>
<body>

	<p>
		<fmt:message key="user.groups.info" /> <b><%= username %>.</b>
	</p>

	<form action="user-groups.jsp" method="post" name="main">
	<div class="jive-table">
		<table cellpadding="0" cellspacing="0" border="0" width="100%">
			<thead>
				<tr>
					<th>&nbsp;</th>
					<th nowrap><fmt:message key="user.groups.name" /></th>
					<th nowrap><fmt:message key="user.groups.none" /></th>
					<th nowrap><fmt:message key="user.groups.member" /></th>
					<th nowrap><fmt:message key="user.groups.admin" /></th>
				</tr>
			</thead>
			<tbody>

				<%
					// Print the list of groups
					if (groups.isEmpty()) {
				%>
				<tr>
					<td align="center" colspan="6"><fmt:message key="group.summary.no_groups" /></td>
				</tr>

				<%
					}
					int i = 0;
					for (Group group : groups) {
						String groupName = URLEncoder.encode(group.getName(), "UTF-8");
						i++;
				%>
				<tr class="jive-<%=(((i % 2) == 0) ? "even" : "odd")%>">
					<td width="1%" valign="top"><%=i%></td>
					<td width="40%"><a href="group-edit.jsp?group=<%=groupName%>"><%=StringUtils.escapeHTMLTags(group.getName())%></a>
						<%
							if (group.getDescription() != null) {
						%> <br> <span class="jive-description"> <%=StringUtils.escapeHTMLTags(group.getDescription())%>
					</span> <%
	 						}
	 					%></td>
	 					
	 				<td width="10%"><input type="radio"
						name="<%= groupName %>" value="none"
						<% if (!group.isUser(jid)) { %> checked <% } %>>
					</td>	
					<td width="10%"><input type="radio"
						name="<%= groupName %>" value="member"
						<% if (group.getMembers().contains(jid)) { %> checked <% } %>>
					</td>
					<td width="10%"><input type="radio"
						name="<%= groupName %>" value="admin"
						<% if (group.getAdmins().contains(jid)) { %> checked <% } %>>
					</td>
				</tr>
				<%
					}
				%>
			</tbody>
		</table>
	</div>
	<br />
	<input type="hidden" name="username" value="<%= StringUtils.escapeForXML(username) %>">
	<input type="submit" name="update" value="<fmt:message key="global.save_changes" />">
	<input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">
	</form>

</body>
</html>
