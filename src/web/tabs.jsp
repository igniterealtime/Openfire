<%@ taglib uri="core" prefix="c" %>
<%@ taglib uri="fmt" prefix="fmt" %>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.messenger.JiveGlobals,
                 org.jivesoftware.util.Version,
                 org.jivesoftware.util.WebManager" %>
<jsp:useBean id="adm" class="org.jivesoftware.util.WebManager" />

<%
     adm.init(request, response, session, application, out );
%>

<%	// Get the "tab" parameter -- this tells us which tab to show as active

    String tab = (String)pageContext.getAttribute( "sbar" );

    // Set a default tab value
	  if (tab == null) {
    String sessionTab = (String)session.getAttribute("jive.admin.sidebarTab");
    if (sessionTab == null) {
      tab = "server";
    }
    else {
      tab = sessionTab;
    }
	}

    // Set the content type and character encoding
    response.setContentType("text/html; charset=" + JiveGlobals.getCharacterEncoding());
%>

<!-- Handle SideBar -->
<c:set var="s" value="${pageScope.sbar}" />
<c:if test="${!empty s}">  
  <c:set var="sidebar" value="${s}" />
</c:if>

<table class="jive-admin-header" cellpadding="0" cellspacing="0" border="0" width="100%" background="images/admin-back.gif">
<tr>
    <td><a href="server-status.jsp" >
    <img src="images/header-messenger.gif" vspace="5"  border="0"></a>
    </td>
</tr>
</table>

<table class="jive-tabs" cellpadding="0" cellspacing="0" border="0">
<tr>
    <%  int tabCount = 1; %>

    <td class="jive-<%= (tab.equals("server"))?"selected-":"" %>tab" width="1%" nowrap>
        <a href="server-status.jsp">Server Manager</a>
    </td>
    <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>

    <%  tabCount++; %>

    <td class="jive-<%= (tab.equals("database"))?"selected-":"" %>tab" width="1%" nowrap>
        <a href="db-connection.jsp">Database Manager</a>
    </td>
    <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>

    <%  tabCount++; %>

    <td class="jive-<%= (tab.equals("users"))?"selected-":"" %>tab" width="1%" nowrap>
        <a href="user-summary.jsp">User Manager</a>
    </td>
    <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>

    <%  tabCount++; %>

    <td class="jive-<%= (tab.equals("session"))?"selected-":"" %>tab" width="1%" nowrap>
        <a href="session-summary.jsp">Session Manager</a>
    </td>
    <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>

    <!-- TODO:Check For Plugin -->

    <td class="jive-tab-spring" width="<%= (100-(tabCount*2)) %>%" align="right" nowrap>
        <%  if (adm.getXMPPServer() != null) { %>

            <fmt:message key="title" bundle="${lang}" /> Version: <%= adm.getXMPPServer().getServerInfo().getVersion().getVersionString() %>

        <%  } else { %>

            Jive Messenger

        <%  } %>
    </td>
</tr>
<tr>
    <td class="jive-tab-bar" colspan="99">
        <table cellpadding="3" cellspacing="0" border="0" width="100%">
        <tr>
            <td width="98%">
                <table class="jive-tab-section" cellpadding="3" cellspacing="0" border="0">
                <tr>
                    <%  if (tab.equals("server")) { %>
                        <td>
                            <a href="server-status.jsp" >Server Status</a>
                        </td>
                        <td>
                            <a href="server-props.jsp" >Server Properties</a>
                        </td>
                        <td>
                            <a href="license-details.jsp" >License Details</a>
                        </td>
                    <%  }
                        else if (tab.equals("database")) {
                    %>
                        <td>
                            <a href="db-connection.jsp" >Connection Info</a>
                        </td>
                    <%  }
                        else if (tab.equals("users")) {
                    %>
                        <td>
                            <a href="user-summary.jsp" >User Summary</a>
                        </td>
                        <td>
                            <a href="user-search.jsp" >User Search</a>
                        </td>
                        <td>
                            <a href="user-create.jsp" >Create User</a>
                        </td>
                    <%  }
                        else if (tab.equals("session")) {
                    %>
                        <td>
                            <a href="session-summary.jsp" >List Sessions</a>
                        </td>
                        <td>
                            <a href="user-message.jsp?tabs=false" >Send Administrative Message</a>
                        </td>
                    <%  }
                        else if (tab.equals("muc")) {
                    %>
                        <td>
                            <a href="muc-server-props-edit-form.jsp" >Server Properties</a>
                        </td>
                        <td>
                            <a href="muc-history-settings.jsp" >History Settings</a>
                        </td>
                    <%  } %>
                        <!-- TODO: Change For Plugin -->

                </tr>
                </table>
            </td>
            <td width="1%"
                ><a href="index.jsp?logout=true" title="Click to logout" target="_parent"
                ><img src="images/logout-16x16.gif" width="16" height="16" border="0"
                ></a></td>
            <td nowrap width="1%" class="jive-tab-logout">
                <a href="index.jsp?logout=true" title="Click to logout" target="_parent">Logout</a>
                <%  if (adm.getUser() != null) { %>

                    <span title="You are logged in as '<%= adm.getUser().getUsername() %>'">
                    [<b><%= adm.getUser().getUsername() %></b>]
                    </span>

                <%  } %>
            </td>
        </tr>
        </table>
    </td>
</tr>
</table>
<table cellpadding="0" cellspacing="0" border="0" width="100%" bgcolor="#cccccc">
<tr><td><img src="images/blank.gif" width="1" height="1" border="0"></td></tr>
</table>
<table cellpadding="0" cellspacing="0" border="0" width="100%" bgcolor="#eeeeee">
<tr><td><img src="images/blank.gif" width="1" height="1" border="0"></td></tr>
</table>
