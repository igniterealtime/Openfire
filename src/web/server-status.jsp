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

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.messenger.Session,
                 java.text.DateFormat,
                 org.jivesoftware.messenger.XMPPServer,
                 org.jivesoftware.messenger.container.*,
                 org.jivesoftware.messenger.spi.BasicServer,
                 org.jivesoftware.messenger.auth.UnauthorizedException,
                 org.jivesoftware.admin.AdminPageBean"
%>

<%@ taglib uri="core" prefix="c" %>
<%@ taglib uri="fmt" prefix="fmt" %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<%  // Title of this page and breadcrumbs
    String title = "Server Status";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "main.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "server-status.jsp"));
    pageinfo.setPageID("server-status");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<%  // Get parameters //
    boolean stop = request.getParameter("stop") != null;
    boolean restart = request.getParameter("restart") != null;

    boolean serverOn = (admin.getXMPPServer() != null);

    // Handle starts, stops & restarts
    if (stop) {
      admin.stop(admin.getContainer());
      response.sendRedirect("server-status.jsp");
      return;
    }
    else if (restart) {
      admin.restart(admin.getContainer());
      response.sendRedirect("server-status.jsp");
      return;
    }
%>

<p>
Below is the status of your <fmt:message key="short.title" bundle="${lang}" /> server.
</p>

<table class="box" cellpadding="3" cellspacing="1" border="0" width="600">
<tr class="tableHeaderBlue"><td colspan="2" align="center">Current Status</td></tr>
<tr>
<td class="jive-label">Server Status</td>
  
      
    <%  if (serverOn) { %>
      <td>
      <img src="images/greenlight-24x24.gif" width="24" height="24" border="0" />
      
        </td>

    <%  } else { %>
<td>
     <img src="images/redlight-24x24.gif" width="24" height="24" border="0" />
    
        </td>
        
       

    <%  } %>
</tr>
<%  if (serverOn) { %>
  
     <tr><td class="jive-label">Server Uptime</td>   
        <td>
            <%  DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
                long now = System.currentTimeMillis();
                long lastStarted = admin.getXMPPServer().getServerInfo().getLastStarted().getTime();
                long uptime = (now - lastStarted) / 1000L;
                String uptimeDisplay = null;
                if (uptime < 60) {
                    uptimeDisplay = "Less than 1 minute";
                }
                else if (uptime < 60*60) {
                    long mins = uptime / (60);
                    uptimeDisplay = "Approx " + mins + ((mins==1) ? " minute" : " minutes");
                }
                else if (uptime < 60*60*24) {
                    long days = uptime / (60*60);
                    uptimeDisplay = "Approx " + days + ((days==1) ? " hour" : " hours");
                }
            %>

            <%  if (uptimeDisplay != null) { %>

                <%= uptimeDisplay %> -

            <%  } %>

            <%= formatter.format(admin.getXMPPServer().getServerInfo().getLastStarted()) %>
        </td>
    </tr>
<%  } %>
</table>
</div>

<br>

<script lang="JavaScript" type="text/javascript">
var checked = false;
function checkClick() {
    if (checked) { return false; }
    else { checked = true; return true; }
}
</script>
<% if (admin.getContainer().isStandAlone()){ %>
    <form action="server-status.jsp" onsubmit="return checkClick();">
        <input type="submit" value="Stop" name="stop" <%= ((serverOn) ? "" : "disabled") %>>
    <% if (admin.getContainer().isRestartable()){ %>
        <input type="submit" value="Restart" name="restart" <%= ((serverOn) ? "" : "disabled") %>>
    <% } %>
    </form>
<% } else { %>
<table width=600>
<tr><td>
<span class="highlight">Note: </span><fmt:message key="short.title" bundle="${lang}" /> is running in an application server. You must stop and restart <fmt:message key="short.title" bundle="${lang}" />
by stopping or restarting your application server.</td></tr></table>
<% } %>
<p>
<a href="server-props.jsp">View Server Properties</a>
</p>

<jsp:include page="bottom.jsp" flush="true" />
