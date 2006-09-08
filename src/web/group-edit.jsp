<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2005 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.stringprep.Stringprep,
                 org.jivesoftware.util.LocaleUtils,
                 org.jivesoftware.util.Log,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.wildfire.PresenceManager,
                 org.jivesoftware.wildfire.group.Group,
                 org.jivesoftware.wildfire.group.GroupManager"
%>
<%@ page import="org.jivesoftware.wildfire.user.User"%>
<%@ page import="org.jivesoftware.wildfire.user.UserManager"%>
<%@ page import="org.jivesoftware.wildfire.user.UserNotFoundException"%>
<%@ page import="org.xmpp.packet.JID"%>
<%@ page import="org.xmpp.packet.Presence"%>
<%@ page import="java.io.UnsupportedEncodingException"%>
<%@ page import="java.net.URLDecoder"%>
<%@ page import="java.net.URLEncoder"%>
<%@ page import="java.util.*"%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<!-- Define Administration Bean -->
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
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
    boolean groupInfoChanged = ParamUtils.getBooleanParameter(request, "groupChanged", false);

    Map<String,String> errors = new HashMap<String,String>();

    // Get the presence manager
    PresenceManager presenceManager = webManager.getPresenceManager();
    UserManager userManager = webManager.getUserManager();

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
        if (enableRosterGroups && (groupDisplayName == null || groupDisplayName.trim().length() == 0)) {
            errors.put("groupDisplayName", "");
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
             // Get admin list and compare it the admin posted list.
            response.sendRedirect("group-edit.jsp?group=" + URLEncoder.encode(groupName, "UTF-8") + "&groupChanged=true");
            return;
        }
        else {
            // Continue editing since there are some errors
            edit = true;
            update = false;
        }
    }


    if (update) {
        Set<JID> adminIDSet = new HashSet<JID>();
        for (String adminID : adminIDs) {
            JID newAdmin = new JID(adminID);
            adminIDSet.add(newAdmin);
            boolean isAlreadyAdmin = group.getAdmins().contains(newAdmin);
            if (!isAlreadyAdmin) {
                // Add new admin
                group.getAdmins().add(newAdmin);
            }
        }
        Collection<JID> admins = Collections.unmodifiableCollection(group.getAdmins());
        Set<JID> removeList = new HashSet<JID>();
        for (JID admin : admins) {
            if (!adminIDSet.contains(admin)) {
                removeList.add(admin);
            }
        }
        for (JID member : removeList) {
            group.getMembers().add(member);
        }
        // Get admin list and compare it the admin posted list.
        response.sendRedirect("group-edit.jsp?group=" + URLEncoder.encode(groupName, "UTF-8") + "&updatesuccess=true");
        return;
    }
    else if (add && users != null) {
        StringTokenizer tokenizer = new StringTokenizer(users, ", \t\n\r\f");
        int count = 0;
        while (tokenizer.hasMoreTokens()) {
            String username = tokenizer.nextToken();
            username = username.trim();
            username = username.toLowerCase();

            // Add to group as member by default.
            try {
                boolean added = false;
                if (username.indexOf('@') == -1) {
                    // No @ was found so assume this is a JID of a local user
                    username = Stringprep.nodeprep(username);
                    UserManager.getInstance().getUser(username);
                    added = group.getMembers().add(webManager.getXMPPServer().createJID(username, null));
                }
                else {
                    // Admin entered a JID. Add the JID directly to the list of group members
                    added = group.getMembers().add(new JID(username));
                }

                if (added) {
                    count++;
                }
                else {
                    errorBuf.append("<br>").append(
                            LocaleUtils.getLocalizedString("group.edit.already_user", Arrays.asList(username)));
                }

            }
            catch (Exception e) {
                Log.debug("Problem adding new user to existing group", e);
                errorBuf.append("<br>").append(
                        LocaleUtils.getLocalizedString("group.edit.inexistent_user", Arrays.asList(username)));
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
        for (String deleteMember : deleteMembers) {
            JID member = new JID(deleteMember);
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
                StringTokenizer tokenizer = new StringTokenizer(glist,",\t\n\r\f");
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

<html>
<head>
<title><fmt:message key="group.edit.title"/></title>
<meta name="subPageID" content="group-edit"/>
<meta name="extraParams" content="<%= "group="+URLEncoder.encode(groupName, "UTF-8") %>"/>
<meta name="helpPage" content="edit_group_properties.html"/>
</head>
<body>

<p>
	<fmt:message key="group.edit.form_info" />
</p>

<p>
	<a href="group-summary.jsp" class="jive-link-back"><span>&laquo;</span> Back to all groups</a>
</p>

<%
    if (success) {
%>
    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
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
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
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

	<div class="jive-horizontalRule"></div>

<form name="ff" action="group-edit.jsp">
<input type="hidden" name="group" value="<%= groupName %>"/>


	<!-- BEGIN group name and description -->
	<div class="jive-contentBox-plain">
		<div class="jive-contentBox-toolbox">
			<a href="group-create.jsp?group=<%= group.getName()%>" class="jive-link-edit">Edit Details</a>
			<a href="group-delete.jsp?group=<%= group.getName()%>" class="jive-link-delete">Delete Group</a>
		</div>

		<h3>
			<%= group.getName() %>
		</h3>
		<p>
			<%= group.getDescription() != null ? group.getDescription() : "" %>
		</p>
    </div>
	<!-- END group name and description -->


	<!-- BEGIN contact list settings -->
	<div class="jive-contentBoxHeader">
		<!--<fmt:message key="group.edit.group_share_title" />-->
		Shared Contact Lists (Rosters)
	</div>
	<div class="jive-contentBox">
		<p>
            <!--<fmt:message key="group.edit.group_share_content" />-->
			You can use the form below to show this group in users' contact lists. <strong>By default</strong>,
			<em>an enabled group roster only appears to members of the group</em>. However, you can share the
			group roster with all users or members of other groups.
        </p>

		<table cellpadding="3" cellspacing="0" border="0">
		<tbody>
		<tr>
            <td width="1%">
                <input type="radio" name="enableRosterGroups" value="false" id="rb201" <%= !enableRosterGroups ? "checked" : "" %> onClick="document.getElementById('jive-roster').style.display = 'none';">
            </td>
            <td width="99%">
                <label for="rb201">Disable contact list group<!--<fmt:message key="group.edit.group_share_not_in_rosters" />--></label>
            </td>
        </tr>
        <tr>
            <td width="1%" valign="top">
                <input type="radio" name="enableRosterGroups" value="true" id="rb202" <%= enableRosterGroups ? "checked" : "" %> onClick="document.getElementById('jive-roster').style.display = 'block';">
            </td>
            <td width="99%">
                <label for="rb202">Enable contact list group<!--<fmt:message key="group.edit.group_share_in_rosters" />--></label>

                <div id="jive-roster" style="display: <%= !enableRosterGroups ? "none" : "block"  %>;">
	               <b>Enter contact list group name<!--<fmt:message key="group.edit.group_display_name" />--></b>
	               <input type="text" name="groupDisplayName" size="30" maxlength="100" value="<%= (groupDisplayName != null ? groupDisplayName : "") %>"><br>
                       <%  if (errors.get("groupDisplayName") != null) { %>
                           <span class="jive-error-text"><fmt:message key="group.create.enter_a_group_name" /></span>
                       <%  } %>
	                   <script type="text/javascript" language="JavaScript">
		                   function toggleRosterShare() {
			                   if (document.getElementById('cb101').checked == false) {
			                       document.getElementById('jive-rosterShare').style.display = 'none';
		                        } else {
				                   document.getElementById('jive-rosterShare').style.display = 'block';
			                   }
		                   }
	                   </script>

	               <input type="checkbox" id="cb101" name="shareContactList" onClick="toggleRosterShare();" style="vertical-align: middle;">
	               <label for="cb101">Share group with additional users</label>
	                    <div id="jive-rosterShare" style="display: <%= !enableRosterGroups ? "none" : "block"  %>;">
		                    <table cellpadding="2" cellspacing="0" border="0" width="100%">
							<tbody>
								<tr>
									<td width="1%" nowrap>
										<input type="radio" name="showGroup" value="everybody" id="rb002"
										 <%= ("everybody".equals(showGroup) || "nobody".equals(showGroup) ? "checked" : "") %>>
									</td>
									<td width="99%">
										<label for="rb002">All users<!--<fmt:message key="group.edit.show_groups_in_all_user" />--></label>
									</td>
								</tr>
								<tr>
									<td width="1%" nowrap>
										<input type="radio" name="showGroup" value="spefgroups" id="rb003"
										 <%= (groupNames != null && groupNames.length > 0) ? "checked" : "" %>>
									</td>
									<td width="99%">
										<label for="rb003">The following groups:<!--<fmt:message key="group.edit.show_group_in_roster_group" />--></label>
									</td>
								</tr>
								<tr>
									<td width="1%" nowrap>
										&nbsp;
									</td>
									<td width="99%">
										<select name="groupNames" size="6" onclick="this.form.showGroup[1].checked=true;"
										 multiple style="width:340px;font-family:verdana,arial,helvetica,sans-serif;font-size:8pt;">

										<%  for (Group g : webManager.getGroupManager().getGroups()) {
											// Do not offer the edited group in the list of groups
											// Members of the editing group can always see each other
											if (g.equals(group)) {
												continue;
											}
										%>

											<option value="<%= URLEncoder.encode(g.getName(), "UTF-8") %>"
											 <%= (contains(groupNames, g.getName()) ? "selected" : "") %>
											 ><%= g.getName() %></option>

										<%  } %>

										</select>
									</td>
								</tr>
							</tbody>
							</table>
		                </div>
                </div>
            </td>
        </tr>
        <tr>
            <td width="1%">
                &nbsp;
            </td>
            <td width="99%">

				<%  if (edit) { %>
	                <input type="submit" name="save" value="Save Contact List Settings">
					<!--<input type="submit" name="save" value="<fmt:message key="global.save_settings" />">-->
					<!--<input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">-->
				<%  } %>

            </td>
        </tr>
    </tbody>
    </table>


	</div>
	<!-- END contact list settings -->


</form>


	<!-- BEGIN group membership management -->
	<div class="jive-contentBoxHeader">
		Members of This Group
	</div>
	<div class="jive-contentBox">
		<p>
			Use the form below to add users to this group. Once added, you will be able to remove them,
			or give certain users administrative rights over the group.
		</p>

		<form action="group-edit.jsp" method="post" name="f">
        <input type="hidden" name="group" value="<%= groupName %>">
        <input type="hidden" name="add" value="Add"/>
        <table cellpadding="3" cellspacing="1" border="0" style="margin: 0px 0px 8px 0px;">
            <tr>
                <td nowrap width="1%">
                    <fmt:message key="group.edit.add_user" />
                </td>
                <td nowrap class="c1" align="left">
                    <input type="text" size="45" name="users"/>
                    &nbsp;<input type="submit" name="addbutton" value="<fmt:message key="global.add" />">
                </td>
            </tr>
        </table>
        </form>

		<form action="group-edit.jsp" method="post" name="main">
        <input type="hidden" name="group" value="<%= groupName %>">
        <table class="jive-table" cellpadding="3" cellspacing="0" border="0" width="435">
            <tr>
	            <th>&nbsp;</th>
                <th nowrap><fmt:message key="group.edit.username" /></th>
                <th width="1%" nowrap class="jive-table-th-center"><fmt:message key="group.edit.admin" /></th>
                <th width="1%" nowrap class="jive-table-th-center"><fmt:message key="group.edit.remove" /></th>
            </tr>
            <!-- Add admins first -->
<%
            int memberCount = group.getMembers().size() + group.getAdmins().size();
            boolean showUpdateButtons = memberCount > 0;
            boolean showRemoteJIDsWarning = false;
            if (memberCount == 0) {
%>
                <tr>
                    <td align="center" colspan="4">
                        <br>
                        <fmt:message key="group.edit.user_hint" />
                        <br>
                        <br>
                    </td>
                </tr>
<%
            }
            else {
                // Sort the list of members.
                ArrayList<JID> allMembers = new ArrayList<JID>(memberCount);
                allMembers.addAll(group.getMembers());
                Collection<JID> admins = group.getAdmins();
                allMembers.addAll(admins);
                Collections.sort(allMembers);
                for (JID jid:allMembers) {
                    boolean isLocal = webManager.getXMPPServer().isLocal(jid);
                    User user = null;
                    if (isLocal) {
                        try {
                            user = userManager.getUser(jid.getNode());
                        }
                        catch (UserNotFoundException unfe) {
                            // Ignore.
                        }
                    }
%>
                <tr>
                    <td width="1%">
                    <%  if (user != null && presenceManager.isAvailable(user)) {
                            Presence presence = presenceManager.getPresence(user);
                    %>
                    <% if (presence.getShow() == null) { %>
                    <img src="images/im_available.gif" width="16" height="16" border="0" title="<fmt:message key="user.properties.available" />" alt="<fmt:message key="user.properties.available" />">
                    <% } %>
                    <% if (presence.getShow() == Presence.Show.chat) { %>
                    <img src="images/im_free_chat.gif" width="16" height="16" border="0" title="<fmt:message key="session.details.chat_available" />" alt="<fmt:message key="session.details.chat_available" />">
                    <% } %>
                    <% if (presence.getShow() == Presence.Show.away) { %>
                    <img src="images/im_away.gif" width="16" height="16" border="0" title="<fmt:message key="session.details.away" />" alt="<fmt:message key="session.details.away" />">
                    <% } %>
                    <% if (presence.getShow() == Presence.Show.xa) { %>
                    <img src="images/im_away.gif" width="16" height="16" border="0" title="<fmt:message key="session.details.extended" />" alt="<fmt:message key="session.details.extended" />">
                    <% } %>
                    <% if (presence.getShow() == Presence.Show.dnd) { %>
                    <img src="images/im_dnd.gif" width="16" height="16" border="0" title="<fmt:message key="session.details.not_disturb" />" alt="<fmt:message key="session.details.not_disturb" />">
                    <% } %>

                    <%  } else { %>
                    <img src="images/im_unavailable.gif" width="16" height="16" border="0" title="<fmt:message key="user.properties.offline" />" alt="<fmt:message key="user.properties.offline" />">
                    <%  } %>

                    </td>
                    <% if (user != null) { %>
                    <td><a href="user-properties.jsp?username=<%= URLEncoder.encode(user.getUsername(), "UTF-8") %>"><%= user.getUsername() %></a><% if (!isLocal) { showRemoteJIDsWarning = true; %> <font color="red"><b>*</b></font><%}%></td>
                    <% } else { %>
                    <td><%= jid %><% showRemoteJIDsWarning = true; %> <font color="red"><b>*</b></font></td>
                    <% } %>
                    <td align="center">
                        <input type="checkbox" name="admin" value="<%= jid %>" <% if (admins.contains(jid)) { %>checked<% } %>>
                    </td>
                    <td align="center">
                        <input type="checkbox" name="delete" value="<%= jid %>">
                    </td>
                </tr>
<%
                }
            }
            if (showUpdateButtons) {
%>
                <tr>
                    <td colspan="2">
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

            if (showRemoteJIDsWarning) {
%>
            <tr>
                <td colspan="4">
                    <font color="red">* <fmt:message key="group.edit.note" /></font>
                </td>
            </tr>
<%
            }
%>
        </table>
        </form>

    <script type="text/javascript">
        document.f.users.focus();
    </script>

	</div>
	<!-- END group membership management -->



</body>
</html>


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