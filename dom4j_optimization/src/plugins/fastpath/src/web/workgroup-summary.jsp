<%--

  -	$RCSfile$

  -	$Revision: 32861 $

  -	$Date: 2006-08-03 08:28:30 -0700 (Thu, 03 Aug 2006) $

--%>
<%@ page import="org.jivesoftware.util.*,
                 java.util.Iterator,
                 org.jivesoftware.xmpp.workgroup.WorkgroupResultFilter,
                 org.jivesoftware.xmpp.workgroup.Workgroup,
                 org.jivesoftware.xmpp.workgroup.WorkgroupAdminManager"%>
<%@ page import="org.jivesoftware.openfire.muc.MultiUserChatService"%>
<%@ page import="org.jivesoftware.openfire.XMPPServer"%>
<!-- Define Administration Bean -->
<%
    WorkgroupAdminManager webManager = new WorkgroupAdminManager();
    webManager.init(pageContext);
%>

<%
    // Get muc server
    MultiUserChatService mucService = null;
    for (MultiUserChatService service : webManager.getMultiUserChatManager().getMultiUserChatServices()) {
        if (!service.isRoomCreationRestricted()) {
            mucService = service;
        }
    }

%>
<%@ include file="check-cluster.jspf" %>
<html>
    <head>
        <title>Workgroup Summary</title>
        <meta name="pageID" content="workgroup-summary"/>
        <!--<meta name="helpPage" content="get_around_in_the_admin_console.html"/>-->
    </head>
    <body>
    <style type="text/css">
        @import "style/style.css";
    </style>
<% if(mucService == null){ %>
    <div class="warning">
        Fastpath needs a Group Conference service set up so rooms can be created on the server without restriction. Please set up a Group Conference service with permissions <a href="/muc-service-summary.jsp">here</a>.
    </div>

<% } %>


<%
    boolean deleted = ParamUtils.getParameter(request, "deleted") != null;
%>
<% if(deleted){%>
    <div class="success">
       Workgroup has been deleted!
     </div><br>
<% } %>
<%
    int start = ParamUtils.getIntParameter(request, "start", 0);
    int range = ParamUtils.getIntParameter(request, "range", 15);
    webManager.setStart(start);
    webManager.setRange(range);

    int numPages = (int)Math.ceil((double)webManager.getWorkgroupManager().getWorkgroupCount()/(double)range);
    int curPage = (start/range) + 1;
%>
<table cellpadding="3" cellspacing="0" border="0">
  <tr>
    <td colspan="8">
Below is the list of workgroups in the system. A workgroup is an alias for contacting a group of members and is made up of one or more queues.</td>
  </tr>
</table>
<table cellpadding="3" cellspacing="0" border="0">
  <tr>
    <td colspan="8" class="text">Total Workgroups: <%= webManager.getWorkgroupManager().getWorkgroupCount()%>.
     <% if(webManager.getNumPages() > 1) { %>
        Showing <%= webManager.getStart() + 1%> - <%= webManager.getStart() + webManager.getRange()%>
      <% } %>
      <br/><br/>
      <% if(webManager.getNumPages() > 1){ %>
        <table border="0" cellpadding="3" cellspacing="0">
        <tr>
          <td colspan="8" class="text">Pages: [
            <%   for (int pageIndex=0; pageIndex<numPages; pageIndex++) {

            String sep = ((pageIndex+1)<numPages) ? " " : "";

            boolean isCurrent = (pageIndex+1) == curPage;

    %>
            <a href="workgroup-summary.jsp?start=<%= (pageIndex*range) %>" class="<%= ((isCurrent) ? "jive-current" : "") %>">
              <%=  (pageIndex+1) %>
            </a>
            <%=  sep %>
            <%   } %>]
          </td>
        </tr>
        </table>
      <% } %>
    </td>
  </tr>
