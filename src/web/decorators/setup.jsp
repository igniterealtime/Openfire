<%--
  -	$Revision: 2701 $
  -	$Date: 2005-08-19 16:48:22 -0700 (Fri, 19 Aug 2005) $
  -
  - Copyright (C) 2004-2005 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.LocaleUtils"%>
<%@ page import="org.jivesoftware.util.ClassUtils"%>
<%@ page import="java.beans.PropertyDescriptor"%>
<%@ page import="java.io.File"%>
<%@ page import="org.jivesoftware.database.DbConnectionManager"%>
<%@ page import="java.sql.Connection"%>
<%@ page import="java.util.Map"%>
<%@ page import="java.sql.Statement"%>
<%@ page import="java.sql.SQLException"%>
<%@ page import="org.jivesoftware.wildfire.XMPPServer"%>

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
%>

<%!
    final PropertyDescriptor getPropertyDescriptor(PropertyDescriptor[] pd, String name) {
        for (int i=0; i<pd.length; i++) {
            if (name.equals(pd[i].getName())) {
                return pd[i];
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
            		stmt.executeQuery("SELECT * FROM jiveID");
            		stmt.close();
            	}
            	catch (SQLException sqle) {
                    success = false;
                    sqle.printStackTrace();
                    errors.put("general","The Wildfire database schema does not "
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
	@import "../style/setup.css";
</style>

</head>

<body>


<!-- BEGIN jive-header -->
<div id="jive-header">
	<div id="jive-logo" title="wildfire"></div>
	<div id="jive-header-text">Setup</div>
	<div id="sidebar-top"></div>
</div>
<!-- END jive-header -->



<!-- BEGIN jive-sidebar -->
<div id="jive-sidebar">
    <%  if (showSidebar) { %>
           <%!
                final String INCOMPLETE = "incomplete";
                final String IN_PROGRESS = "in_progress";
                final String DONE = "done";
            %>
            <%  // Get sidebar values from the session:

                String step1 = (String)session.getAttribute("jive.setup.sidebar.1");
                String step2 = (String)session.getAttribute("jive.setup.sidebar.2");
                String step3 = (String)session.getAttribute("jive.setup.sidebar.3");
                String step4 = (String)session.getAttribute("jive.setup.sidebar.4");
                String step5 = (String)session.getAttribute("jive.setup.sidebar.4");

                if (step1 == null) { step1 = IN_PROGRESS; }
                if (step2 == null) { step2 = INCOMPLETE; }
                if (step3 == null) { step3 = INCOMPLETE; }
                if (step4 == null) { step4 = INCOMPLETE; }
                if (step5 == null) { step5 = INCOMPLETE; }

                String[] items = {step1, step2, step3, step4, step5};
                String[] names = {
                    LocaleUtils.getLocalizedString("setup.sidebar.language"),
                    LocaleUtils.getLocalizedString("setup.sidebar.settings"),
                    LocaleUtils.getLocalizedString("setup.sidebar.datasource"),
                    LocaleUtils.getLocalizedString("setup.sidebar.profile"),
                    LocaleUtils.getLocalizedString("setup.sidebar.admin")
                };
                String[] links = {
                    "index.jsp",
                    "setup-host-settings.jsp",
                    "setup-datasource-settings.jsp",
                    "setup-profile-settings.jsp",
                    "setup-admin-settings.jsp"
                };
            %>
	<div class="jive-sidebar-group">
	<strong><fmt:message key="setup.sidebar.title" /></strong>
		<ul>
			<%  for (int i=0; i<items.length; i++) { %>
				<%  if (INCOMPLETE.equals(items[i])) { %>
				<li><%= names[i] %></li>
				<%  } else if (IN_PROGRESS.equals(items[i])) { %>
				<li class="jiveCurrent"><%= names[i] %></li>
				<%  } else { %>
				<li class="jiveComplete"><!--<a href="<%= links[i] %>">--><%= names[i] %></li>
				<%  } %>
			<%  } %>
		</ul>
	</div>
    <%  } %>

	<div class="jive-sidebar-group">
		<strong>Setup Progress</strong>
		<img src="../images/setup_sidebar_progress1.gif" alt="" width="142" height="13" border="0">
	</div>
</div>
<!-- END jive-sidebar -->



<!-- BEGIN jive-body -->
<div id="jive-body">

    <decorator:body/>

</div>
<!-- END jive-body -->



<!-- BEGIN jive-footer -->
<div id="jive-footer"></div>
<!-- END jive-footer -->



</body>
</html>