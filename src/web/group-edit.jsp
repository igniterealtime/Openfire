<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2005 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="java.text.DateFormat,
                 java.util.*,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.admin.*,
                 org.xmpp.packet.JID,
                 org.jivesoftware.messenger.group.GroupManager,
                 org.jivesoftware.messenger.group.Group,
                 java.net.URLEncoder,
                 java.net.URLDecoder,
                 org.jivesoftware.messenger.user.UserManager,
                 org.jivesoftware.messenger.user.UserNotFoundException,
                 org.jivesoftware.stringprep.Stringprep,
                 java.io.UnsupportedEncodingException,
                 org.jivesoftware.util.LocaleUtils"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<!-- Define Administration Bean -->
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
<jsp:useBean id="errors" class="java.util.HashMap"/>

<%  webManager.init(pageContext); %>

<%  // Get parameters
    boolean add = request.getParameter("add") != null;
    boolean delete = request.getParameter("remove") != null;
    boolean update = request.getParameter("save") != null;
    boolean cancel = request.getParameter("cancel") != null;
    String users = ParamUtils.getParameter(request, "users");
    String [] adminIDs = ParamUtils.getParameters(request, "admin");
    String [] deleteMembers = ParamUtils.getParameters(request, "delete");
    String groupName = ParamUtils.getParameter(request, "group");
    GroupManager groupManager = webManager.getGroupManager();
    boolean edit = ParamUtils.getBooleanParameter(request, "edit", false);
    String newName = ParamUtils.getParameter(request, "newName");
    String newDescription = ParamUtils.getParameter(request, "newDescription");
    String newShowInRosterType = ParamUtils.getParameter(request, "newShow");
    boolean newShowInRoster = "onlyGroup".equals(newShowInRosterType) || "everybody".equals(newShowInRosterType);
    String  newDisplayName = ParamUtils.getParameter(request, "newDisplay");
    boolean groupInfoChanged = ParamUtils.getBooleanParameter(request, "groupChanged", false);

    boolean enableRosterGroups = ParamUtils.getBooleanParameter(request,"enableRosterGroups");
    String groupDisplayName = ParamUtils.getParameter(request,"groupDisplayName");
    String showGroup = ParamUtils.getParameter(request,"showGroup");
    String[] groupNames = ParamUtils.getParameters(request, "groupNames");

    edit = true;

    Group group = groupManager.getGroup(groupName);
    boolean success = false;
    StringBuffer errorBuf = new StringBuffer();

    if (cancel) {
        response.sendRedirect("group-summary.jsp");
        return;
    }

    if (newName != null && newName.length() > 0) {
        if (newShowInRoster && (newDisplayName == null || newDisplayName.length() == 0)) {
            errors.put("display", "");
        }
        if (errors.isEmpty()) {
            group.setName(newName);
            group.setDescription(newDescription);

                if (enableRosterGroups) {
                    if ("spefgroups".equals(showGroup)) {
                        showGroup = "onlyGroup";
                    }
                    else {
                        groupNames = new String[] {};
                    }
                    group.getProperties().put("sharedRoster.showInRoster", showGroup);
                    if (groupDisplayName != null) {
                        group.getProperties().put("sharedRoster.displayName", groupDisplayName);
                    }
                    group.getProperties().put("sharedRoster.groupList", toList(groupNames, "UTF-8"));
                }
                else {
                    group.getProperties().put("sharedRoster.showInRoster", "nobody");
                    group.getProperties().put("sharedRoster.displayName", "");
                    group.getProperties().put("sharedRoster.groupList", "");
                }

            groupName = newName;
            groupInfoChanged = true;
             // Get admin list and compare it the admin posted list.
            response.sendRedirect("group-edit.jsp?group=" + URLEncoder.encode(groupName, "UTF-8") + "&groupChanged=true");
            return;
        }
        else {
            // Continue editing since there are some errors
            edit = true;
        }
    }


    if (update) {
        Set adminIDSet = new HashSet();
        for (int i = 0; i < adminIDs.length; i++) {
            String newAdmin = adminIDs[i];
            adminIDSet.add(newAdmin);
            boolean isAlreadyAdmin = group.getAdmins().contains(newAdmin);
            if (!isAlreadyAdmin) {
                // Add new admin
                group.getMembers().remove(newAdmin);
                group.getAdmins().add(newAdmin);
            }
        }
        Iterator groupIter = Collections.unmodifiableCollection(group.getAdmins()).iterator();
        Set removeList = new HashSet();
        while (groupIter.hasNext()) {
            String m = (String) groupIter.next();
            if (!adminIDSet.contains(m)) {
                removeList.add(m);
            }
        }
        Iterator i = removeList.iterator();
        while (i.hasNext()) {
            String m = (String) i.next();
            group.getAdmins().remove(m);
            group.getMembers().add(m);
        }
        // Get admin list and compare it the admin posted list.
        response.sendRedirect("group-edit.jsp?group=" + URLEncoder.encode(groupName, "UTF-8") + "&updatesuccess=true");
        return;
    }
    else if (add && users != null) {
        StringTokenizer tokenizer = new StringTokenizer(users, ",");
        int count = 0;
        while (tokenizer.hasMoreTokens()) {
            String username = tokenizer.nextToken();
            username = username.trim();
            username = username.toLowerCase();

            // Add to group as member by default.
            if (!group.getMembers().contains(username) && !group.getAdmins().contains(username)) {
                // Ensure that the user is valid
                try {
                    group.getMembers().add(username);
                    count++;
                }
                catch (IllegalArgumentException unfe) {
                  errorBuf.append("<br>"+username + " is not a registered user.");
                }
            }
            else {
                errorBuf.append("<br>"+username+" is already in group.");
            }
        }
        if (count > 0) {
            response.sendRedirect("group-edit.jsp?group=" +
                    URLEncoder.encode(groupName, "UTF-8") + "&success=true");
            return;
        }
        else {
            success = false;
            add = true;
        }

    }
    else if(add && users == null){
        add = false;
    }
    else if (delete) {
        for (int i = 0; i < deleteMembers.length; i++) {
            String member = deleteMembers[i];
            group.getMembers().remove(member);
            group.getAdmins().remove(member);
        }
        response.sendRedirect("group-edit.jsp?group=" + URLEncoder.encode(groupName, "UTF-8") + "&deletesuccess=true");
        return;
    }
    success = groupInfoChanged || "true".equals(request.getParameter("success")) ||
            "true".equals(request.getParameter("deletesuccess")) ||
            "true".equals(request.getParameter("updatesuccess")) ||
            "true".equals(request.getParameter("creategroupsuccess"));

    if (errors.size() == 0) {
        enableRosterGroups = !"nobody".equals(group.getProperties().get("sharedRoster.showInRoster"));
        showGroup = group.getProperties().get("sharedRoster.showInRoster");
        if ("onlyGroup".equals(showGroup)) {
            String glist = group.getProperties().get("sharedRoster.groupList");
            List l = new ArrayList();
            if (glist != null) {
                StringTokenizer tokenizer = new StringTokenizer(glist,",");
                while (tokenizer.hasMoreTokens()) {
                    String tok = tokenizer.nextToken().trim();
                    l.add(tok.trim());
                }
            }
            groupNames = (String[])l.toArray(new String[]{});
        }
        groupDisplayName = group.getProperties().get("sharedRoster.displayName"); 
    }
