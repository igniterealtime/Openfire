<%--
  -	$Revision: 2701 $
  -	$Date: 2005-08-19 16:48:22 -0700 (Fri, 19 Aug 2005) $
  -
  - Copyright (C) 2004-2005 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.StringUtils,
                 org.jivesoftware.admin.AdminConsole,
                 org.jivesoftware.util.LocaleUtils"
    errorPage="../error.jsp"
%>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%@ taglib uri="http://www.opensymphony.com/sitemesh/decorator" prefix="decorator" %>
<%@ taglib uri="http://www.opensymphony.com/sitemesh/page" prefix="page" %>

<jsp:useBean id="info" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out); %>

<decorator:usePage id="decoratedPage" />

<%
    String path = request.getContextPath();
    // Decorated pages will typically must set a pageID and optionally set a subPageID
    // and extraParams. Store these values as request attributes so that the tab and sidebar
    // handling tags can get at the data.
    request.setAttribute("pageID", decoratedPage.getProperty("meta.pageID"));
    request.setAttribute("subPageID", decoratedPage.getProperty("meta.subPageID"));
    request.setAttribute("extraParams", decoratedPage.getProperty("meta.extraParams"));
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.0 Transitional//EN">

<html>
<head>
    <title><%= AdminConsole.getAppName() %> <fmt:message key="login.title" />: <decorator:title /></title>
    <meta http-equiv="content-type" content="text/html; charset=ISO-8859-1">
    <link rel="stylesheet" type="text/css" href="<%= path %>/style/global.css">
    <script language="JavaScript" type="text/javascript">
    <!-- // code for window popups
    function helpwin() {
        var newwin = window.open('<%= path %>/help/index.html#<decorator:getProperty property="meta.helpPage" default=""/>',
            'helpWindow','width=750,height=550,menubar=yes,location=no,personalbar=no,scrollbars=yes,resize=yes');
        newwin.focus();
    }
    //-->
    </script>
    <script type="text/javascript" src="<%= path %>/js/behaviour.js"></script>
    <script type="text/javascript">
    // Add a nice little rollover effect to any row in a jive-table object. This will help
    // visually link left and right columns.
    var myrules = {
        '.jive-table TBODY TR' : function(el) {
            el.onmouseover = function() {
                this.style.backgroundColor = '#efefef';
            }
            el.onmouseout = function() {
                this.style.backgroundColor = '#ffffff';
            }
        }
    };
    Behaviour.register(myrules);
    </script>
    <decorator:head />
</head>

<body id="jive-body">

<div id="jive-header">
<table cellpadding="0" cellspacing="0" width="100%" border="0">
<tbody>
    <tr>
        <td>
            <img id="jive-logo-image" src="<%= path %>/<%= AdminConsole.getLogoImage() %>" border="0" alt="<%= AdminConsole.getAppName() %> <fmt:message key="login.title" />">
        </td>
        <td align="right">
            <table cellpadding="0" cellspacing="0" border="0">
            <tr>
                <td>
                    <a href="#" onclick="helpwin();return false;"
                     ><img src="<%= path %>/images/header-help.gif" width="24" height="24" border="0" alt="Click for help" hspace="10"></a>
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

                            <admin:tabs css="" currentcss="currentlink">
                                <a href="[url]" title="[description]"
                                 onmouseover="self.status='[description]';return true;" onmouseout="self.status='';return true;"
                                 >[name]</a>
                            </admin:tabs>

                        </div>
                    </td>
                    <td width="1%" id="jive-logout" nowrap>
                        <a href="<%= path %>/index.jsp?logout=true"><%= LocaleUtils.getLocalizedString("global.logout") %> [<%= StringUtils.escapeHTMLTags(webManager.getUser().getUsername()) %>]</a>
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
<%--
<div id="jive-secondary">
    <ul>
        <li><a href="">Server Manager</a></li>
        <li><a href="">Server Settings</a></li>
    </ul>
</div>
--%>

<div id="jive-main">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tbody>
    <tr valign="top">
        <td width="1%" id="jive-sidebar-box">
            <div id="jive-sidebar">

                <admin:sidebar css="" currentcss="currentlink" headercss="category">
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

        <div id="jive-title">
            <decorator:title default="&nbsp;"/>
        </div>

        <decorator:body/>

        </td>
    </tr>
</tbody>
</table>
</div>

</body>
</html>