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
                 org.xmpp.packet.Message"%>
<%@ page import="org.xmpp.packet.Packet" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.util.*" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<!-- Define Administration Bean -->
<%
    String wgID = ParamUtils.getParameter(request, "wgID");
    String agentJID = ParamUtils.getParameter(request, "agentJID");
    String roomID = ParamUtils.getParameter(request, "roomID");

    // Load the workgroup
    final WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
    Workgroup workgroup = workgroupManager.getWorkgroup(new JID(wgID));
    Agent agent = workgroupManager.getAgentManager().getAgent(new JID(agentJID));
    AgentSession agentSession = agent.getAgentSession();
    List<AgentSession.ChatInfo> chatsInfo = new ArrayList<AgentSession.ChatInfo>(agentSession.getChatsInfo(workgroup));
    Collections.sort(chatsInfo);

    Map<Packet, java.util.Date> transcript = null;
    if (roomID != null) {
        transcript = new HashMap<Packet, java.util.Date>(workgroup.getTranscript(roomID));
    }

    DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
%>
<html>
    <head>
        <title>Current chats of Agent</title>
        <meta name="pageID" content="workgroup-summary"/>
    </head>
    <body>
<table cellpadding="3" cellspacing="0" border="0">
  <tr>
    <td colspan="8">
Below is a list of current chats the agent <b><%= agentJID %></b> is having.</td>
  </tr>
</table>
<table cellpadding="3" cellspacing="0" border="0">
  <tr>
    <td colspan="8" class="text">
    Total Chats: <%= chatsInfo.size()%>
    </td>
  </tr>
</table>
<table class="jive-table" cellpadding="3" cellspacing="0" border="0" width="100%">
  <thead>
    <tr>
      <th nowrap align="left">Date</th>
      <th nowrap>User ID</th>
      <th nowrap>User JID</th>
      <th nowrap>Room ID</th>
      <th nowrap>Messages</th>
    </tr>
  </thead>
    <%   // Print the list of chats
    if (chatsInfo.size() == 0) {
%>
    <tr>
      <td align="center" colspan="8">
        <br/>Agent is not having chats at the moment
      </td>
    </tr>
    <%
    }

    for (AgentSession.ChatInfo chatInfo : chatsInfo) {
%>
    <tr class="c1">
      <td width="30%">
        <%= formatter.format(chatInfo.getDate()) %>
      </td>
      <td width="20%" align="center">
        <%= chatInfo.getUserID()%></td>
      <td width="20%" align="center">
        <%= chatInfo.getUserJID().toString() %></td>
      <td width="20%" align="center">
        <%= chatInfo.getSessionID() %>
      </td>
      <td width="10%" align="center">
        <% int count = 0;
        for (Packet packet : chatInfo.getPackets().keySet()) {
            if (packet instanceof Message) {
                count++;
            }
        }%>
        <a href="workgroup-agent-chats.jsp?wgID=<%= workgroup.getJID().toString() %>&agentJID=<%= agent.getAgentJID() %>&roomID=<%= chatInfo.getSessionID()%>"><%= count %></a>
      </td>
    </tr>
    <% } %>
  </thead>
</table>


<% if (transcript != null) { %>
<br>
<table cellpadding="3" cellspacing="0" border="0">
  <tr>
    <td colspan="8">Below is the chat transcript of the room <b><%= roomID %></b>.</td>
  </tr>
</table>
<table class="jive-table" cellpadding="3" cellspacing="0" border="0" width="100%">
  <thead>
    <tr>
      <th nowrap align="left">Date</th>
      <th nowrap>Sender</th>
      <th nowrap>Message</th>
    </tr>
  </thead>
    <%   // Print the list of chats
    if (transcript.size() == 0) {
%>
    <tr>
      <td align="center" colspan="8">
        <br/>No messages in the room where found
      </td>
    </tr>
    <% }

    SortedMap<Date, Packet> sortedTranscript = new TreeMap<Date, Packet>();
    for (Packet packet : transcript.keySet()) {
        sortedTranscript.put(transcript.get(packet), packet);
    }

    for (Date date : sortedTranscript.keySet()) {
        Packet packet = sortedTranscript.get(date);
        if (!(packet instanceof Message)) {
            continue;
        }
%>
    <tr class="c1">
      <td width="20%">
        <%= formatter.format(date) %>
      </td>
      <td width="10%" align="center">
        <%= StringUtils.escapeForXML(packet.getFrom().getResource()) %></td>
      <td width="70%" align="center">
        <%= StringUtils.escapeForXML(((Message)packet).getBody()) %></td>
    </tr>
    <% } %>
  </thead>
</table>

<% } %>
</body>
</html>
