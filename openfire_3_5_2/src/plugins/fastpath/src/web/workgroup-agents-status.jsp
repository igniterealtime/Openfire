<%--

  -	$RCSfile$

  -	$Revision: 32833 $

  -	$Date: 2006-08-02 15:52:36 -0700 (Wed, 02 Aug 2006) $

--%>
<%@ page import="org.jivesoftware.xmpp.workgroup.Agent,
                 org.jivesoftware.xmpp.workgroup.AgentSession,
                 org.jivesoftware.xmpp.workgroup.Workgroup,
                 org.jivesoftware.xmpp.workgroup.WorkgroupManager,
                 org.jivesoftware.util.ParamUtils,
                 org.xmpp.packet.JID,
                 org.xmpp.packet.Presence"
        errorPage="/error.jsp"%>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Iterator" %>
<%
    String wgID = ParamUtils.getParameter(request, "wgID");
    int start = ParamUtils.getIntParameter(request, "start", 0);
    int range = ParamUtils.getIntParameter(request, "range", 15);
    String filter = ParamUtils.getParameter(request, "filter");
    if (filter == null) {
        filter = "all";
    }

    // Load the workgroup
    final WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
    Workgroup workgroup = workgroupManager.getWorkgroup(new JID(wgID));

    Collection<Agent> agents = new ArrayList<Agent>(workgroup.getAgents());
    if ("connected".equals(filter)) {
        for (Agent agent : agents) {
            agent.getAgentSession();
        }
    }
    else if ("available".equals(filter)) {
        for (Iterator<Agent> it=agents.iterator(); it.hasNext();) {
            AgentSession agentSession = it.next().getAgentSession();
            if (!agentSession.isAvailableToChat()) {
                it.remove();
            }
        }
    }

    int numPages = (int)Math.ceil((double)agents.size()/(double)range);
    int curPage = (start/range) + 1;
%>
<html>
    <head>
        <title>Status of Agents</title>
        <meta name="pageID" content="workgroup-summary"/>
    </head>
    <body>
<table cellpadding="3" cellspacing="0" border="0">
  <tr>
    <td colspan="8">
Below is a list of agents related to the workgroup <a href="workgroup-properties.jsp?wgID=<%= workgroup.getJID().toString() %>">
<%=  workgroup.getJID().getNode() %></a>. The list includes the status of the agent and the number of chats that the agent is having at the moment.</td>
  </tr>
</table>
<table cellpadding="3" cellspacing="0" border="0">
  <tr>
    <td colspan="8" class="text">
    <table cellpadding="3" cellspacing="0" border="0">
    <tr>
        <td>Total Agents: <a href="workgroup-agents-status.jsp?wgID=<%= workgroup.getJID().toString() %>">
            <% if ("all".equals(filter)) { %>
                <b><%= workgroup.getAgents().size()%></b>
            <% } else { %>
               <%= workgroup.getAgents().size()%>
            <% } %></a>
        </td>
        <td>Connected: <a href="workgroup-agents-status.jsp?wgID=<%= workgroup.getJID().toString() %>&filter=connected">
            <% if ("connected".equals(filter)) { %>
                <b><%= workgroup.getAgentSessions().size()%></b>
            <% } else { %>
                <%= workgroup.getAgentSessions().size()%>
            <% } %></a>
        </td>
        <td>Available: <a href="workgroup-agents-status.jsp?wgID=<%= workgroup.getJID().toString() %>&filter=available">
            <% if ("available".equals(filter)) { %>
                <b><%= workgroup.getAgentAvailableSessions().size()%></b>
            <% } else { %>
                <%= workgroup.getAgentAvailableSessions().size()%>
            <% } %></a>
        </td>
    </tr>
    <%  if (numPages > 1) { %>
    <tr>
        Showing:
        [
        <%  for (int i=0; i<numPages; i++) {
                String sep = ((i+1)<numPages) ? " " : "";
                boolean isCurrent = (i+1) == curPage;
        %>
            <a href="workgroup-agents-status.jsp?start=<%= (i*range) %>&wgID=<%= workgroup.getJID().toString() %>"
             class="<%= ((isCurrent) ? "jive-current" : "") %>"
             ><%= (i+1) %></a><%= sep %>

        <%  } %>
        ]
    </tr>
    <%  } %>
    </table>
    </td>
  </tr>