</table>
<table class="jive-table" cellpadding="3" cellspacing="0" border="0" width="100%">
  <thead>
    <tr>
      <th nowrap align="left" colspan="2">Name</th>
      <th nowrap>Status</th>
      <th nowrap>Members (Active/Total) </th>
      <th nowrap>Queues</th>
      <th nowrap>Users in Queues</th>
      <th nowrap>Edit</th>
      <th nowrap>Delete</th>
    </tr>
  </thead>
    <%   // Print the list of workgroups

    WorkgroupResultFilter filter = new WorkgroupResultFilter();

    filter.setStartIndex(start);

    filter.setNumResults(range);

    Iterator workgroups = webManager.getWorkgroupManager().getWorkgroups(filter);

    if (!workgroups.hasNext()) {

%>
    <tr>
      <td align="center" colspan="8">
        <br/>No workgroups --
        <a href="workgroup-create.jsp">create workgroup<a>.
            <br/>
            <br/>
          </a>
        </a>
      </td>
    </tr>
    <%

    }

    int i = start;

    while (workgroups.hasNext()) {

        Workgroup workgroup = (Workgroup)workgroups.next();

        i++;

%>
    <tr class="c1">
      <td width="39%" colspan="2">
        <a href="workgroup-queues.jsp?wgID=<%= workgroup.getJID().toString() %>">
            <b><%=  workgroup.getJID().getNode() %></b>
          </a>
        <%   if (workgroup.getDescription() != null) { %>
        <span class="jive-description">
          <br/>
          <%=  workgroup.getDescription() %>
        </span>
        <%   } %>
      </td>
      <td width="10%" align="center" nowrap>
      <table>
         <tr>
             <td width="14">
                 <%   if (workgroup.getStatus() == Workgroup.Status.OPEN) { %>
                 <img src="images/bullet-green-14x14.gif" width="14" height="14" border="0" title="Workgroup is currently open, active and accepting requests." alt=""/>
                 </td><td nowrap>Open
                     <%   } else if (workgroup.getStatus() == Workgroup.Status.READY) { %>
                 <img src="images/bullet-yellow-14x14.gif" width="14" height="14" border="0" title="Workgroup is currently ready to open when a member is available." alt=""/>
                 </td><td nowrap>Waiting for member
                         <%   } else { %>
                 <img src="images/bullet-red-14x14.gif" width="14" height="14" border="0" title="Workgroup is currently closed." alt=""/>
                 </td><td nowrap>Closed
                 <%   } %>
         </td>
        </tr></table>
      </td>
      <td width="10%" align="center">
        <a href="workgroup-agents-status.jsp?wgID=<%= workgroup.getJID().toString() %>">
          <%=  webManager.getActiveAgentMemberCount(workgroup) %>/<%=  webManager.getAgentsInWorkgroup(workgroup).size() %>
        </a>
      </td>
      <td width="10%" align="center">
        <%=  workgroup.getRequestQueueCount() %>
      </td>
      <td width="10%" align="center">
        <%=  webManager.getWaitingCustomerCount(workgroup) %>
      </td>
      <td width="10%" align="center">
        <a href="workgroup-queues.jsp?wgID=<%= workgroup.getJID().toString() %>" title="Click to edit...">
          <img src="images/edit-16x16.gif" width="16" height="16" border="0" alt=""/>
        </a>
      </td>
      <td width="10%" align="center">
        <a href="workgroup-delete.jsp?wgID=<%= workgroup.getJID().toString() %>" title="Click to delete...">
          <img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""/>
        </a>
      </td>
    </tr>
    <%

    }

%>
  </thead>
</table>
<% if(numPages > 1){ %>
  <p>Pages: [
    <%   for (int pageIndex=0; pageIndex<numPages; pageIndex++) {

            String sep = ((pageIndex+1)<numPages) ? " " : "";

            boolean isCurrent = (pageIndex+1) == curPage;

    %>
    <a href="workgroup-summary.jsp?start=<%= (pageIndex*range) %>" class="<%= ((isCurrent) ? "jive-current" : "") %>">
      <%=  (pageIndex+1) %>
    </a>
    <%=  sep %>
    <%   } %>]
  </p>
<% } %>

    </body>
</html>