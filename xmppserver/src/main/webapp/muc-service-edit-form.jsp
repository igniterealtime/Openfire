<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

<%@ page import="org.jivesoftware.util.StringUtils,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.CookieUtils,
                 org.jivesoftware.util.AlreadyExistsException,
                 org.jivesoftware.openfire.muc.spi.MUCPersistenceManager,                 
                 java.util.*"
    errorPage="error.jsp"
%>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="org.xmpp.packet.JID" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

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
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (save) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            save = false;
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

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
        } else {
            try {
                mucname = JID.domainprep(mucname);
            } catch (Exception e) {
                errors.put("mucname", e.getMessage());
            }
        }
        if (errors.size() == 0) {
            // Create or update the service.
            if (!create) {
                webManager.getMultiUserChatManager().updateMultiUserChatService(mucname, mucname, mucdesc);
            }
            else {
                try {
                    webManager.getMultiUserChatManager().createMultiUserChatService(mucname, mucdesc, false);
                }
                catch (IllegalArgumentException e) {
                    errors.put("mucname","mucname");
                }
                catch (AlreadyExistsException e) {
                    errors.put("already_exists","already_exists");
                }
            }

            // Update settings only after the service has been created.
            // TODO move the parsing and validation of these parameters to the section above, that does that for all other parameters.
            String muccleanupdays = ParamUtils.getParameter(request, "muccleanupdays");
            if (muccleanupdays != null) {
                MUCPersistenceManager.setProperty(mucname, "unload.empty_days", muccleanupdays);
            }

            if (ParamUtils.getParameter(request, "muckeep") != null) {
                if (ParamUtils.getParameter(request, "muckeep").equalsIgnoreCase("on")) {
                    MUCPersistenceManager.setProperty(mucname, "unload.empty_days", "0");
                }
            }

            // Log the event
            if (!create) {
                webManager.logEvent("updated MUC service configuration for "+mucname, "name = "+mucname+"\ndescription = "+mucdesc);
                response.sendRedirect("muc-service-edit-form.jsp?success=true&mucname="+mucname);
                return;
            } else {
                webManager.logEvent("created MUC service "+mucname, "name = "+mucname+"\ndescription = "+mucdesc);
                response.sendRedirect("muc-service-edit-form.jsp?success=true&mucname="+mucname);
                return;
            }
        }
    }    
   
    boolean muckeep = false;
    String muccleanupdays = "30"; // default

    // When creating a new service (as opposed to editing an existing one), mucName will be initially empty (OF-1954)
	if (mucname != null) {
        muccleanupdays = MUCPersistenceManager.getProperty(mucname, "unload.empty_days", muccleanupdays);
    }

	if (Integer.parseInt(muccleanupdays)<=0) {
        muckeep = true;
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
<script>
	function checkMUCKeep() {
		var checkedValue = null;
		var inputKeeps = document.getElementsByName('muckeep');
		var inputCleanups = document.getElementsByName('muccleanupdays');

		if (inputKeeps[0].checked) {			
			inputCleanups[0].disabled = true;			
		} else {
			inputCleanups[0].disabled = false;
		}
	}
</script>
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
            <% if (errors.get("already_exists") != null) { %>
                <fmt:message key="groupchat.service.properties.error_already_exists" />
            <% } %>
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<!-- BEGIN 'Service Name'-->
<form action="muc-service-edit-form.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">
<input type="hidden" name="save" value="true">
<% if (!create) { %>
<input type="hidden" name="mucname" value="<%= StringUtils.escapeForXML(mucname) %>">
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
                    <input type="text" size="30" maxlength="150" name="mucname" value="<%= (mucname != null ? StringUtils.escapeForXML(mucname) : "") %>">

                    <%  if (errors.get("mucname") != null) { %>

                    <span class="jive-error-text">
                    <br><fmt:message key="groupchat.service.properties.error_service_name" />
                    </span>

                    <%  } %>
                    <% } else { %>
                    <%= StringUtils.escapeHTMLTags(mucname) %>
                    <% } %>
                </td>
            </tr>
            <tr>
                <td class="c1">
                   <fmt:message key="groupchat.service.properties.label_service_description" />
                </td>
                <td>
                    <input type="text" size="30" maxlength="150" name="mucdesc" value="<%= (mucdesc != null ? StringUtils.escapeForXML(mucdesc) : "") %>">
                </td>
            </tr>
            <tr>
                <td class="c1">
                   <fmt:message key="groupchat.service.properties.label_service_muckeep" />
                </td>
                <td>
                    <input type="checkbox" name="muckeep" <%= muckeep ? "checked" : "" %> onClick="checkMUCKeep();">
                </td>
            </tr>
            <tr>
                <td class="c1">
                   <fmt:message key="groupchat.service.properties.label_service_cleanupdays" />
                </td>
                <td>
                    <input type="number" size="4" maxlength="3" name="muccleanupdays" <%= muckeep ? "disabled" : "" %> value="<%= (muccleanupdays != null ? StringUtils.escapeForXML(muccleanupdays) : "") %>">
                </td>
            </tr>
        </table>
    </div>
    <input type="submit" value="<fmt:message key="groupchat.service.properties.save" />">
</form>
<!-- END 'Service Name'-->


</body>
</html>
