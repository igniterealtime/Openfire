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

<%@ page import="org.jivesoftware.util.*,
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
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
<jsp:useBean id="errors" class="java.util.HashMap"/>

<%  webManager.init(request, response, session, application, out); %>

<%  // Get parameters //
    boolean create = request.getParameter("create") != null;
    boolean cancel = request.getParameter("cancel") != null;
    String name = ParamUtils.getParameter(request, "name");
    String description = ParamUtils.getParameter(request, "description");
    String users = ParamUtils.getParameter(request, "users", true);

    boolean enableRosterGroups = ParamUtils.getBooleanParameter(request,"enableRosterGroups");
    String groupDisplayName = ParamUtils.getParameter(request,"groupDisplayName");
    String showGroup = ParamUtils.getParameter(request,"showGroup");
    String[] groupNames = ParamUtils.getParameters(request, "groupNames");

//    String showInRosterType = ParamUtils.getParameter(request, "show");
//    boolean showInRoster = "onlyGroup".equals(showInRosterType) || "everybody".equals(showInRosterType);
//    String displayName = ParamUtils.getParameter(request, "display");
//    String groupList = ParamUtils.getParameter(request, "groupList");

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
        if (enableRosterGroups) {
            if (groupDisplayName == null) {
                errors.put("groupDisplayName", "");
            }
            if ("spefgroups".equals(showGroup) && groupNames == null) {
                errors.put("groupNames","");
            }
        }
        // do a create if there were no errors
        if (errors.size() == 0) {
            try {
                Group newGroup = webManager.getGroupManager().createGroup(name);
                if (description != null) {
                    newGroup.setDescription(description);
                }
                if ("onlyGroup".equals(showGroup) || "everybody".equals(showGroup)) {
                    newGroup.getProperties().put("sharedRoster.showInRoster", showGroup);
                    newGroup.getProperties().put("sharedRoster.displayName", groupDisplayName);
                    newGroup.getProperties().put("sharedRoster.groupList", toList(groupNames));
                }
                else {
                    newGroup.getProperties().put("sharedRoster.showInRoster", "nobody");
                    newGroup.getProperties().put("sharedRoster.displayName", "");
                    newGroup.getProperties().put("sharedRoster.groupList", "");
                }

                if (users.length() > 0){
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
                response.sendRedirect("group-edit.jsp?creategroupsuccess=true&group=" + URLEncoder.encode(newGroup.getName(), "UTF-8"));
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

<%  if (errors.get("general") != null) { %>

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
    </div><br>

<%  } %>

<p>Use the form below to create a new group.</p>

<form name="f" action="group-create.jsp" method="post">

<fieldset>
    <legend>Create New Group</legend>
    <div>

    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr valign="top">
        <td width="1%" nowrap>
            <label for="gname">Group Name:</label> *
        </td>
        <td width="99%">
            <input type="text" name="name" size="30" maxlength="75"
             value="<%= ((name != null) ? name : "") %>" id="gname">
        </td>
    </tr>

    <%  if (errors.get("name") != null || errors.get("groupAlreadyExists") != null) { %>

        <tr valign="top">
            <td width="1%" nowrap>&nbsp;</td>
            <td width="99%">
                <%  if (errors.get("name") != null) { %>
                    <span class="jive-error-text">Invalid group name.</span>
                <%  } else if (errors.get("groupAlreadyExists") != null) { %>
                    <span class="jive-error-text">Group already exists - please choose a different name.</span>
                <%  } %>
            </td>
        </tr>

    <%  } %>

    <tr valign="top">
        <td width="1%" nowrap>
            <label for="gdesc">Description:</label>
        </td>
        <td width="99%">
            <textarea name="description" cols="30" rows="3" id="gdesc"
             ><%= ((description != null) ? description : "") %></textarea>
        </td>
    </tr>

    <%  if (errors.get("description") != null) { %>

        <tr valign="top">
            <td width="1%" nowrap>
                &nbsp;
            </td>
            <td width="99%">
                <span class="jive-error-text">Invalid description.</span>
            </td>
        </tr>

    <%  } %>

    <tr>
        <td nowrap width="1%" valign="top">
            Initial Member(s):
        </td>
        <td nowrap class="c1" align="left">
            <textarea name="users" cols="30" rows="3" id="gdesc"
             ><%= ((users != null) ? users : "") %></textarea>
        </td>
    </tr>
    </table>

<%--					<tr><td height="15" colspan="3"><img src="images/blank.gif"></td>--%>
<%--                    <tr>--%>
<%--                        <td width="1%" nowrap>--%>
<%--                            <label for="gshow">Show group in rosters for:</label>--%>
<%--                        </td>--%>
<%--                        <td width="99%">--%>
<%--                            <label for="onlyGroup">Only group users</label>--%>
<%--                            <input type="radio" name="show" id="onlyGroup" value="onlyGroup" onclick="refreshDisplayName(this)"/>&nbsp;&nbsp;--%>
<%--                            <label for="everybody">Everybody</label>--%>
<%--                            <input type="radio" name="show" id="everybody" value="everybody" onclick="refreshDisplayName(this)"/>&nbsp;&nbsp;--%>
<%--                            <label for="nobody">Nobody</label>--%>
<%--                            <input type="radio" name="show" id="nobody" value="nobody" checked onclick="refreshDisplayName(this)"/>--%>
<%----%>
<%--                        </td>--%>
<%--                    </tr>--%>
<%--                    <tr>--%>
<%--                        <td nowrap width="1%">--%>
<%--                            <label for="gdisplay">Display name in roster:</label>--%>
<%--                        </td>--%>
<%--                        <td nowrap class="c1" align="left">--%>
<%--                            <input type="text" size="40" name="display" id="gdisplay" disabled/>--%>
<%--<%--%>
<%--                                if (errors.get("display") != null) {--%>
<%--%>--%>
<%--                                                            <span class="jive-error-text"> Please enter a display name. </span>--%>
<%--<%--%>
<%--                                }--%>
<%--%>--%>
<%--                        </td>--%>
<%--                    </tr>--%>
<%--                    <tr>--%>
<%--                        <td nowrap width="1%">--%>
<%--                            <label for="gGroupList">Viewable by groups:</label>--%>
<%--                        </td>--%>
<%--                        <td width="99%">--%>
<%--                            <textarea name="groupList" cols="30" rows="2" id="gGroupList"><%= ((groupList != null) ? groupList : "") %></textarea>--%>
<%--                        </td>--%>
<%--                    </tr>--%>
<%--                </table>--%>
<%----%>
<%--                <br>--%>


    <br>
    <p><b>Shared Roster Groups</b></p>

    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
                <input type="radio" name="enableRosterGroups" value="false" id="rb201" <%= !enableRosterGroups ? "checked" : "" %>>
            </td>
            <td width="99%">
                <label for="rb201">Disable roster groups</label>
            </td>
        </tr>
        <tr>
            <td width="1%">
                <input type="radio" name="enableRosterGroups" value="true" id="rb202" <%= enableRosterGroups ? "checked" : "" %>>
            </td>
            <td width="99%">
                <label for="rb202">Enable roster groups</label>
            </td>
        </tr>
        <tr>
            <td width="1%">
                &nbsp;
            </td>
            <td width="99%">

                <table cellpadding="3" cellspacing="0" border="0" width="100%">
                <tbody>
                    <tr>
                        <td width="1%" nowrap>
                            Group Display Name *
                        </td>
                        <td width="99%">
                            <input type="text" name="groupDisplayName" size="30" maxlength="100" value="<%= (groupDisplayName != null ? groupDisplayName : "") %>"
                             onclick="this.form.enableRosterGroups[1].checked=true;">
                        </td>
                    </tr>
                </tbody>
                </table>

                <table cellpadding="3" cellspacing="0" border="0" width="100%">
                <tbody>
                    <tr>
                        <td width="1%" nowrap>
                            <input type="radio" name="showGroup" value="onlyGroup" checked id="rb001"
                             onclick="this.form.enableRosterGroups[1].checked=true;">
                        </td>
                        <td width="99%">
                            <label for="rb001"
                             >Show group in group members' rosters</label>
                        </td>
                    </tr>
                    <tr>
                        <td width="1%" nowrap>
                            <input type="radio" name="showGroup" value="everybody" id="rb002"
                             onclick="this.form.enableRosterGroups[1].checked=true;">
                        </td>
                        <td width="99%">
                            <label for="rb002"
                             >Show group in all users' rosters.</label>
                        </td>
                    </tr>
                    <tr>
                        <td width="1%" nowrap>
                            <input type="radio" name="showGroup" value="spefgroups" id="rb003"
                             onclick="this.form.enableRosterGroups[1].checked=true;">
                        </td>
                        <td width="99%">
                            <label for="rb003"
                             >Show groups to members of these groups:</label>
                        </td>
                    </tr>
                    <tr>
                        <td width="1%" nowrap>
                            &nbsp;
                        </td>
                        <td width="99%">
                            <select name="groupNames" size="6" onclick="this.form.showGroup[2].checked=true;this.form.enableRosterGroups[1].checked=true;"
                             multiple style="width:300px;font-family:verdana,arial,helvetica,sans-serif;font-size:8pt;">
                            <%
                                Collection<Group> groups = webManager.getGroupManager().getGroups();
                                for (Group group : groups) {
                            %>
                                <option value="<%= java.net.URLEncoder.encode(group.getName(), "UTF-8") %>"
                                 ><%= group.getName() %></option>

                            <%  } %>
                            </select>
                        </td>
                    </tr>
                </tbody>
                </table>

            </td>
        </tr>
    </tbody>
    </table>

    <br>
    <span class="jive-description">* Required fields </span>
    </div>

</fieldset>

<br><br>

<input type="submit" name="create" value="Create Group">
<input type="submit" name="cancel" value="Cancel">

</form>

<script language="JavaScript" type="text/javascript">
document.f.name.focus();
</script>

<jsp:include page="bottom.jsp" flush="true"/>

<%!
    private static String toList(String[] array) {
        if (array == null || array.length == 0) {
            return "";
        }
        StringBuffer buf = new StringBuffer();
        String sep = "";
        for (int i=0; i<array.length; i++) {
            buf.append(sep).append(array[i]);
            sep = ",";
        }
        return buf.toString();
    }
%>