%>
    <jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean"/>
<% // Title of this page and breadcrumbs
    String title = LocaleUtils.getLocalizedString("group.edit.title");
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "group-edit.jsp?group="+URLEncoder.encode(groupName, "UTF-8")));
    pageinfo.setSubPageID("group-edit");
    pageinfo.setExtraParams("group="+URLEncoder.encode(groupName, "UTF-8"));
%>
    <jsp:include page="top.jsp" flush="true"/>
    <jsp:include page="title.jsp" flush="true"/>
    <p>
        <fmt:message key="group.edit.form_info" />
    </p>

<%
    if (success) {
%>
    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        <% if (groupInfoChanged) { %>
        <fmt:message key="group.edit.update" />
        <% } else if ("true".equals(request.getParameter("success"))) { %>
            <fmt:message key="group.edit.update_add_user" />
        <% } else if ("true".equals(request.getParameter("deletesuccess"))) { %>
            <fmt:message key="group.edit.update_del_user" />
        <% } else if ("true".equals(request.getParameter("updatesuccess"))) { %>
            <fmt:message key="group.edit.update_user" />
         <% } else if ("true".equals(request.getParameter("creategroupsuccess"))) { %>
            <fmt:message key="group.edit.update_success" />
        <%
            }
        %>
        </td></tr>
    </tbody>
    </table>
    </div><br>
<%
    }
    else if(!success && add){
%>
 <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        <% if(add) { %>
        <fmt:message key="group.edit.not_update" />
        <%= errorBuf %>
        <% } %>
        </td></tr>
    </tbody>
    </table>
    </div><br>
<% } %>
<form name="ff" action="group-edit.jsp">
<input type="hidden" name="group" value="<%= groupName %>"/>

    <fieldset>
        <legend>
            <fmt:message key="group.edit.group_summary" />
        </legend>

        <table cellpadding="3" cellspacing="1" border="0">
            <tr>
                <td  width="1%" nowrap>
                    <fmt:message key="group.edit.group_name" />
                </td>
                <% if(!edit) { %>
                <td align=left nowrap width="1%">
                    <b><%= group.getName() %></b>
                </td>
                <td>
                    <a href="group-edit.jsp?edit=true&group=<%= URLEncoder.encode(groupName, "UTF-8") %>">
                    <img src="images/edit-16x16.gif" border="0">
                   </a>
                </td>
                <% } else { %>

                <td>
                <input type="text" name="newName" value="<%= group.getName() %>">
                </td>

                <% } %>
            </tr>
            <tr>
                <td width="1%" nowrap>
                    <fmt:message key="group.edit.group_description" />
                </td>
                <% if(!edit) { %>
                <td colspan="2">
                    <%= ((group.getDescription() != null) ? group.getDescription() : "<i>"+LocaleUtils.getLocalizedString("group.edit.group_not_description")+"</i>") %>
                </td>
                <% } else { %>

                <td>
                <textarea name="newDescription" cols="40" rows="4"><%= group.getDescription() != null ? group.getDescription() : "" %></textarea>
                </td>

                <% } %>
            </tr>
            </table>



    <br>
    <p><fmt:message key="group.edit.group_share_title" /></p>

    <p>
    <fmt:message key="group.edit.group_share_content" />
    </p>

    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
                <input type="radio" name="enableRosterGroups" value="false" id="rb201" <%= !enableRosterGroups ? "checked" : "" %>>
            </td>
            <td width="99%">
                <label for="rb201"><fmt:message key="group.edit.group_share_not_in_rosters" /></label>
            </td>
        </tr>
        <tr>
            <td width="1%">
                <input type="radio" name="enableRosterGroups" value="true" id="rb202" <%= enableRosterGroups ? "checked" : "" %>>
            </td>
            <td width="99%">
                <label for="rb202"><fmt:message key="group.edit.group_share_in_rosters" /></label>
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
                            <fmt:message key="group.edit.group_display_name" />
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
                            <input type="radio" name="showGroup" value="everybody" id="rb002"
                             onclick="this.form.enableRosterGroups[1].checked=true;"
                             <%= ("everybody".equals(showGroup) ? "checked" : "") %>>
                        </td>
                        <td width="99%">
                            <label for="rb002"><fmt:message key="group.edit.show_groups_in_all_user" /></label>
                        </td>
                    </tr>
                    <tr>
                        <td width="1%" nowrap>
                            <input type="radio" name="showGroup" value="onlyGroup" id="rb001"
                             onclick="this.form.enableRosterGroups[1].checked=true;"
                             <%= ("onlyGroup".equals(showGroup) && (groupNames == null || groupNames.length == 0) ? "checked" : "") %>>
                        </td>
                        <td width="99%">
                            <label for="rb001"><fmt:message key="group.edit.show_groups_in_groups_members" /></label>
                        </td>
                    </tr>
                    <tr>
                        <td width="1%" nowrap>
                            <input type="radio" name="showGroup" value="spefgroups" id="rb003"
                             onclick="this.form.enableRosterGroups[1].checked=true;"
                             <%= (groupNames != null && groupNames.length > 0) ? "checked" : "" %>>
                        </td>
                        <td width="99%">
                            <label for="rb003"><fmt:message key="group.edit.show_group_in_roster_group" /></label>
                        </td>
                    </tr>
                    <tr>
                        <td width="1%" nowrap>
                            &nbsp;
                        </td>
                        <td width="99%">
                            <select name="groupNames" size="6" onclick="this.form.showGroup[2].checked=true;this.form.enableRosterGroups[1].checked=true;"
                             multiple style="width:300px;font-family:verdana,arial,helvetica,sans-serif;font-size:8pt;">

                            <%  for (Group g : webManager.getGroupManager().getGroups()) { %>

                                <option value="<%= URLEncoder.encode(g.getName(), "UTF-8") %>"
                                 <%= (contains(groupNames, g.getName()) ? "selected" : "") %>
                                 ><%= g.getName() %></option>

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

    </fieldset>
    <br>

    <%  if (edit) { %>
        <input type="submit" name="save" value="<fmt:message key="global.save_settings" />">
        <input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">
    <%  } %>

                    </form>

    <br><br>

    <form action="group-edit.jsp" method="post" name="f">
        <input type="hidden" name="group" value="<%= groupName %>">
        <input type="hidden" name="add" value="Add"/>
        <table cellpadding="3" cellspacing="1" border="0">
            <tr>
                <td nowrap width="1%">
                    <fmt:message key="group.edit.add_user" />
                </td>
                <td nowrap class="c1" align="left">
                    <input type="text" size="40" name="users"/>
                    &nbsp;<input type="submit" name="addbutton" value="<fmt:message key="global.add" />">
                </td>
            </tr>
        </table>
    </form>

    <form action="group-edit.jsp" method="post" name="main">
        <input type="hidden" name="group" value="<%= groupName %>">
        <table class="jive-table" cellpadding="3" cellspacing="0" border="0" width="600">
            <tr>
                <th nowrap><fmt:message key="group.edit.username" /></th>
                <th width="1%" nowrap><fmt:message key="group.edit.admin" /></th>
                <th width="1%" nowrap><fmt:message key="group.edit.remove" /></th>
            </tr>
            <!-- Add admins first -->
<%
            int memberCount = group.getMembers().size() + group.getAdmins().size();
            Iterator members = group.getMembers().iterator();
            Iterator admins = group.getAdmins().iterator();
%>
<%
            if (memberCount == 0) {
%>
                <tr>
                    <td align="center" colspan="3">
                        <br>
                        <fmt:message key="group.edit.user_hint" />
                        <br>
                        <br>
                    </td>
                </tr>
<%
            }
%>
<%
            boolean showUpdateButtons = memberCount > 0;
            while (admins.hasNext()) {
                String username = (String)admins.next();
%>
                <tr>
                    <td><%= username %></td>
                    <td align="center">
                        <input type="checkbox" name="admin" value="<%= username %>" checked>
                    </td>
                    <td align="center">
                        <input type="checkbox" name="delete" value="<%= username %>">
                    </td>
                </tr>
<%
            }
%>
<%
            while (members.hasNext()) {
                String username = (String)members.next();
%>
                <tr>
                    <td><%= username %></td>
                    <td align="center">
                        <input type="checkbox" name="admin" value="<%= username %>">
                    </td>
                    <td align="center">
                        <input type="checkbox" name="delete" value="<%= username %>">
                    </td>
                </tr>
<%
            }
%>
<%
            if (showUpdateButtons) {
%>
                <tr>
                    <td>
                        &nbsp;
                    </td>
                    <td align="center">
                        <input type="submit" name="save" value="Update">
                    </td>
                    <td align="center">
                        <input type="submit" name="remove" value="Remove">
                    </td>
                </tr>
<%
            }
%>
        </table>
        </div>
    </form>

    <script type="text/javascript">
        document.f.users.focus();
    </script>

    <jsp:include page="footer.jsp" flush="true"/>



<%!
    private static String toList(String[] array, String enc) {
        if (array == null || array.length == 0) {
            return "";
        }
        StringBuffer buf = new StringBuffer();
        String sep = "";
        for (int i=0; i<array.length; i++) {
            String item;
            try {
                item = URLDecoder.decode(array[i], enc);
            }
            catch (UnsupportedEncodingException e) {
                item = array[i];
            }
            buf.append(sep).append(item);
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