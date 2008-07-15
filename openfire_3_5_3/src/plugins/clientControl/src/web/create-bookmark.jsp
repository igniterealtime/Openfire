<%@ page import="org.jivesoftware.openfire.plugin.spark.Bookmark" %>
<%@ page import="org.jivesoftware.openfire.plugin.spark.SparkUtil" %>
<%@ page import="org.jivesoftware.util.Log" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.muc.MUCRoom" %>
<%@ page import="org.jivesoftware.openfire.muc.MultiUserChatServer" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.StringTokenizer" %>
<%@ page import="org.jivesoftware.util.NotFoundException"%>
<%@ page import="org.jivesoftware.util.LocaleUtils"%>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%
    boolean urlType = false;
    boolean groupchatType = false;

    String type = request.getParameter("type");
    if ("url".equals(type)) {
        urlType = true;
    }
    else {
        groupchatType = true;
    }

    boolean edit = request.getParameter("edit") != null;
    String bookmarkID = request.getParameter("bookmarkID");

    Bookmark editBookmark = null;
    if (edit && bookmarkID != null) {
        try {
            editBookmark = new Bookmark(Long.parseLong(bookmarkID));
        }
        catch (NotFoundException e) {
            Log.error(e);
        }
    }

    Map<String,String> errors = new HashMap<String,String>();
    String groupchatName = request.getParameter("groupchatName");
    String groupchatJID = request.getParameter("groupchatJID");

    boolean autojoin = ParamUtils.getBooleanParameter(request,"autojoin");

    String users = request.getParameter("users");
    String groups = request.getParameter("groups");


    String url = request.getParameter("url");
    String urlName = request.getParameter("urlName");

    boolean isRSS = ParamUtils.getBooleanParameter(request, "rss", false);

    boolean allUsers = ParamUtils.getBooleanParameter(request,"all");

    boolean createGroupchat = request.getParameter("createGroupchatBookmark") != null;
    boolean createURLBookmark = request.getParameter("createURLBookmark") != null;


    boolean submit = false;
    if (createGroupchat || createURLBookmark) {
        submit = true;
    }

    if (submit && createURLBookmark) {
        if (!SparkUtil.hasLength(url)) {
            errors.put("url", LocaleUtils.getLocalizedString("bookmark.url.error", "clientcontrol"));
        }

        if (!SparkUtil.hasLength(urlName)) {
            errors.put("urlName", LocaleUtils.getLocalizedString("bookmark.urlName.error", "clientcontrol"));
        }
    }
    else if (submit && createGroupchat) {
        if (!SparkUtil.hasLength(groupchatName)) {
            errors.put("groupchatName", LocaleUtils.getLocalizedString("bookmark.groupchat.name.error", "clientcontrol"));
        }

        if (!SparkUtil.hasLength(groupchatJID) || !groupchatJID.contains("@")) {
            errors.put("groupchatJID", LocaleUtils.getLocalizedString("bookmark.groupchat.address.error", "clientcontrol"));
        }
    }

    if (!submit && errors.size() == 0) {
        if (editBookmark != null) {
            if (editBookmark.getType() == Bookmark.Type.url) {
                url = editBookmark.getProperty("url");
                urlName = editBookmark.getName();
            }
            else {
                groupchatName = editBookmark.getName();
                autojoin = editBookmark.getProperty("autojoin") != null;
                groupchatJID = editBookmark.getValue();
            }

            users = getCommaDelimitedList(editBookmark.getUsers());
            groups = getCommaDelimitedList(editBookmark.getGroups());
            allUsers = editBookmark.isGlobalBookmark();
            isRSS = editBookmark.getProperty("rss") != null;
        }
        else {
            groupchatName = "";
            groupchatJID = "";
            url = "";
            urlName = "";
            users = "";
            groups = "";
        }
    }
    else {
        if ((createURLBookmark || createGroupchat) && errors.size() == 0) {
            Bookmark bookmark = null;

            if (bookmarkID == null) {
                if (createURLBookmark)
                    bookmark = new Bookmark(Bookmark.Type.url, urlName, url);

                if (createGroupchat) {
                    bookmark = new Bookmark(Bookmark.Type.group_chat, groupchatName, groupchatJID);
                }
            }
            else {
                try {
                    bookmark = new Bookmark(Long.parseLong(bookmarkID));
                }
                catch (NotFoundException e) {
                    Log.error(e);
                }
                if (createURLBookmark) {
                    bookmark.setName(urlName);
                    bookmark.setValue(url);
                }
                else {
                    bookmark.setName(groupchatName);
                    bookmark.setValue(groupchatJID);
                }
            }

            List<String> userCollection = new ArrayList<String>();
            List<String> groupCollection = new ArrayList<String>();
            if (users != null) {
                StringTokenizer tkn = new StringTokenizer(users, ",");
                while (tkn.hasMoreTokens()) {
                    userCollection.add(tkn.nextToken());
                }

                bookmark.setUsers(userCollection);
            }

            if (groups != null) {
                StringTokenizer tkn = new StringTokenizer(groups, ",");
                while (tkn.hasMoreTokens()) {
                    groupCollection.add(tkn.nextToken());
                }

                bookmark.setGroups(groupCollection);
            }

            if (allUsers) {
                bookmark.setGlobalBookmark(true);
            }
            else {
                bookmark.setGlobalBookmark(false);
            }

            if (createURLBookmark) {
                if (url != null) {
                    bookmark.setProperty("url", url);
                }

                if (isRSS) {
                    bookmark.setProperty("rss", "true");
                }
		else {
	            bookmark.deleteProperty("rss");
		}
            }
            else {
                if (autojoin) {
                    bookmark.setProperty("autojoin", "true");
                }
		else {
	            bookmark.deleteProperty("autojoin");
		}
            }
        }
    }

    if (submit && errors.size() == 0) {
        if (createURLBookmark) {
            response.sendRedirect("url-bookmarks.jsp?urlCreated=true");
            return;
        }
        else if (createGroupchat) {
            response.sendRedirect("groupchat-bookmarks.jsp?groupchatCreated=true");
        }
    }

    String description = LocaleUtils.getLocalizedString("bookmark.url.create.description", "clientcontrol");
    if (groupchatType) {
        description = LocaleUtils.getLocalizedString("bookmark.groupchat.create.description", "clientcontrol");
        if(edit){
            description = LocaleUtils.getLocalizedString("bookmark.groupchat.edit.description", "clientcontrol");
        }
    }
    else if(edit){
        description = LocaleUtils.getLocalizedString("bookmark.url.edit.description", "clientcontrol");
    }

