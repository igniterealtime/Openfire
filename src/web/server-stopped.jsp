<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.admin.AdminConsole"%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<%  boolean restart = request.getParameter("restart") != null;

    String path = request.getContextPath();

    // Title of this page
    String title = restart ? "Restarting Server" : "Server stopped";
    pageinfo.setTitle(title);
%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.0 Transitional//EN">

<html>
<head>
 <title><%= AdminConsole.getAppName() %> Admin Console<%= (pageinfo.getTitle() != null ? (": "+pageinfo.getTitle()) : "") %></title>
 <meta http-equiv="content-type" content="text/html; charset=ISO-8859-1">
 <link rel="stylesheet" type="text/css" href="<%= path %>/style/global.css">
</head>

<body>

<div id="jive-header">
<table cellpadding="0" cellspacing="0" width="100%" border="0">
<tbody>
    <tr>
     <td>
         <img src="<%= path %>/<%= AdminConsole.getLogoImage() %>" border="0" alt="<%= AdminConsole.getAppName() %> Admin Console">
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

        <jsp:include page="title.jsp" flush="true" />

        <% if (restart) { %>
            <p>
            The server is being restarted.
            To continue:
            </p>

            <ol>
                <li>
                    Wait a few seconds until the server has been restarted.
                </li>
                <li>
                    <a href="index.jsp">Login to the admin console</a>.
                </li>
            </ol>
        <% } else { %>
            <p>
            The server is being stopped.
            To continue:
            </p>

            <ol>
                <li>
                    Wait a few seconds and then <b style="font-size:1.2em;">restart</b> the server.
                </li>
                <li>
                    <a href="index.jsp">Login to the admin console</a>.
                </li>
            </ol>
        <% } %>

        </td>
    </tr>
</tbody>
</table>
</div>

</body>
</html>

<%  // Flush all the contents before the server is stopped
    out.flush();

    if (restart) {
        admin.restart();
    }
    else {
        admin.stop();
    }
%>


