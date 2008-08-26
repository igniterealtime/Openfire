<%--
  -	$Revision: 2701 $
  -	$Date: 2005-08-19 16:48:22 -0700 (Fri, 19 Aug 2005) $
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution, or a commercial license
  - agreement with Jive.
--%>

<%@ page import="org.jivesoftware.util.LocaleUtils"%>
<%@ page import="java.beans.PropertyDescriptor"%>
<%@ page import="java.io.File"%>
<%@ page import="org.jivesoftware.database.DbConnectionManager"%>
<%@ page import="java.sql.Connection"%>
<%@ page import="java.util.Map"%>
<%@ page import="java.sql.Statement"%>
<%@ page import="java.sql.SQLException"%>
<%@ page import="org.jivesoftware.admin.AdminConsole" %>

<%@ page contentType="text/html;charset=UTF-8" language="java" %>

<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%@ taglib uri="http://www.opensymphony.com/sitemesh/decorator" prefix="decorator" %>
<%@ taglib uri="http://www.opensymphony.com/sitemesh/page" prefix="page" %>

<decorator:usePage id="decoratedPage" />
<%
    // Check to see if the sidebar should be shown; default to true unless the page specifies
    // that it shouldn't be.
    String sidebar = decoratedPage.getProperty("meta.showSidebar");
    if (sidebar == null) {
        sidebar = "true";
    }
    boolean showSidebar = Boolean.parseBoolean(sidebar);
    int currentStep = decoratedPage.getIntProperty("meta.currentStep");
%>

<%
    String preloginSidebar = (String) session.getAttribute("prelogin.setup.sidebar");
    if (preloginSidebar == null) {
        preloginSidebar = "false";
    }
    boolean showPreloginSidebar = Boolean.parseBoolean(preloginSidebar);
%>

<%!
    final PropertyDescriptor getPropertyDescriptor(PropertyDescriptor[] pd, String name) {
        for (PropertyDescriptor aPd : pd) {
            if (name.equals(aPd.getName())) {
                return aPd;
            }
        }
        return null;
    }

    boolean testConnection(Map<String,String> errors) {
        boolean success = true;
        Connection con = null;
        try {
            con = DbConnectionManager.getConnection();
            if (con == null) {
                success = false;
                errors.put("general","A connection to the database could not be "
                    + "made. View the error message by opening the "
                    + "\"" + File.separator + "logs" + File.separator + "error.log\" log "
                    + "file, then go back to fix the problem.");
            }
            else {
            	// See if the Jive db schema is installed.
            	try {
            		Statement stmt = con.createStatement();
            		// Pick an arbitrary table to see if it's there.
            		stmt.executeQuery("SELECT * FROM ofID");
            		stmt.close();
            	}
            	catch (SQLException sqle) {
                    success = false;
                    sqle.printStackTrace();
                    errors.put("general","The Openfire database schema does not "
                        + "appear to be installed. Follow the installation guide to "
                        + "fix this error.");
            	}
            }
        }
        catch (Exception ignored) {}
        finally {
            try {
        	    con.close();
            } catch (Exception ignored) {}
        }
        return success;
    }
%>

<html>
<head>
    <title><fmt:message key="title" /> <fmt:message key="setup.title" />: <decorator:title /></title>

    <style type="text/css" title="setupStyle" media="screen">
        @import "../style/global.css";
        @import "../style/setup.css";
        @import "../style/lightbox.css";
    </style>

    <script language="JavaScript" type="text/javascript" src="../js/prototype.js"></script>
    <script language="JavaScript" type="text/javascript" src="../js/scriptaculous.js"></script>
    <script language="JavaScript" type="text/javascript" src="../js/lightbox.js"></script>
    <script language="javascript" type="text/javascript" src="../js/tooltips/domLib.js"></script>
    <script language="javascript" type="text/javascript" src="../js/tooltips/domTT.js"></script>
    <script language="javascript" type="text/javascript" src="../js/setup.js"></script>
    <decorator:head />
