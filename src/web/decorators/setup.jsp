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
	<link rel="stylesheet" type="text/css" href="setup-style.css">
</head>

<body>

<span class="jive-setup-header">
<table cellpadding="8" cellspacing="0" border="0" width="100%">
<tr>
    <td>
        <fmt:message key="title" /> <fmt:message key="setup.title" />
    </td>
</tr>
</table>
</span>
<table bgcolor="#bbbbbb" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td><img src="../images/blank.gif" width="1" height="1" border="0" alt=""></td></tr>
</table>
<table bgcolor="#dddddd" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td><img src="../images/blank.gif" width="1" height="1" border="0" alt=""></td></tr>
</table>
<table bgcolor="#eeeeee" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td><img src="../images/blank.gif" width="1" height="1" border="0" alt=""></td></tr>
</table>

<br>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tr valign="top">
    <%  if (showSidebar) { %>
        <td width="1%" nowrap>
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

                if (step1 == null) { step1 = IN_PROGRESS; }
                if (step2 == null) { step2 = INCOMPLETE; }
                if (step3 == null) { step3 = INCOMPLETE; }
                if (step4 == null) { step4 = INCOMPLETE; }

                String[] items = {step1, step2, step3, step4};
                String[] names = {
                    LocaleUtils.getLocalizedString("setup.sidebar.language"),
                    LocaleUtils.getLocalizedString("setup.sidebar.settings"),
                    LocaleUtils.getLocalizedString("setup.sidebar.datasource"),
                    LocaleUtils.getLocalizedString("setup.sidebar.admin")
                };
                String[] links = {
                    "index.jsp",
                    "setup-host-settings.jsp",
                    "setup-datasource-settings.jsp",
                    "setup-admin-settings.jsp"
                };
            %>

            <table bgcolor="#cccccc" cellpadding="0" cellspacing="0" border="0" width="200">
            <tr><td>
            <table bgcolor="#cccccc" cellpadding="3" cellspacing="1" border="0" width="200">
            <tr bgcolor="#eeeeee">
                <td align="center">
                    <span style="padding:6px">
                    <b><fmt:message key="setup.sidebar.title" /></b>
                    </span>
                </td>
            </tr>
            <tr bgcolor="#ffffff">
                <td>
                    <table cellpadding="5" cellspacing="0" border="0" width="100%">
                    <%  for (int i=0; i<items.length; i++) { %>
                        <tr>
                        <%  if (INCOMPLETE.equals(items[i])) { %>

                            <td width="1%"><img src="../images/bullet-red-14x14.gif" width="14" height="14" border="0" alt="*"></td>
                            <td width="99%">
                                    <%= names[i] %>
                            </td>

                        <%  } else if (IN_PROGRESS.equals(items[i])) { %>

                            <td width="1%"><img src="../images/bullet-yellow-14x14.gif" width="14" height="14" border="0" alt="*"></td>
                            <td width="99%">
                                    <a href="<%= links[i] %>"><%= names[i] %></a>
                            </td>

                        <%  } else { %>

                            <td width="1%"><img src="../images/bullet-green-14x14.gif" width="14" height="14" border="0" alt="*"></td>
                            <td width="99%">
                                    <a href="<%= links[i] %>"><%= names[i] %></a>
                            </td>

                        <%  } %>
                        </tr>
                    <%  } %>
                    <tr><td colspan="2"><br><br><br><br></td></tr>
                    </table>
                </td>
            </tr>
            </table>
            </td></tr>
            </table>
        </td>
        <td width="1%" nowrap><img src="../images/blank.gif" width="15" height="1" border="0" alt=""></td>
    <%  } %>
    <td width="98%">

    <decorator:body/>

    </td></tr>
</table>

</body>
</html>