%>


<html>
<head>
    <title><%= editBookmark != null ? LocaleUtils.getLocalizedString("bookmark.edit", "clientcontrol") : LocaleUtils.getLocalizedString("bookmark.create", "clientcontrol")%></title>
    <link rel="stylesheet" type="text/css" href="/style/global.css">
    <meta name="pageID" content="<%= groupchatType ? "groupchat-bookmarks" : "url-bookmarks"%>"/>
    <script src="/js/prototype.js" type="text/javascript"></script>
    <script src="/js/scriptaculous.js" type="text/javascript"></script>
    <script type="text/javascript">
        function toggleAllElement(ele, users, groups) {
            users.disabled = ele.checked;
            groups.disabled = ele.checked;
        }

        function showPicker() {
            alert("Not implemented!");
        }

        function validateForms(form) {
            form.users.disabled = form.all.checked;
            form.groups.disabled = form.all.checked;
        }
    </script>
    <style type="text/css">
        .div-border {
            border: 1px;
            border-color: #ccc;
            border-style: dotted;
        }
    </style>
    <style type="text/css">
        @import "style/style.css";
    </style>
</head>

<body>

<!-- Create URL Bookmark -->
<p>
    <%= description%>
</p>


<% if (submit && errors.size() == 0 && createURLBookmark) { %>
<div class="success">
   <fmt:message key="bookmark.created" />
</div>
<% } %>