</table>
<table class="jive-table" cellpadding="3" cellspacing="0" border="0" width="100%">
  <thead>
    <tr>
      <th nowrap align="left" colspan="2">Agent</th>
      <th nowrap align="left">Nickname</th>
      <th nowrap colspan="2">Status</th>
      <th nowrap>Current chats</th>
      <th nowrap>Max chats</th>
    </tr>
  </thead>
    <%   // Print the list of agents
    if (agents.size() == 0) {

%>
    <tr>
      <td align="center" colspan="8">
        <br/>No agents found
      </td>
    </tr>
    <%

    }

    int i = start;

    int stop = i + range;

    int counter = 0;
    for (Agent agent : agents) {
        AgentSession agentSession = agent.getAgentSession();

        counter++;
        if(counter < i){
            continue;
        }

        if(counter == stop){
            break;
        }
        i++;

%>
    <tr class="c1">
      <td width="1%">
        <%=  i %>.
      </td>
      <td>
        <%=  agent.getAgentJID() %>
      </td>
      <td>
       <%   if (agent.getNickname() != null) { %>
          <%=  agent.getNickname() %>
        <%   } %>
      </td>
        <% if (agentSession == null) { %>
        <td width="1%"
            ><img src="images/bullet-clear-16x16.png" border="0" title="Not Available" alt=""
            ></td>
        <td width="46%">
            Not Available
        </td>
        <%
        } else {
            Presence.Show _show = agentSession.getPresence().getShow();
            String _stat = agentSession.getPresence().getStatus();
            if (_show == Presence.Show.away) {
        %>
        <td width="1%"
            ><img src="images/bullet-yellow-14x14.gif" width="14" height="14" border="0" title="Away" alt=""
            ></td>
        <td width="46%">
            <%  if (_stat != null) { %>

                <%= _stat %>

            <%  } else { %>

                Away

            <%  } %>
        </td>

    <%  } else if (_show == Presence.Show.chat) { %>

        <td width="1%"
            ><img src="images/bullet-green-14x14.gif" width="14" height="14" border="0" title="Available to Chat" alt=""
            ></td>
        <td width="46%">
            Available to Chat
        </td>

    <%  } else if (_show == Presence.Show.dnd) { %>

        <td width="1%"
            ><img src="images/bullet-red-14x14.gif" width="14" height="14" border="0" title="Do Not Disturb" alt=""
            ></td>
        <td width="46%">
            <%  if (_stat != null) { %>

                <%= _stat %>

            <%  } else { %>

                Do Not Disturb

            <%  } %>
        </td>

    <%  } else if (_show == null) { %>

        <td width="1%"
            ><img src="images/bullet-green-14x14.gif" width="14" height="14" border="0" title="Online" alt=""
            ></td>
        <td width="46%">
            Online
        </td>

    <%  } else if (_show == Presence.Show.xa) { %>

        <td width="1%"
            ><img src="images/bullet-yellow-14x14.gif" width="14" height="14" border="0" title="Extended Away" alt=""
            ></td>
        <td width="46%">
            <%  if (_stat != null) { %>

                <%= _stat %>

            <%  } else { %>

                Extended Away

            <%  } %>
        </td>

    <%  } else { %>

        <td colspan="2" width="46%">
            Unknown/Not Recognized
        </td>

    <%  } } %>
      <td width="10%" align="center">
        <% if (agentSession == null) { %>
            0
        <% } else { %>
            <a href="workgroup-agent-chats.jsp?wgID=<%= workgroup.getJID().toString() %>&agentJID=<%= agent.getAgentJID() %>"><%=  agentSession.getCurrentChats(workgroup) %></a>
        <% } %>
      </td>
      <td width="10%" align="center">
        <% if (agentSession != null) { %>
            <%=  agentSession.getMaxChats(workgroup) %>
        <% } else { %>
            <%=  workgroup.getMaxChats() %>
        <% } %>
      </td>
    </tr>
    <% } %>
</table>
<%  if (numPages > 1) { %>

    <p>
    Pages:
    [
    <%  for (i=0; i<numPages; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="workgroup-agents-status.jsp?start=<%= (i*range) %>&wgID=<%= workgroup.getJID().toString() %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>
    ]
    </p>

<%  } %>

</body>
</html>
