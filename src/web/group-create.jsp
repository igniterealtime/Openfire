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

<%@ page import="org.jivesoftware.openfire.group.Group,
                 org.jivesoftware.openfire.group.GroupAlreadyExistsException,
                 org.jivesoftware.openfire.security.SecurityAuditManager,
                 org.jivesoftware.util.StringUtils,
                 org.jivesoftware.util.Log"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.util.ParamUtils"%>
<%@ page import="org.jivesoftware.util.CookieUtils"%>
<%@ page import="java.net.URLEncoder"%>
<%@ page import="java.util.HashMap"%>
<%@ page import="java.util.Map" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<%  webManager.init(request, response, session, application, out); %>

<%  // Get parameters //
    String groupName = ParamUtils.getParameter(request, "group");

    boolean create = request.getParameter("create") != null;
    boolean edit = request.getParameter("edit") != null;
    boolean cancel = request.getParameter("cancel") != null;
    String name = ParamUtils.getParameter(request, "name");
    String description = ParamUtils.getParameter(request, "description", true);
    
    Map<String, String> errors = new HashMap<String, String>();
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (create || edit) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            create = false;
            edit = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    // Handle a cancel
    if (cancel) {
        if (groupName == null) {
            response.sendRedirect("group-summary.jsp");
        }
        else {
            response.sendRedirect("group-edit.jsp?group=" + URLEncoder.encode(groupName, "UTF-8"));    
        }
        return;
    }
    // Handle a request to create a group:
    if (create) {
        // Validate
        if (name == null) {
            errors.put("name", "");
        }
        // do a create if there were no errors
        if (errors.size() == 0) {
            try {
                Group newGroup = webManager.getGroupManager().createGroup(name);
                if (description != null) {
                    newGroup.setDescription(description);
                }

                if (!SecurityAuditManager.getSecurityAuditProvider().blockGroupEvents()) {
                    // Log the event
                    webManager.logEvent("created new group "+name, "description = "+description);
                }

                // Successful, so redirect
                response.sendRedirect("group-edit.jsp?creategroupsuccess=true&group=" + URLEncoder.encode(newGroup.getName(), "UTF-8"));
                return;
            }
            catch (GroupAlreadyExistsException e) {
                errors.put("groupAlreadyExists", "");
            }
            catch (Exception e) {
                errors.put("general", "");
                Log.error(e);
            }
        }
    }
    // Handle a request to edit a group:
    if (edit) {
        // Validate
        if (name == null) {
            errors.put("name", "");
        }
        // do a create if there were no errors
        if (errors.size() == 0) {
            try {
                Group group = webManager.getGroupManager().getGroup(groupName);
                group.setName(name);
                if (description != null) {
                    group.setDescription(description);
                }

                if (!SecurityAuditManager.getSecurityAuditProvider().blockGroupEvents()) {
                    // Log the event
                    webManager.logEvent("edited group "+groupName, "description = "+description);
                }

                // Successful, so redirect
                response.sendRedirect("group-edit.jsp?groupChanged=true&group=" + URLEncoder.encode(group.getName(), "UTF-8"));
                return;
            }
            catch (Exception e) {
                errors.put("general", "");
                Log.error(e);
            }
        }
    }
%>

<html>
<head>
<title><%
           // If editing the group.
           if (groupName != null) {
        %>
        <fmt:message key="group.edit.title" />
        <% }
           // Otherwise creating a new group.
           else {
        %>
        <fmt:message key="group.create.title" />
        <% } %>
</title>

<% if (groupName == null) { %>
<meta name="pageID" content="group-create"/>
<% }
   else { %>
<meta name="subPageID" content="group-edit"/>
<meta name="extraParams" content="<%= "group="+URLEncoder.encode(groupName, "UTF-8") %>"/>
<% } %>
    
<meta name="helpPage" content="create_a_group.html"/>
</head>
<body>

<c:set var="submit" value="${param.create}"/>