<% if (urlType) { %>
<form id="urlForm" name="urlForm" action="create-bookmark.jsp" method="post">
    <table class="div-border" cellpadding="3">
        <tr valign="top">
            <td><b><fmt:message key="bookmark.url.name" />:</b></td>
            <td><input type="text" name="urlName" size="30" value="<%=urlName %>"/><br/>
                <% if (errors.get("urlName") != null) { %>
                <span class="jive-error-text"><%= errors.get("urlName")%><br/></span>
                <% } %>
                <span class="jive-description"><fmt:message key="bookmark.url.name.description" /></span></td>

        </tr>
        <tr valign="top">
            <td><b><fmt:message key="bookmark.url" />:</b></td>
            <td><input type="text" name="url" size="30" value="<%=url %>"/><br/>
                <% if (errors.get("url") != null) { %>
                <span class="jive-error-text"><%= errors.get("url")%><br/></span>
                <% } %>
                <span class="jive-description">eg. http://www.acme.com</span></td>
        </tr>
        <tr valign="top">
            <td><b><fmt:message key="users" />:</b></td>
            <td><input type="text" name="users" size="30" value="<%= users%>"/><br/>
                <span class="jive-error-text"></span></td>
            <!--
            <td><img src="images/icon_browse_14x13.gif"/></td><td><a href="javascript:showPicker();"><fmt:message key="bookmark.browse.users" /></a></td>-->
            <td><input type="checkbox" name="all" <%= allUsers ? "checked" : "" %> onclick="toggleAllElement(this, document.urlForm.users, document.urlForm.groups);"/>All Users</td>
        </tr>

        <tr valign="top">
            <td><b><fmt:message key="groups" />:</b></td>
            <td><input type="text" name="groups" size="30" value="<%= groups %>"/><br/><span
                class="jive-error-text"></span></td><!--
            <td><img src="images/icon_browse_14x13.gif"/></td><td><a href="javascript:showPicker();"><fmt:message key="bookmark.browse.groups" /></a></td>-->
        </tr>
        <% if (errors.get("noUsersOrGroups") != null) { %>
        <tr>
            <td colspan="2" class="jive-error-text"><fmt:message key="bookmark.users.groups.error" /></td>
        </tr>
        <% } %>
        <tr><td><b><fmt:message key="bookmark.create.rss.feed" /></b></td><td><input type="checkbox" name="rss" <%= isRSS ? "checked" : "" %>/></td></tr>

        <tr><td></td><td><input type="submit" name="createURLBookmark"
                                value="<%= editBookmark != null ? LocaleUtils.getLocalizedString("bookmark.save.changes", "clientcontrol") : LocaleUtils.getLocalizedString("create", "clientcontrol")  %>"/>
            &nbsp;<input type="button" value="<fmt:message key="cancel" />"
                         onclick="window.location.href='url-bookmarks.jsp'; return false;">
        </td>
        </tr>

    </table>
    <input type="hidden" name="type" value="url"/>
    <% if (editBookmark != null) { %>
    <input type="hidden" name="bookmarkID" value="<%= editBookmark.getBookmarkID()%>"/>
    <input type="hidden" name="edit" value="true" />
    <% } %>

<script type="text/javascript">
   validateForms(document.urlForm);
</script>
</form>

<% }
else { %>

<form name="f" id="f" action="create-bookmark.jsp" method="post">

    <table class="div-border" cellpadding="3">
        <tr valign="top">
            <td><b><fmt:message key="group.chat.bookmark.name" />:</b></td>
            <td colspan="3"><input type="text" name="groupchatName" size="40" value="<%= groupchatName %>"/><br/>
                <% if (errors.get("groupchatName") != null) { %>
                <span class="jive-error-text"><%= errors.get("groupchatName")%><br/></span>
                <% } %>
                <span class="jive-description">eg. Discussion Room</span></td>
        </tr>
        <tr valign="top">
            <td><b><fmt:message key="group.chat.bookmark.address" />:</b></td>
            <td colspan="3"><input type="text" name="groupchatJID" size="40" value="<%= groupchatJID %>"/><br/>
                <% if (errors.get("groupchatJID") != null) { %>
                <span class="jive-error-text"><%= errors.get("groupchatJID")%><br/></span>
                <% } %>
                <span class="jive-description">eg. myroom@conference.example.com</span></td>
        </tr>

        <tr valign="top">
            <td><b><fmt:message key="users" />:</b></td>
            <td><input type="text" name="users" size="30" value="<%= users%>"/><br/>
                <span class="jive-error-text"></span></td>
            <!--
            <td><img src="images/icon_browse_14x13.gif"/></td><td><a href="javascript:showPicker();"><fmt:message key="bookmark.browse.users" /></a></td>-->
            <td><input type="checkbox" name="all" <%= allUsers ? "checked" : "" %> onclick="toggleAllElement(this, document.f.users, document.f.groups);"/><fmt:message key="bookmark.create.all.users" /></td>
        </tr>

        <tr valign="top">
            <td><b><fmt:message key="groups" />:</b></td>
            <td><input type="text" name="groups" size="30" value="<%= groups %>"/><br/><span
                class="jive-error-text"></span></td>
            <!--
            <td><img src="images/icon_browse_14x13.gif"/></td><td><a href="javascript:showPicker();"><fmt:message key="bookmark.browse.groups" /></a></td>-->
        </tr>
        <tr>
            <td><b><fmt:message key="group.chat.bookmark.autojoin" />:</b></td><td><input type="checkbox" name="autojoin" <%= autojoin ? "checked" : "" %>/></td>
        </tr>
        <tr>
            <td></td>
            <td><input type="submit" name="createGroupchatBookmark"  value="<%= editBookmark != null ? LocaleUtils.getLocalizedString("bookmark.save.changes", "clientcontrol") : LocaleUtils.getLocalizedString("create", "clientcontrol")  %>"/>&nbsp;
                <input type="button" value="Cancel" onclick="window.location.href='groupchat-bookmarks.jsp'; return false;">
            </td>
        </tr>

    </table>
    <input type="hidden" name="type" value="groupchat"/>
    <% if (editBookmark != null) { %>
    <input type="hidden" name="bookmarkID" value="<%= editBookmark.getBookmarkID()%>"/>
    <input type="hidden" name="edit" value="true" />
    <% } %>

<script type="text/javascript">
    validateForms(document.f);
</script>
</form>

<% } %>



</body>
</html>

<%!
    /**
     * A more elegant string representing all users that this bookmark
     * "belongs" to.
     *
     * @return the string.
     */
    public String getCommaDelimitedList(Collection<String> strings) {
        StringBuilder buf = new StringBuilder();
        for (String string : strings) {
            buf.append(string);
            buf.append(",");
        }

        String returnStr = buf.toString();
        if (returnStr.endsWith(",")) {
            returnStr = returnStr.substring(0, returnStr.length() - 1);
        }


        return returnStr;
    }

    public String getJidFromRoomName(String roomName) {
        // Load the MultiUserChatServer
        XMPPServer xmppServer = XMPPServer.getInstance();
        MultiUserChatServer mucServer = xmppServer.getMultiUserChatServer();
        MUCRoom room = null;
        try {
            room = mucServer.getChatRoom(roomName);
        }
        catch (Exception e) {
            Log.error(e);

            return null;
        }

        return "";//Todo. Return actual name. Checking in for file transfer.
    }
%>
