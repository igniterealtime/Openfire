<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<%@ page
import="java.text.DateFormat,
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
        org.jivesoftware.stringprep.Stringprep"%>
<!-- Define Administration Bean -->
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
<%
    webManager.init(pageContext);
%>
<%
    // Get parameters
    boolean add = request.getParameter("add") != null;
    boolean delete = request.getParameter("remove") != null;
    boolean update = request.getParameter("update") != null;
    String users = ParamUtils.getParameter(request, "users");
    String [] adminIDs = ParamUtils.getParameters(request, "admin");
    String [] deleteMembers = ParamUtils.getParameters(request, "delete");
    String groupName = ParamUtils.getParameter(request, "group");
    GroupManager groupManager = webManager.getGroupManager();
    boolean edit = ParamUtils.getBooleanParameter(request, "edit", false);
    String newName = ParamUtils.getParameter(request, "newName");
    String newDescription = ParamUtils.getParameter(request, "newDescription");
    boolean groupInfoChanged = ParamUtils.getBooleanParameter(request, "groupChanged", false);

    Group group = groupManager.getGroup(groupName);
    boolean success = false;
    StringBuffer errorBuf = new StringBuffer();


    if (newName != null && newName.length() > 0) {
        group.setName(newName);
        group.setDescription(newDescription);
        groupName = newName;
        groupInfoChanged = true;
         // Get admin list and compare it the admin posted list.
        response.sendRedirect("group-edit.jsp?group=" + URLEncoder.encode(groupName, "UTF-8") + "&groupChanged=true");
        return;
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
            "true".equals(request.getParameter("updatesuccess"));
%>
    <jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean"/>
<% // Title of this page and breadcrumbs
    String title = "Edit Group";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "group-edit.jsp"));
    pageinfo.setPageID("group-summary");
    pageinfo.setExtraParams("group="+groupName);
%>
    <jsp:include page="top.jsp" flush="true"/>
    <jsp:include page="title.jsp" flush="true"/>
    <p>
        Edit group settings and add or remove group members and administrators
        using the forms below.
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
        Group information updated successfully.
        <% } else if ("true".equals(request.getParameter("success"))) { %>
            User(s) added successfully.
        <% } else if ("true".equals(request.getParameter("deletesuccess"))) { %>
            User(s) deleted successfully.
        <% } else if ("true".equals(request.getParameter("updatesuccess"))) { %>
            User(s) updated successfully.
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
        User(s) not added successfully.
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
            Group Summary
        </legend>

        <table cellpadding="3" cellspacing="1" border="0">
            <tr>
                <td  width="1%">
                    Name:
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
                <td width="1%">
                    Description:
                </td>
                <% if(!edit) { %>
                <td colspan="3">
                    <%= ((group.getDescription() != null) ? group.getDescription() : "<i>No Description</i>") %>
                </td>
                <% } else { %>

                <td>
                <input type="text" name="newDescription" value="<%= group.getDescription() != null ? group.getDescription() : "" %>">
                </td>

                <% } %>
            </tr>
            <% if(edit) { %>
            <tr>
            <td colspan="3">
            <input type="submit" value="Change">
            </td>
            </tr>
            <% } %>
        </table>

    </fieldset> </form>
    <br>

    <form action="group-edit.jsp" method="post" name="f">
        <input type="hidden" name="group" value="<%= groupName %>">
        <input type="hidden" name="add" value="Add"/>
        <table cellpadding="3" cellspacing="1" border="0">
            <tr>
                <td nowrap width="1%">
                    Add User(s):
                </td>
                <td nowrap class="c1" align="left">
                    <input type="text" size="40" name="users"/>
                    &nbsp;<input type="submit" name="addbutton" value="Add">
                </td>
            </tr>
        </table>
    </form>

    <form action="group-edit.jsp" method="post" name="main">
        <input type="hidden" name="group" value="<%= groupName %>">
        <table class="jive-table" cellpadding="3" cellspacing="0" border="0" width="600">
            <tr>
                <th>Username</th><th width="1%">Admin</th><th width="1%">Remove</th>
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
                        No members in this group. Use the form above to add some.
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
                        <input type="submit" name="update" value="Update">
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
    <jsp:include page="footer.jsp" flush="true"/>
    <script>
        document.f.users.focus();
    </script>