</head>

<body onload="<decorator:getProperty property="body.onload" />">

<!-- BEGIN jive-main -->
<div id="main">

    <!-- BEGIN jive-header -->
    <div id="jive-header">
        <div id="jive-logo">
            <a href="/index.jsp"><img src="/images/login_logo.gif" alt="Openfire" width="179" height="53" /></a>
        </div>
        <div id="jive-userstatus">
            <%= AdminConsole.getAppName() %> <%= AdminConsole.getVersionString() %><br/>
        </div>
        <div id="jive-nav">
            <div id="jive-nav-left"></div>
            <ul>
                <li><a><fmt:message key="setup.title"/></a></li>
            </ul>
            <div id="jive-nav-right"></div>
        </div>
        <div id="jive-subnav">
            &nbsp;
        </div>
    </div>
    <!-- END jive-header -->


    <div id="jive-main">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr valign="top">
            <td width="1%">
                <div id="jive-sidebar-container">
                    <div id="jive-sidebar-box">


<!-- BEGIN jive-sidebar -->
                        <div id="jive-sidebar">
                            <%  if (showSidebar) {
                                    String[] names;
                                    String[] links;
                                    if (showPreloginSidebar) {
                                        names = new String[] {
                                                LocaleUtils.getLocalizedString((String) session.getAttribute("prelogin.setup.sidebar.title"))
                                        };
                                        links = new String[] {
                                                (String) session.getAttribute("prelogin.setup.sidebar.link")
                                        };
                                    } else {
                                        names = new String[] {
                                             LocaleUtils.getLocalizedString("setup.sidebar.language"),
                                             LocaleUtils.getLocalizedString("setup.sidebar.settings"),
                                             LocaleUtils.getLocalizedString("setup.sidebar.datasource"),
                                             LocaleUtils.getLocalizedString("setup.sidebar.profile"),
                                             LocaleUtils.getLocalizedString("setup.sidebar.admin")
                                         };
                                         links = new String[] {
                                             "index.jsp",
                                             "setup-host-settings.jsp",
                                             "setup-datasource-settings.jsp",
                                             "setup-profile-settings.jsp",
                                             "setup-admin-settings.jsp"
                                         };
                                    }
                                    %>
                                <ul id="jive-sidebar-progress">
                                    <%  if (!showPreloginSidebar) { %>
                                    <li class="category"><fmt:message key="setup.sidebar.title" /></li>
                                    <li><img src="../images/setup_sidebar_progress<%= currentStep %>.gif" alt="" width="142" height="13" border="0"></li>
                                    <%  } %>
                                    <%  for (int i=0; i<names.length; i++) { %>
                                        <%  if (currentStep < i) { %>
                                        <li><a href="<%= links[i] %>"><%= names[i] %></a></li>
                                        <%  } else if (currentStep == i) { %>
                                        <li class="currentlink"><a href="<%= links[i] %>"><%= names[i] %></a></li>
                                        <%  } else { %>
                                        <li class="completelink"><a href="<%= links[i] %>"><%= names[i] %></a></li>
                                        <%  } %>
                                    <%  } %>
                                </ul>

                            <%  } %>


                        </div>
<!-- END jive-sidebar -->

                    </div>
                </div>
            </td>
            <td width="99%" id="jive-content">

<!-- BEGIN jive-body -->

                <div id="jive-main-content">
                    <decorator:body/>
                </div>

<!-- END jive-body -->
            </td>
        </tr>
    </tbody>
    </table>
    </div>

</div>
<!-- END jive-main -->

<!-- BEGIN jive-footer -->
    <div id="jive-footer">
        <div class="jive-footer-copyright">
            Built by <a href="http://www.jivesoftware.com">Jive Software</a> and the <a href="http://www.igniterealtime.org">IgniteRealtime.org</a> community
        </div>
    </div>
<!-- END jive-footer -->

</body>
</html>