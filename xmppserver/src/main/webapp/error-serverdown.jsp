<%@ page contentType="text/html; charset=UTF-8" %>
 <%--
--%>
<%@ page import="org.jivesoftware.admin.AdminConsole,
                 org.jivesoftware.util.LocaleUtils"%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<%  String path = request.getContextPath();

    // Title of this page
    String title = AdminConsole.getAppName() + " " +LocaleUtils.getLocalizedString("error.serverdown.title");
    pageinfo.setTitle(title);
%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.0 Transitional//EN">

<html>
<head>
 <title><%= AdminConsole.getAppName() %> <fmt:message key="error.serverdown.admin_console" /><%= (pageinfo.getTitle() != null ? (": "+pageinfo.getTitle()) : "") %></title>
 <meta http-equiv="content-type" content="text/html; charset=UTF-8">
 <link rel="stylesheet" type="text/css" href="<%= path %>/style/global.css">
</head>

<body>

<div id="jive-header">
<table cellpadding="0" cellspacing="0" width="100%" border="0">
<tbody>
    <tr>
     <td>
         <img src="<%= path %>/<%= AdminConsole.getLogoImage() %>" border="0" alt="<%= AdminConsole.getAppName() %> <fmt:message key="error.serverdown.admin_console" />">
     </td>
     <td align="right">
         <table cellpadding="0" cellspacing="0" border="0">
         <tr>
             <td>&nbsp;</td>
             <td class="info">
                 <nobr><%= AdminConsole.getAppName() %> <%= AdminConsole.getVersionString() %></nobr>
             </td>
         </tr>
         </table>
     </td>
    </tr>
</tbody>
</table>
</div>

<div id="jive-main">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tbody>
    <tr valign="top">
        <td width="1%">
            <div id="jive-sidebar">
                <img src="<%= path %>/images/blank.gif" width="5" height="1" border="0" alt="">
            </div>
        </td>
        <td width="99%" id="jive-content">

        <div id="jive-title">
            <%= title %>
        </div>

        <p>
        <%= AdminConsole.getAppName() %> <fmt:message key="error.serverdown.is_down" />
        </p>

        <ol>
            <li>
                <fmt:message key="error.serverdown.start" />
            </li>
            <li>
                <a href="index.jsp"><fmt:message key="error.serverdown.login" /></a>.
            </li>
        </ol>

        </td>
    </tr>
</tbody>
</table>
</div>

</body>
</html>
