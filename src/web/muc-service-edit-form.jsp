<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution, or a commercial license
  - agreement with Jive.
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 java.util.*"
    errorPage="error.jsp"
%>
<%@ page import="java.net.URLEncoder" %>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
   // Handle a cancel
    if (request.getParameter("cancel") != null) {
      response.sendRedirect("muc-service-edit-form.jsp");
      return;
    }
%>

<%-- Define Administration Bean --%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    boolean create = ParamUtils.getBooleanParameter(request,"create");
    boolean save = request.getParameter("save") != null;
    boolean success = request.getParameter("success") != null;
    String mucname = ParamUtils.getParameter(request,"mucname");
    String mucdesc = ParamUtils.getParameter(request,"mucdesc");

    // Load the service object
    if (!create && !webManager.getMultiUserChatManager().isServiceRegistered(mucname)) {
        // The requested service name does not exist so return to the list of the existing rooms
        response.sendRedirect("muc-service-summary.jsp");
        return;
    }

    if (!create && mucdesc == null) {
        mucdesc = webManager.getMultiUserChatManager().getMultiUserChatService(mucname).getDescription();
    }

    // Handle a save
    Map<String,String> errors = new HashMap<String,String>();
    if (save) {
        // Make sure that the MUC Service is lower cased.
        mucname = mucname.toLowerCase();

        // do validation
        if (mucname == null || mucname.indexOf('.') >= 0 || mucname.length() < 1) {
            errors.put("mucname","mucname");
        }
        if (errors.size() == 0) {
            if (!create) {
                webManager.getMultiUserChatManager().updateMultiUserChatService(mucname, mucname, mucdesc);
                // Log the event
                webManager.logEvent("updated MUC service configuration for "+mucname, "name = "+mucname+"\ndescription = "+mucdesc);
                response.sendRedirect("muc-service-edit-form.jsp?success=true&mucname="+mucname);
                return;
            }
            else {
                webManager.getMultiUserChatManager().createMultiUserChatService(mucname, mucdesc, false);
                // Log the event
                webManager.logEvent("created MUC service "+mucname, "name = "+mucname+"\ndescription = "+mucdesc);
                response.sendRedirect("muc-service-edit-form.jsp?success=true&mucname="+mucname);
                return;
            }
        }
    }
%>

<html>
<head>
<title><fmt:message key="groupchat.service.properties.title"/></title>
<% if (create) { %>
<meta name="pageID" content="muc-service-create"/>
<% } else { %>
<meta name="subPageID" content="muc-service-edit-form"/>
<meta name="extraParams" content="<%= "mucname="+URLEncoder.encode(mucname, "UTF-8") %>"/>
<% } %>
<meta name="helpPage" content="edit_group_chat_service_properties.html"/>
</head>
<body>

<p>
<fmt:message key="groupchat.service.properties.introduction" />
</p>

<%  if (success) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
            <fmt:message key="groupchat.service.properties.saved_successfully" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if (errors.size() > 0) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
            <% if (errors.get("mucname") != null) { %>
                <fmt:message key="groupchat.service.properties.error_service_name" />
            <% } %>
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<!-- BEGIN 'Service Name'-->
<form action="muc-service-edit-form.jsp" method="post">
<input type="hidden" name="save" value="true">
<% if (!create) { %>
<input type="hidden" name="mucname" value="<%= mucname %>">
<% } else { %>
<input type="hidden" name="create" value="true" />
<% } %>

    <div class="jive-contentBoxHeader">
		<fmt:message key="groupchat.service.properties.legend" />
	</div>
	<div class="jive-contentBox">
		<table cellpadding="3" cellspacing="0" border="0">
            <tr>
                <td class="c1">
                   <fmt:message key="groupchat.service.properties.label_service_name" />
                </td>
                <td>
                    <% if (create) { %>
                    <input type="text" size="30" maxlength="150" name="mucname" value="<%= (mucname != null ? mucname : "") %>">

                    <%  if (errors.get("mucname") != null) { %>

                    <span class="jive-error-text">
                    <br><fmt:message key="groupchat.service.properties.error_service_name" />
                    </span>

                    <%  } %>
                    <% } else { %>
                    <%= mucname %>
                    <% } %>
                </td>
            </tr>
            <tr>
                <td class="c1">
                   <fmt:message key="groupchat.service.properties.label_service_description" />
                </td>
                <td>
                    <input type="text" size="30" maxlength="150" name="mucdesc" value="<%= (mucdesc != null ? mucdesc : "") %>">
                </td>
            </tr>
        </table>
	</div>
    <input type="submit" value="<fmt:message key="groupchat.service.properties.save" />">
</form>
<!-- END 'Service Name'-->


</body>
</html>