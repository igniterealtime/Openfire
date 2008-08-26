<%@ page import="org.jivesoftware.openfire.plugin.MonitoringPlugin" %>
<%@ page import="org.jivesoftware.openfire.archive.ArchivedMessage" %>
<%@ page import="org.jivesoftware.openfire.archive.Conversation" %>
<%@ page import="org.jivesoftware.openfire.archive.ConversationManager" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.util.*"%><%@ page import="java.util.Collection"%>
<%!
     Map<String, String> colorMap = new HashMap<String, String>();
%>
<%
    // Get handle on the Monitoring plugin
    MonitoringPlugin plugin = (MonitoringPlugin)XMPPServer.getInstance().getPluginManager().getPlugin(
        "monitoring");

    ConversationManager conversationManager = (ConversationManager)plugin.getModule(ConversationManager.class);

    long conversationID = ParamUtils.getLongParameter(request, "conversationID", -1);

    Conversation conversation = null;
    if (conversationID > -1) {
        try {
            conversation = new Conversation(conversationManager, conversationID);
        }
        catch (NotFoundException nfe) {
            Log.error(nfe);
        }
    }

    Map<String, String> colorMap = new HashMap<String, String>();

    if (conversation != null) {
        Collection<JID> set = conversation.getParticipants();
        int count = 0;
        for (JID jid : set) {
            if (count == 0) {
                colorMap.put(jid.toBareJID(), "blue");
            }
            else {
                colorMap.put(jid.toBareJID(), "red");
            }
            count++;
        }
    }

%>

<html>
<head>
    <meta name="decorator" content="none"/>

    <title>Conversation Viewer</title>

    <style type="text/css">
	    @import "style/style.css";
    </style>
</head>

<body>

<%
    if (conversation != null) {
%>

<table width="100%">
    <% for (ArchivedMessage message : conversation.getMessages()) { %>
    <tr valign="top">
        <td width="1%" nowrap class="jive-description" style="color:<%= getColor(message.getFromJID()) %>">
            [<%= JiveGlobals.formatTime(message.getSentDate())%>] <%= message.getFromJID().getNode()%>:</td>
        <td><span class="jive-description"><%= StringUtils.escapeHTMLTags(message.getBody())%></span></td>
    </tr>
    <%}%>

</table>

<% }
else { %>
No conversation could be found.
<% } %>


<%!
    String getColor(JID jid){
        String color = colorMap.get(jid.toBareJID());
        if(color == null){
            Log.debug("Unable to find "+jid.toBareJID()+" using "+colorMap);
        }
        return color;
    }
%>
</body>
</html>