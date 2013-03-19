<%@ page errorPage="/error.jsp" import="org.jivesoftware.openfire.plugin.ClientControlPlugin" %>
<%@ page import="org.jivesoftware.openfire.plugin.spark.Bookmark" %>
<%@ page import="org.jivesoftware.openfire.plugin.spark.BookmarkManager" %>
<%@ page import="org.jivesoftware.util.Log" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.muc.MUCRoom" %>
<%@ page import="org.jivesoftware.openfire.muc.MultiUserChatService" %>
<%@ page import="java.util.Collection" %>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>


<%
    String bookmarkID = request.getParameter("bookmarkID");

    Bookmark bookmark = new Bookmark(Long.parseLong(bookmarkID));

    boolean delete = request.getParameter("delete") != null;

    if (delete && bookmarkID != null) {
        BookmarkManager.deleteBookmark(Long.parseLong(bookmarkID));

        if(bookmark.getType() == Bookmark.Type.group_chat){
            response.sendRedirect("groupchat-bookmarks.jsp?delete=true");
        }
        else {
            response.sendRedirect("url-bookmarks.jsp?delete=true");
        }
        return;
    }
%>


<html>
<head>
    <title><fmt:message key="bookmark.delete.confirm" /></title>
    <meta name="pageID" content="<%= bookmark.getType() == Bookmark.Type.group_chat ? "groupchat-bookmarks" : "url-bookmarks"%>"/>
    <script type="text/javascript">
    </script>
    <style type="text/css">

        .field-label {
            font-size: 11px;
            font-weight: bold;
        }

        .field-text {
            font-size: 12px;
            font-family: verdana;
        }

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
    <fmt:message key="bookmark.delete.confirm.prompt" />
</p>


<% if (bookmark.getType() == Bookmark.Type.url) { %>
<form name="urlForm" action="confirm-bookmark-delete.jsp" method="post">
    <table class="div-border">
        <tr valign="top">
            <td><b><fmt:message key="bookmark.delete.url.urlname" /></b></td>
            <td><%= bookmark.getName()%>
        </tr>
        <tr valign="top">
            <td><b><fmt:message key="bookmark.delete.url.url" /></b></td>
            <td><%= bookmark.getValue()%></td>
        </tr>
        <tr valign="top">
            <td><b><fmt:message key="bookmark.delete.url.users" /></b></td>
            <td><%= bookmark.getUsers()%>
        </tr>
        <tr valign="top">
            <td><b><fmt:message key="bookmark.delete.url.groups" /></b></td>
            <td><%= bookmark.getGroups()%>
        </tr>
        <tr><td></td>
            <td>
                <input type="submit" name="delete" value="<fmt:message key="bookmark.delete.url.submit" />"/>&nbsp;
                <input type="button" value="<fmt:message key="bookmark.delete.url.cancel" />"
                       onclick="window.location.href='url-bookmarks.jsp'; return false;">
            </td>
        </tr>

    </table>
    <input type="hidden" name="bookmarkID" value="<%= bookmarkID%>"/>
</form>

<% }
else { %>

<form name="f" action="confirm-bookmark-delete.jsp" method="post">

    <table class="div-border" width="50%">
        <tr valign="top">
            <td><b><fmt:message key="bookmark.delete.chat.groupname" /></b></td>
            <td class="field-text"><%= bookmark.getName()%></td>
        </tr>
        <tr valign="top">
            <td><b><fmt:message key="bookmark.delete.chat.address" /></b></td>
            <td class="field-text"><%= bookmark.getValue()%>
        </tr>
        <tr valign="top">
            <td><b><fmt:message key="bookmark.delete.chat.users" /></b></td>
            <td class="field-text"><%= bookmark.isGlobalBookmark() ? "ALL" : getCommaDelimitedList(bookmark.getUsers(), 5)%></td>
        </tr>

        <tr valign="top">
            <td><b><fmt:message key="bookmark.delete.chat.groups" /></b></td>
            <td class="field-text"><%= bookmark.isGlobalBookmark() ? "ALL" : getCommaDelimitedList(bookmark.getGroups(), 5) %></td>
        </tr>
        <tr>
            <td><b><fmt:message key="bookmark.delete.chat.autojoin" /></b></td>
            <td><%= bookmark.getProperty("autojoin") != null ? "<img src='/images/check.gif'>" : "&nbsp;"%></td>
        </tr>
        <tr>
            <td></td>
            <td>
                <input type="submit" name="delete" value="<fmt:message key="bookmark.delete.chat.submit" />">
                <input type="button" value="<fmt:message key="bookmark.delete.chat.cancel" />"
                       onclick="window.location.href='groupchat-bookmarks.jsp'; return false;">
        </td>
        </tr>

    </table>
    <input type="hidden" name="bookmarkID" value="<%= bookmarkID%>"/>
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
    public String getCommaDelimitedList(Collection<String> strings, int limit) {
        int counter = 0;
        StringBuilder buf = new StringBuilder();
        for (String string : strings) {
            buf.append(string);
            buf.append(",");
            counter++;
            if (counter >= limit) {
                break;
            }
        }

        String returnStr = buf.toString();
        if (returnStr.endsWith(",")) {
            returnStr = returnStr.substring(0, returnStr.length() - 1);
        }
        return returnStr;
    }

%>