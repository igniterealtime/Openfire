<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004-2005 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.stringprep.Stringprep,
                 org.jivesoftware.util.Log,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.wildfire.group.Group,
                 org.jivesoftware.wildfire.group.GroupAlreadyExistsException,
                 org.jivesoftware.wildfire.user.UserManager,
                 org.xmpp.packet.JID"
    errorPage="error.jsp"
%>
<%@ page import="java.net.URLEncoder"%>
<%@ page import="java.util.HashMap"%>
<%@ page import="java.util.Map"%>
<%@ page import="java.util.StringTokenizer"%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
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

    Map<String, String> errors = new HashMap<String, String>();

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
                            if (username.indexOf('@') == -1) {
                                // No @ was found so assume this is a JID of a local user
                                username = Stringprep.nodeprep(username);
                                UserManager.getInstance().getUser(username);
                                newGroup.getMembers().add(webManager.getXMPPServer().createJID(username, null));
                            }
                            else {
                                // Admin entered a JID. Add the JID directly to the list of group members
                                newGroup.getMembers().add(new JID(username));
                            }
                        }
                        catch (Exception e) {
                            throw new IllegalArgumentException("Invalid user.", e);
                        }
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

<html>
<head>
<title><fmt:message key="group.create.title"/></title>
<meta name="pageID" content="group-create"/>
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

<p>
Use the form below to create your new group. Once you've created the group you will proceed to another
screen where you can add members and set up group contact list.
<!--<fmt:message key="group.create.form" />-->
</p>

<form name="f" action="group-create.jsp" method="post">

	<!-- BEGIN create group -->
	<div class="jive-contentBoxHeader">
		<fmt:message key="group.create.new_group_title" />
	</div>
	<div class="jive-contentBox">
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
		<td></td>
		<td>

			<input type="submit" name="create" value="<fmt:message key="group.create.create" />">
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


</body>
</html>

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