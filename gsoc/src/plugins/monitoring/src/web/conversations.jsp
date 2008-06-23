<%@ page import="java.util.*,
                 org.jivesoftware.openfire.XMPPServer"
%>
<%@ page import="org.jivesoftware.openfire.plugin.MonitoringPlugin"%>
<%@ page import="org.jivesoftware.openfire.archive.ConversationManager"%>
<%@ page import="org.jivesoftware.openfire.archive.Conversation"%>
<%@ page import="org.jivesoftware.util.JiveGlobals"%>
<%@ page import="org.xmpp.packet.JID"%>
<%@ page import="org.jivesoftware.openfire.user.UserManager"%>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    // Get handle on the Monitoring plugin
    MonitoringPlugin plugin = (MonitoringPlugin)XMPPServer.getInstance().getPluginManager().getPlugin(
            "monitoring");
    ConversationManager conversationManager = (ConversationManager)plugin.getModule(
            ConversationManager.class);

    XMPPServer server = XMPPServer.getInstance();
    UserManager userManager = UserManager.getInstance();
%>

<html>
    <head>
        <title>Conversations</title>
        <meta name="pageID" content="active-conversations"/>
        <script src="/js/prototype.js" type="text/javascript"></script>
        <script src="/js/scriptaculous.js" type="text/javascript"></script>
        <script src="/plugins/monitoring/dwr/engine.js" type="text/javascript" ></script>
        <script src="/plugins/monitoring/dwr/util.js" type="text/javascript" ></script>
        <script src="/plugins/monitoring/dwr/interface/conversations.js" type="text/javascript"></script>
    </head>
    <body>

<style type="text/css">
	@import "style/style.css";
</style>
<script type="text/javascript">
DWREngine.setErrorHandler(handleError);
window.onerror = handleError;
function handleError() {
    // swallow errors: probably caused by the server being down
}

var peConversations = new PeriodicalExecuter(conversationUpdater, 10);

function conversationUpdater() {
    try {
        conversations.getConversations(updateConversations, true);
    } catch(err) {
        // swallow errors
    }
}

function updateConversations(data) {
    conversationsTable = $('conversations');
    rows = conversationsTable.getElementsByTagName("tr");
    // loop over existing rows in the table
    var rowsToDelete = new Array();
    for (i = 0; i < rows.length; i++) {
        // is this a conversation row?
        if (rows[i].id == 'noconversations') {
            rowsToDelete.push(i);
        } else if (rows[i].id != '') {
            // does the conversation exist in update we received?
            convID = rows[i].id.replace('conversation-', '');
            if (data[convID] != undefined) {

                row = rows[i];
                cells = row.getElementsByTagName('td');
                conversation = data[convID];
                if (cells[3].innerHTML != conversation.messageCount) {
                    users = conversation.participant1 + '<br />' + conversation.participant2;
                    cells[0].innerHTML = users;
                    cells[1].innerHTML = conversation.duration;
                    cells[2].innerHTML = conversation.lastActivity;
                    cells[3].innerHTML = conversation.messageCount;
                    new Effect.Highlight(row, {duration: 3.0});
                }
            // doesn't exist in update, delete from table
            } else {
                rowsToDelete.push(i);
            }
        }
    }

    for (i=0; i<rowsToDelete.length; i++) {
        conversationsTable.deleteRow(rowsToDelete[i]);
    }


    // then add any new conversations from the update
    counter = 0;
    for (var c in data) {
        counter++;
        // does this conversation already exist?
        if ($('conversation-' + c) == undefined) {
            conversation = data[c];
            users = conversation.participant1 + '<br />' + conversation.participant2;
            var newTR = document.createElement("tr");
            newTR.setAttribute('id', 'conversation-' + c)
            conversationsTable.appendChild(newTR);
            var TD = document.createElement("TD");
            TD.innerHTML = users;
            newTR.appendChild(TD);

            TD = document.createElement("TD");
            TD.innerHTML = conversation.duration;
            newTR.appendChild(TD);

            TD = document.createElement("TD");
            TD.innerHTML = conversation.lastActivity;
            newTR.appendChild(TD);

            TD = document.createElement("TD");
            TD.innerHTML = conversation.messageCount;
            newTR.appendChild(TD);
        }
    }

    // update activeConversations number
    $('activeConversations').innerHTML = counter;
}

</script>

<!-- <a href="#" onclick="conversationUpdater(); return false;">click me</a> -->
<p>
    <fmt:message key="archive.conversations" />
    <span id="activeConversations"><%= conversationManager.getConversationCount() %></span
</p>

<%
    Collection<Conversation> conversations = conversationManager.getConversations();
%>


<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%" id="conversations">
<thead>
    <tr>
        <th nowrap><fmt:message key="archive.conversations.users" /></th>
        <th nowrap><fmt:message key="archive.conversations.duration" /></th>
        <th nowrap><fmt:message key="archive.conversations.lastactivity" /></th>
        <th nowrap><fmt:message key="archive.conversations.messages" /></th>
    </tr>
</thead>
<tbody>
    <%
        if (conversations.isEmpty()) {
    %>
        <tr id="noconversations">
            <td colspan="4">
                <fmt:message key="archive.converations.no_conversations" />
            </td>
        </tr>

    <%  } %>
    <%
        for (Conversation conversation : conversations) {
            Collection<JID> participants = conversation.getParticipants();
    %>
    <tr id="conversation-<%= conversation.getConversationID()%>">
        <td>
            <% if (conversation.getRoom() == null) { %>
                <% for (JID jid : participants) { %>
                    <% if (server.isLocal(jid) && userManager.isRegisteredUser(jid.getNode())) { %>
                        <a href="/user-properties.jsp?username=<%= jid.getNode() %>"><%= jid %></a><br />
                    <% } else { %>
                        <%= jid.toBareJID() %><br/>
                    <% } %>
                <% } %>
            <% } else { %>
                <fmt:message key="archive.group_conversation">
                    <fmt:param value="<%= "<a href='../../muc-room-occupants.jsp?roomName=" + URLEncoder.encode(conversation.getRoom().getNode(), "UTF-8") + "'>" %>" />
                    <fmt:param value="<%= "</a>" %>" />
                </fmt:message>
            <% } %>
        </td>
        <%
            long duration = conversation.getLastActivity().getTime() -
                    conversation.getStartDate().getTime();
        %>
        <td><%= StringUtils.getTimeFromLong(duration) %></td>
        <td><%= JiveGlobals.formatTime(conversation.getLastActivity()) %></td>
        <td><%= conversation.getMessageCount() %></td>
    </tr>
    <%  } %>
</tbody>
</table>
</div>

</body>
</html>