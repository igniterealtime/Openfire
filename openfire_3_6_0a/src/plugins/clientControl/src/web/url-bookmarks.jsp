<%@ page import="org.jivesoftware.openfire.plugin.spark.Bookmark" %>
<%@ page import="org.jivesoftware.openfire.plugin.spark.BookmarkManager" %>
<%@ page import="org.jivesoftware.openfire.plugin.spark.SparkUtil" %>
<%@ page import="java.util.Collection" %>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    boolean urlBookmarkCreated = request.getParameter("urlCreated") != null;

    boolean delete = request.getParameter("delete") != null;
    final Collection<Bookmark> bookmarks = BookmarkManager.getBookmarks();
%>

<html>
<head>
    <title><fmt:message key="bookmark.url.manager.title" /></title>
    <link rel="stylesheet" type="text/css" href="/style/global.css">
    <meta name="pageID" content="url-bookmarks"/>
    <script src="/js/prototype.js" type="text/javascript"></script>
    <script src="/js/scriptaculous.js" type="text/javascript"></script>
    <style type="text/css">
        .small-label {
            font-size: 11px;
            font-weight: bold;
        }

        .div-border {
            border: 1px solid #CCCCCC;
            -moz-border-radius: 3px;
        }
    </style>
    <style type="text/css">
        @import "style/style.css";
    </style>
</head>

<body>

<p>
    <fmt:message key="bookmark.url.manager.description" />
</p>

<% if (urlBookmarkCreated) { %>
<div class="success">
    <fmt:message key="bookmark.url.success" />
</div>
<%}%>

<% if (delete) { %>
<div class="success">
   <fmt:message key="bookmark.url.deleted" />
</div>
<% } %>
<br/>


    <div class="div-border" style="padding: 12px; width: 95%;">
        <table class="jive-table" cellspacing="0" width="100%">
            <th><fmt:message key="bookmark.url.name" /></th>
            <th><fmt:message key="bookmark.url" /></th>
            <th><fmt:message key="bookmark.url.users" /></th>
            <th><fmt:message key="bookmark.url.groups" /></th>
            <th><fmt:message key="bookmark.url.rss" /></th>
            <th><fmt:message key="bookmark.url.options" /></th>
            <%
                boolean hasBookmarks = false;
                for (Bookmark bookmark : bookmarks) {
                    String users = "";
                    String groups = "";
                    if (bookmark.getType() != Bookmark.Type.url) {
                        continue;
                    }
                    else {
                        hasBookmarks = true;

                        if (bookmark.isGlobalBookmark()) {
                            users = "ALL";
                            groups = "ALL";
                        }
                        else {
                            users = getCommaDelimitedList(bookmark.getUsers(), 5);
                            groups = getCommaDelimitedList(bookmark.getGroups(), 5);
                        }
                    }
            %>
            <tr>
                <td><%= bookmark.getName()%></td>
                <td><%= bookmark.getValue()%></td>
                <td><%= users%></td>
                <td><%= groups%></td>
                <td><%= bookmark.getProperty("rss") != null ? "<img src='/images/check.gif'>" : "&nbsp;"%></td>
                <td>
                    <a href="create-bookmark.jsp?edit=true&type=url&bookmarkID=<%= bookmark.getBookmarkID()%>"><img src="/images/edit-16x16.gif" border="0" width="16" height="16" alt="Edit Bookmark"/></a>
                    <a href="confirm-bookmark-delete.jsp?bookmarkID=<%= bookmark.getBookmarkID()%>"><img src="/images/delete-16x16.gif" border="0" width="16" height="16" alt="Delete Bookmark"/></a>

                </td>
            </tr>
            <% } %>

            <% if (!hasBookmarks) { %>
            <tr>
                <td colspan="6" align="center"><fmt:message key="bookmark.url.no.bookmarks" /></td>
            </tr>
            <%} %>
            <tr>
                <td colspan="6">
                    <a href="create-bookmark.jsp?type=url"><img src="/images/add-16x16.gif" border="0" align="texttop" style="margin-right: 3px;"/><fmt:message key="bookmark.url.add" /></a>
                </td>
            </tr>
        </table>
    </div>

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

        if (!SparkUtil.hasLength(returnStr)) {
            returnStr = "&nbsp;";
        }
        return returnStr;
    }

%>