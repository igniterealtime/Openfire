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
        java.net.URLDecoder"%>
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
    String  users = ParamUtils.getParameter(request, "users");
    String [] adminIDs = ParamUtils.getParameters(request, "admin");
    String [] deleteMembers = ParamUtils.getParameters(request, "delete");
    String  groupName = ParamUtils.getParameter(request, "group");
    GroupManager groupManager = webManager.getGroupManager();
    boolean edit = ParamUtils.getBooleanParameter(request, "edit", false);
    String newName = ParamUtils.getParameter(request, "newName");

    Group   group = groupManager.getGroup(groupName);

    boolean nameChanged = false;
    if(newName != null && newName.length() > 0){
        group.setName(newName);
        groupName = newName;
        nameChanged = true;
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
        Set      removeList = new HashSet();
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
        String hostName = webManager.getXMPPServer().getServerInfo().getName();
        StringTokenizer tokenizer = new StringTokenizer(users, ",");
        while (tokenizer.hasMoreTokens()) {
            String tok = tokenizer.nextToken();
            String address = tok;
            if (address.indexOf("@") == -1) {
                address = address + "@" + hostName;
            }
            // Add To Group as member by default.
            if (!group.getMembers().contains(address) && !group.getAdmins().contains(address)) {
                group.getMembers().add(address);
            }
        }
        response.sendRedirect("group-edit.jsp?group=" + URLEncoder.encode(groupName, "UTF-8") + "&success=true");
        return;
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
    DateFormat dateFormatter = DateFormat.getDateInstance(DateFormat.MEDIUM);
%>
    <jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean"/>
<% // Title of this page and breadcrumbs
    String     title = "Edit Group";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "group-edit.jsp"));
    pageinfo.setPageID("group-summary");
    pageinfo.setExtraParams("group="+groupName);
%>
    <jsp:include page="top.jsp" flush="true"/>
    <jsp:include page="title.jsp" flush="true"/>
    <script language="JavaScript" type="text/javascript">
        function openWin(el) {
            var win = window.open(
                          'user-browser.jsp?formName=f&elName=agents', 'newWin',
                          'width=500,height=550,menubar=yes,location=no,personalbar=no,scrollbars=yes,resize=yes');
        }
    </script>
    <p>
        Below is a summary of properties for the group as well as admins and members. Use the forms on the page to add
        members and optionally designate them as groups administrators.
    </p>
<%
    if (nameChanged){
%>
            <p class="jive-success-text">
            Name has been changed successfully.
            </p>
<%
    }
%>
<%
    if ("true".equals(request.getParameter("success"))) {
%>
            <p class="jive-success-text">
            User(s) added successfully.
            </p>
<%
    }
    else if ("true".equals(request.getParameter("deletesuccess"))) {
%>
            <p class="jive-success-text">
            User(s) deleted successfully.
            </p>
<%
    }
    else if ("true".equals(request.getParameter("updatesuccess"))) {
%>
            <p class="jive-success-text">
            User(s) updated successfully.
            </p>
<%
    }
%>
    <fieldset>
        <legend>
            Group Summary
        </legend><form name="ff" action="group-edit.jsp">
        <input type="hidden" name="group" value="<%= groupName %>"/>
        <table cellpadding="3" cellspacing="1" border="0">
            <tr>
                <td  width="1%">
                    Name:
                </td>
                <td align=left nowrap width="1%">
                    <b><%= group.getName() %></b>
                </td>
                <% if(!edit) { %>
                <td>
                  <a href="group-edit.jsp?edit=true&group=<%= URLEncoder.encode(groupName, "UTF-8") %>">
                    <img src="images/edit-16x16.gif" border="0">
                   </a>
                </td>
                <% }else { %>

                <td>
                New Name:<input type="text" name="newName"><input type="submit" value="Change">
                </td>

                <% } %>
            <tr>
                <td width="1%">
                    Description:
                </td>
                <td colspan="3">
                    <%= ((group.getDescription() != null) ? group.getDescription() : "No Description") %>
                </td>
            </tr>
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
            <td width="1%">
            </td>
            <td nowrap align="left" class="jive-description">
                Comma delimited list. Example: "user1@site.com", "user2@site.com"
            </td>
            </tr>
        </table>
    </form>

    <form action="group-edit.jsp" method="post" name="main">
        <input type="hidden" name="group" value="<%= groupName %>">
        <table class="jive-table" cellpadding="3" cellspacing="0" border="0" width="600">
            <tr>
                <th width="10%">Name</th> <th width="60%">Address</th> <th>Admin</th> <th>Remove</th>
            </tr>
            <!-- Add admins first -->
<%
            int memberCount = group.getCachedSize();
            Iterator members = group.getMembers().iterator();
            Iterator admins = group.getAdmins().iterator();
%>
<%
            if (memberCount == 0) {
%>
                <tr>
                    <td align="center" colspan="4">
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
                String adminName = (String) admins.next();
                JID    adminJID = new JID(adminName);
%>
                <tr>
                    <td><%= adminJID.getNode() %>
                    </td>
                    <td><%= adminName %>
                    </td>
                    <td>
                        <input type="checkbox" name="admin" value="<%= adminName %>" checked>
                    </td>
                    <td align="center">
                        <input type="checkbox" name="delete" value="<%= adminName %>">
                    </td>
                </tr>
<%
            }
%>
<%
            while (members.hasNext()) {
                String member = (String) members.next();
                JID    memberJID = new JID(member);
%>
                <tr>
                    <td><%= memberJID.getNode() %>
                    </td>
                    <td><%= member %>
                    </td>
                    <td>
                        <input type="checkbox" name="admin" value="<%= member %>">
                    </td>
                    <td align="center">
                        <input type="checkbox" name="delete" value="<%= member %>">
                    </td>
                </tr>
<%
            }
%>
<%
            if (showUpdateButtons) {
%>
                <tr>
                    <td colspan="2">
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
