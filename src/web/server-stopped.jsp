<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.admin.AdminConsole,
                 org.jivesoftware.util.LocaleUtils"%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<%  boolean restart = request.getParameter("restart") != null;

    String path = request.getContextPath();

    // Title of this page
    String title = restart ? LocaleUtils.getLocalizedString("server.stopped.title_restarting") : LocaleUtils.getLocalizedString("server.stopped.title_stopped");
    pageinfo.setTitle(title);
%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.0 Transitional//EN">

<html>
<head>
 <title><%= AdminConsole.getAppName() %> <fmt:message key="server.stopped.admin_console" /><%= (pageinfo.getTitle() != null ? (": "+pageinfo.getTitle()) : "") %></title>
 <meta http-equiv="content-type" content="text/html; charset=ISO-8859-1">
 <meta name="decorator" content="false"/>
 <link rel="stylesheet" type="text/css" href="<%= path %>/style/global.css">
</head>

<body>

<div id="jive-header">
<table cellpadding="0" cellspacing="0" width="100%" border="0">
<tbody>
    <tr>
     <td>
         <img src="<%= path %>/<%= AdminConsole.getLogoImage() %>" border="0" alt="<%= AdminConsole.getAppName() %> <fmt:message key="server.stopped.admin_console" />">
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
            <fmt:message key="server.stopped.restarted" />
            </p>

            <ol>
                <li>
                    <fmt:message key="server.stopped.wait_time" />
                </li>
                <li>
                    <a href="index.jsp"><fmt:message key="server.stopped.login_console" /></a>.
                </li>
            </ol>
        <% } else { %>
            <p>
                   <fmt:message key="server.stopped.stop" />
            </p>

            <ol>
                <li>
                    <fmt:message key="server.stopped.wait_restarted" /> <b style="font-size:1.2em;"><fmt:message key="global.restart" /></b> <fmt:message key="server.stopped.wait_restarted2" />
                </li>
                <li>
                    <a href="index.jsp"><fmt:message key="server.stopped.login_console" /></a>.
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


