<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>
<%@ page
import   ="org.jivesoftware.util.*,
                 java.util.HashMap,
                 java.util.Map,
                 java.util.*,
                 org.jivesoftware.messenger.*,
                 org.jivesoftware.admin.*,
                 java.io.StringWriter,
                 java.io.StringWriter,
                 java.io.IOException,
                 org.jivesoftware.messenger.auth.UnauthorizedException,
                 java.io.PrintStream,
                 org.dom4j.xpath.DefaultXPath,
                 org.dom4j.*,
                 org.jivesoftware.messenger.group.*,
           java.net.URLEncoder,
           org.jivesoftware.messenger.user.UserManager,
           org.jivesoftware.messenger.user.UserNotFoundException"
errorPage="error.jsp"%>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
<jsp:useBean id="errors" class="java.util.HashMap"/>
<%
    webManager.init(request, response, session, application, out);
%>
<% // Get parameters //
    boolean create = request.getParameter("create") != null;
    boolean cancel = request.getParameter("cancel") != null;
    String  name = ParamUtils.getParameter(request, "name");
    String  description = ParamUtils.getParameter(request, "description");
    boolean showInRoster = ParamUtils.getBooleanParameter(request, "show", false);
    String  displayName = ParamUtils.getParameter(request, "display");
    String  users = ParamUtils.getParameter(request, "users", true);
    // Handle a cancel
    if (cancel) {
        response.sendRedirect("group-summary.jsp");
        return;
    }
    // Handle a request to create a group:
    if (create) {
        // Validate
        if (name == null) {
            errors.put("name", "");
        }
        if (showInRoster && (displayName == null || displayName.length() == 0)) {
            errors.put("display", "");
        }
        // do a create if there were no errors
        if (errors.size() == 0) {
            try {
                Group newGroup = webManager.getGroupManager().createGroup(name);
                if (description != null) {
                    newGroup.setDescription(description);
                }
                if (showInRoster) {
                    newGroup.getProperties().put("showInRoster", "true");
                    newGroup.getProperties().put("displayName", displayName);
                }
                else {
                    newGroup.getProperties().put("showInRoster", "false");
                    newGroup.getProperties().put("displayName", "");
                }

                if(users.length() > 0){
                    StringTokenizer tokenizer = new StringTokenizer(users, ",");
                    while (tokenizer.hasMoreTokens()) {
                        String username = tokenizer.nextToken();
                        try {
                            UserManager.getInstance().getUser(username);
                            newGroup.getMembers().add(username);
                        }
                        catch (UserNotFoundException unfe) { }
                    }
                }
                // Successful, so redirect
                response.sendRedirect("group-properties.jsp?success=true&group=" + URLEncoder.encode(newGroup.getName(), "UTF-8"));
                return;
            }
            catch (GroupAlreadyExistsException e) {
                e.printStackTrace();
                errors.put("groupAlreadyExists", "");
            }
            catch (Exception e) {
                e.printStackTrace();
                errors.put("general", "");
                Log.error(e);
            }
        }
    }
%>
    <jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean"/>
<% // Title of this page and breadcrumbs
    String title = "Create Group";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "group-create.jsp"));
    pageinfo.setPageID("group-create");
%>
    <jsp:include page="top.jsp" flush="true"/>
    <jsp:include page="title.jsp" flush="true"/>
    <c:set var="submit" value="${param.create}"/>
    <c:set var="errors" value="${errors}"/>

    <script language="javascript">
    <!--
    function refreshDisplayName(showCheck)
    {
        document.forms.f.display.disabled=!showCheck.checked;
    }
    function setDisplayName()
    {
        if (document.forms.f.display.value == "") {
            document.forms.f.display.value=document.forms.f.name.value;
        }
    }
    //-->
    </script>

<%
    if (errors.get("general") != null) {
%>
        <div class="jive-success">
            <table cellpadding="0" cellspacing="0" border="0">
                <tbody>
                    <tr>
                        <td class="jive-icon">
                            <img src="images/success-16x16.gif" width="16" height="16" border="0">
                        </td>
                        <td class="jive-icon-label">
                            Error creating the group. Please check your error logs.
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
        <br>
<%
    }
%>
    <p>
        Use the form below to create a new group.</p>
    <form name="f" action="group-create.jsp" method="post">
        <fieldset>
            <legend>
                Create New Group
            </legend>
            <div>
                <table cellpadding="3" cellspacing="0" border="0" width="100%">
                    <tr valign="top">
                        <td width="1%" nowrap>
                            <label for="gname">Group Name:</label>
                            *
                        </td>
                        <td width="99%">
                            <input type="text"                                 name="name" size="30" maxlength="75"
                                   value="<%= ((name != null) ? name : "") %>" id="gname" onChange="setDisplayName()">
<%
                            if (errors.get("name") != null) {
%>
                                <span class="jive-error-text"> Invalid name. </span>
<%
                            }
                            else if (errors.get("groupAlreadyExists") != null) {
%>
                                <span class="jive-error-text"> Group already exists - please choose a different
                                name. </span>
<%
                            }
%>
                        </td>
                    </tr>
                    <tr valign="top">
                        <td width="1%" nowrap>
                            <label for="gdesc">Description:</label>
                        </td>
                        <td width="99%">
                            <textarea name="description" cols="30" rows="5" id="gdesc"><%= ((description != null) ? description : "") %></textarea>
<%
                                if (errors.get("description") != null) {
%>
                                                            <span class="jive-error-text"> Invalid description. </span>
<%
                                }
%>
                        </td>
                    </tr>
                    <tr>
                        <td nowrap width="1%">
                            Initial Member(s):
                        </td>
                        <td nowrap class="c1" align="left">
                            <input type="text" size="40" name="users"/>
                            &nbsp;
                        </td>
                    </tr>
					<tr><td height="15" colspan="3"><img src="images/blank.gif"></td>
                    <tr>
                        <td width="1%" nowrap>
                            <label for="gshow">Show group in group members' rosters:</label>
                        </td>
                        <td width="99%">
                            <input type="checkbox" name="show" value="true" id="gshow" onclick="refreshDisplayName(this)"/>
                        </td>
                    </tr>
                    <tr>
                        <td nowrap width="1%">
                            <label for="gdisplay">Display name in roster:</label>
                        </td>
                        <td nowrap class="c1" align="left">
                            <input type="text" size="40" name="display" id="gdisplay" disabled/>
<%
                                if (errors.get("display") != null) {
%>
                                                            <span class="jive-error-text"> Please enter a display name. </span>
<%
                                }
%>
                        </td>
                    </tr>
                </table>
                <br>
                <span class="jive-description"> * Required fields </span>
            </div>
        </fieldset>
        <br>
        <br>
        <input type="submit" name="create" value="Create Group"> <input type="submit" name="cancel" value="Cancel">
    </form>
    <script language="JavaScript" type="text/javascript">
        document.f.name.focus();
        function checkFields() {
        }
    </script>
    <jsp:include page="bottom.jsp" flush="true"/>
