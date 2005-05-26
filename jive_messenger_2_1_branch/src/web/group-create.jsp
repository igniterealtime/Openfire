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
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<jsp:useBean id="errors" class="java.util.HashMap" />

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
            if ("spefgroups".equals(showGroup) && (groupNames == null || groupNames.length == 0)) {
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
                if (enableRosterGroups) {
                    if ("spefgroups".equals(showGroup)) {
                        showGroup = "onlyGroup";
                    }
                    newGroup.getProperties().put("sharedRoster.showInRoster", showGroup);
                    if (groupDisplayName != null) {
                        newGroup.getProperties().put("sharedRoster.displayName", groupDisplayName);
                    }
                    newGroup.getProperties().put("sharedRoster.groupList", toList(groupNames));
                }
                else {
                    newGroup.getProperties().put("sharedRoster.showInRoster", "nobody");
                    newGroup.getProperties().put("sharedRoster.displayName", "");
                    newGroup.getProperties().put("sharedRoster.groupList", "");
                }

                if (users.length() > 0){
                    StringTokenizer tokenizer = new StringTokenizer(users, ", \t\n\r\f");
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
                errors.put("groupAlreadyExists", "");
            }
            catch (Exception e) {
                errors.put("general", "");
                Log.error(e);
            }
        }
    }

    if (errors.size() == 0) {
        showGroup = "everybody";
    }
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean"/>

<% // Title of this page and breadcrumbs
    String title = LocaleUtils.getLocalizedString("group.create.title");
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(LocaleUtils.getLocalizedString("global.main"), "index.jsp"));
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
                <fmt:message key="group.create.error" />
            </td>
        </tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<p>
<fmt:message key="group.create.form" />
</p>

<form name="f" action="group-create.jsp" method="post">

<fieldset>
    <legend><fmt:message key="group.create.new_group_title" /></legend>
    <div>

    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr valign="top">
        <td width="1%" nowrap>
            <label for="gname"><fmt:message key="group.create.group_name" /></label> *
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
             ><%= ((description != null) ? description : "") %></textarea>
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
        <td nowrap width="1%" valign="top">
            <fmt:message key="group.create.label_initial_member" />
        </td>
        <td nowrap class="c1" align="left">
            <textarea name="users" cols="30" rows="3" id="gdesc"
             ><%= ((users != null) ? users : "") %></textarea>
        </td>
    </tr>
    </table>

    <br>
    <p><b><fmt:message key="group.create.share_groups_title" /></b></p>

    <p>
    <fmt:message key="group.create.share_groups_info" />
    </p>

    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
                <input type="radio" name="enableRosterGroups" value="false" id="rb201" <%= !enableRosterGroups ? "checked" : "" %>>
            </td>
            <td width="99%">
                <label for="rb201"><fmt:message key="group.create.disable_share_group" /></label>
            </td>
        </tr>
        <tr>
            <td width="1%">
                <input type="radio" name="enableRosterGroups" value="true" id="rb202" <%= enableRosterGroups ? "checked" : "" %>>
            </td>
            <td width="99%">
                <label for="rb202"><fmt:message key="group.create.enable_share_group" /></label>
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
                            <fmt:message key="group.create.group_display_name" />
                        </td>
                        <td width="99%">
                            <input type="text" name="groupDisplayName" size="30" maxlength="100" value="<%= (groupDisplayName != null ? groupDisplayName : "") %>"
                             onclick="this.form.enableRosterGroups[1].checked=true;">

                            <%  if (errors.get("groupDisplayName") != null) { %>

                                    <span class="jive-error-text"><fmt:message key="group.create.enter_a_group_name" /></span>

                            <%  } %>
                        </td>
                    </tr>
                </tbody>
                </table>

                <table cellpadding="3" cellspacing="0" border="0" width="100%">
                <tbody>
                    <tr>
                        <td width="1%" nowrap>
                            <input type="radio" name="showGroup" value="everybody" id="rb002"
                             onclick="this.form.enableRosterGroups[1].checked=true;"
                             <%= ("everybody".equals(showGroup) ? "checked" : "") %>>
                        </td>
                        <td width="99%">
                            <label for="rb002"><fmt:message key="group.create.show_group_in_all_users" /></label>
                        </td>
                    </tr>
                    <tr>
                        <td width="1%" nowrap>
                            <input type="radio" name="showGroup" value="onlyGroup" id="rb001"
                             onclick="this.form.enableRosterGroups[1].checked=true;"
                             <%= ("onlyGroup".equals(showGroup) && (groupNames == null || groupNames.length == 0) ? "checked" : "") %>>
                        </td>
                        <td width="99%">
                            <label for="rb001"><fmt:message key="group.create.show_group_in_group_members" /></label>
                        </td>
                    </tr>
                    <tr>
                        <td width="1%" nowrap>
                            <input type="radio" name="showGroup" value="spefgroups" id="rb003"
                             onclick="this.form.enableRosterGroups[1].checked=true;"
                             <%= (groupNames != null && groupNames.length > 0) ? "checked" : "" %>>
                        </td>
                        <td width="99%">
                            <label for="rb003"><fmt:message key="group.create.show_group_in_roster_group" /></label>
                        </td>
                    </tr>
                    <tr>
                        <td width="1%" nowrap>
                            &nbsp;
                        </td>
                        <td width="99%">
                            <select name="groupNames" size="6" onclick="this.form.showGroup[2].checked=true;this.form.enableRosterGroups[1].checked=true;"
                             multiple style="width:300px;font-family:verdana,arial,helvetica,sans-serif;font-size:8pt;">

                            <%  for (Group group : webManager.getGroupManager().getGroups()) { %>

                                <option value="<%= URLEncoder.encode(group.getName(), "UTF-8") %>"
                                 <%= (contains(groupNames, group.getName()) ? "selected" : "") %>
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
    <span class="jive-description">* <fmt:message key="group.create.required_fields" /> </span>
    </div>

</fieldset>

<br><br>

<input type="submit" name="create" value="<fmt:message key="group.create.create" />">
<input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">

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

    private static boolean contains(String[] array, String item) {
        if (array == null || array.length == 0 || item == null) {
            return false;
        }
        for (int i=0; i<array.length; i++) {
            if (item.equals(array[i])) {
                return true;
            }
        }
        return false;
    }
%>