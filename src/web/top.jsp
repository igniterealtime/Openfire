<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.messenger.container.ServiceLookup,
                 org.jivesoftware.messenger.container.ServiceLookupFactory,
                 org.jivesoftware.messenger.container.Container,
                 org.jivesoftware.util.StringUtils,
                 org.jivesoftware.admin.AdminConsole"
    errorPage="error.jsp"
%>

<%@ taglib uri="admin" prefix="admin" %>

<jsp:useBean id="info" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out); %>

<%  String path = request.getContextPath(); %>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.0 Transitional//EN">

<html>
<head>
    <title><%= AdminConsole.getAppName() %> Admin Console<%= (info.getTitle() != null ? (": "+info.getTitle()) : "") %></title>
    <meta http-equiv="content-type" content="text/html; charset=ISO-8859-1">
    <link rel="stylesheet" type="text/css" href="<%= path %>/style/global.css">
</head>

<body id="jive-body">

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
                <td>
                    <%--
                    <a href="#" onclick="helpwin();return false;"
                     ><img src="images/header-help.gif" width="24" height="24" border="0" alt="Click for help" hspace="10"></a>
                    --%>
                    &nbsp;
                </td>
                <td class="info">
                    <nobr><%= AdminConsole.getAppName() %> <%= AdminConsole.getVersionString() %></nobr>
                </td>
            </tr>
            </table>
        </td>
    </tr>
    <tr>
        <td colspan="3">
            <table cellpadding="0" cellspacing="0" border="0" width="100%">
            <tbody>
                <tr>
                    <td width="99%" nowrap>
                        <div id="jive-tabs">

                            <admin:tabs css="" currentcss="currentlink" bean="pageinfo">
                                <a href="[url]" title="[description]"
                                 onmouseover="self.status='[description]';return true;" onmouseout="self.status='';return true;"
                                 >[name]</a>
                            </admin:tabs>

                        </div>
                    </td>
                    <td width="1%" id="jive-logout" nowrap>
                        <a href="<%= path %>/index.jsp?logout=true">Logout [<%= StringUtils.escapeHTMLTags(admin.getUser().getUsername()) %>]</a>
                        &nbsp;&nbsp;&nbsp;
                    </td>
                </tr>
            </tbody>
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

                <admin:sidebar css="" currentcss="currentlink" headercss="category" bean="pageinfo">
                    <a href="[url]" title="[description]"
                      onmouseover="self.status='[description]';return true;" onmouseout="self.status='';return true;"
                      >[name]</a>
                     <admin:subsidebar css="" currentcss="currentlink">
                        <a href="[url]" title="[description]"
                         onmouseover="self.status='[description]';return true;" onmouseout="self.status='';return true;"
                         >[name]</a>
                     </admin:subsidebar>
                </admin:sidebar>

                <br>
                <img src="<%= path %>/images/blank.gif" width="150" height="1" border="0" alt="">

            </div>
        </td>
        <td width="99%" id="jive-content">

<%  out.flush(); %>