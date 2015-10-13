<%@ page import="org.jivesoftware.openfire.plugin.spark.Bookmark" %>
<%@ page import="org.jivesoftware.openfire.plugin.spark.BookmarkManager" %>
<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ page import="java.util.Collection"%>
<%@ page import="org.xmpp.packet.JID" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%
    final Collection<Bookmark> bookmarks = BookmarkManager.getBookmarks();
%>

<html>
<head>
    <title><fmt:message key="ofmeet.planner.title" /></title>
    <link rel="stylesheet" type="text/css" href="/style/global.css">
    <meta name="pageID" content="ofmeet-planner"/>
    <script src="/js/prototype.js" type="text/javascript"></script>
    <script src="/js/scriptaculous.js" type="text/javascript"></script>
    <style type="text/css">
        .div-border {
            border: 1px solid #CCCCCC;
            -moz-border-radius: 3px;
        }
    </style>
    <style type="text/css">
        @import "stylesheets/style.css";
    </style>
</head>

<body>

<p>
    <fmt:message key="ofmeet.planner.description" />
</p>

<br/>

    <div class="div-border" style="padding: 12px; width: 95%;">
        <table class="jive-table" cellspacing="0" width="100%">
            <th><fmt:message key="ofmeet.planner.name" /></th><th><fmt:message key="ofmeet.planner.address"/></th><th><fmt:message key="ofmeet.planner.users" /></th><th><fmt:message key="ofmeet.planner.groups" /></th>
            <%
                boolean hasBookmarks = false;
                
                for (Bookmark bookmark : bookmarks) 
                {
                    String users = "";
                    String groups = "";
                    
                    if (bookmark.getType() != Bookmark.Type.group_chat) {
                        continue;
                    }
                    else {
                        hasBookmarks = true;
                        if (bookmark.isGlobalBookmark()) {
                            users = "All";
                            groups = "All";
                        }
                        else {
                            users = bookmark.getUsers().size() + " "+ LocaleUtils.getLocalizedString("ofmeet.planner.users", "ofmeet");
                            groups = bookmark.getGroups().size() + " "+LocaleUtils.getLocalizedString("ofmeet.planner.groups", "ofmeet");
                        }
                    }
		    
		    String room = (new JID(bookmark.getValue())).getNode();
		    String roomHtml = "<a href='ofmeet-calendar.jsp?room=" + room + "&name=" + bookmark.getName() + "&id=" + bookmark.getBookmarkID() + "'>" + bookmark.getName() + "</a>";  
		    
		    if (!"All".equals(users))
		    {
            %>
			    <tr style="border-left: none;">
				<td><%= roomHtml%></td>
				<td><%= bookmark.getValue()%></td>
				<td><%= users%></td>
				<td><%= groups%></td>
			    </tr>
            	 <% } %>			    
            <% } %>

            <% if (!hasBookmarks) { %>
            <tr>
                <td colspan="4" align="center"><fmt:message key="ofmeet.planner.none" /></td>
            </tr>
            <%} %>
        </table>
    </div>

</body>
</html>