<%  if (errors.get("general") != null) { %>
    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon">
                <img src="images/error-16x16.gif" width="16" height="16" border="0" alt="">
            </td>
            <td class="jive-icon-label">
                <fmt:message key="group.create.error" />
            </td>
        </tr>
    </tbody>
    </table>
    </div><br>
<%  } %>

<% if (webManager.getGroupManager().isReadOnly()) { %>
<div class="error">
    <fmt:message key="group.read_only"/>
</div>
<% } %>

<p>
    <%
        // If editing the group.
        if (groupName != null) {
    %>
    <fmt:message key="group.edit.details_info" />
    <% }
       // Otherwise creating a new group.
       else {
    %>
    <fmt:message key="group.create.form" />
    <% } %>
</p>

<form name="f" action="group-create.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">

   <% if (groupName != null) { %>
    <input type="hidden" name="group" value="<%= StringUtils.escapeForXML(groupName) %>" id="existingName">
   <% } %>

    <!-- BEGIN create group -->
    <div class="jive-contentBoxHeader">
        <%
            // If editing the group.
            if (groupName != null) {
        %>
        <fmt:message key="group.edit.title" />
        <% }
           // Otherwise creating a new group.
           else {
        %>
        <fmt:message key="group.create.new_group_title" />
        <% } %>
    </div>
    <div class="jive-contentBox">
        <table cellpadding="3" cellspacing="0" border="0">
    <tr valign="top">
        <td width="1%" nowrap>
            <label for="gname"><fmt:message key="group.create.group_name" /></label> *
        </td>
        <td width="99%">
            <input type="text" name="name" size="30" maxlength="75"
             value="<%= ((name != null) ? StringUtils.escapeForXML(name) : "") %>" id="gname">
        </td>
    </tr>

    <%  if (errors.get("name") != null || errors.get("groupAlreadyExists") != null) { %>

        <tr valign="top">
            <td width="1%" nowrap>&nbsp;</td>
            <td width="99%">
                <%  if (errors.get("name") != null) { %>
                    <span class="jive-error-text"><fmt:message key="group.create.invalid_group_name" /></span>
                <%  } else if (errors.get("groupAlreadyExists") != null) { %>
                    <span class="jive-error-text"><fmt:message key="group.create.invalid_group_info" /></span>
                <%  } %>
            </td>
        </tr>

    <%  } %>

    <tr valign="top">
        <td width="1%" nowrap>
            <label for="gdesc"><fmt:message key="group.create.label_description" /></label>
        </td>
        <td width="99%">
            <textarea name="description" cols="30" rows="3" id="gdesc"
             ><%= ((description != null) ? StringUtils.escapeHTMLTags(description) : "") %></textarea>
        </td>
    </tr>

    <%  if (errors.get("description") != null) { %>

        <tr valign="top">
            <td width="1%" nowrap>
                &nbsp;
            </td>
            <td width="99%">
                <span class="jive-error-text"><fmt:message key="group.create.invalid_description" /></span>
            </td>
        </tr>

    <%  } %>

    <tr>
        <td></td>
        <td>
            <%
               // If editing the group.
               if (groupName != null) {
            %>
            <input type="submit" name="edit" value="<fmt:message key="group.edit.title" />">
            <% }
               // Otherwise creating a new group.
               else {
            %>
            <input type="submit" name="create" value="<fmt:message key="group.create.create" />">
            <% } %>
            <input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">
        </td>
    </tr>
    </table>
    </div>
    <span class="jive-description">* <fmt:message key="group.create.required_fields" /> </span>
    <!-- END create group -->

</form>

<script language="JavaScript" type="text/javascript">
document.f.name.focus();
</script>

<%  // Disable the form if a read-only user provider.
if (webManager.getGroupManager().isReadOnly()) { %>

<script language="Javascript" type="text/javascript">
  function disable() {
var limit = document.forms[0].elements.length;
for (i=0;i<limit;i++) {
  document.forms[0].elements[i].disabled = true;
}
  }
  disable();
</script>
<% } %>

</body>
</html>%>
