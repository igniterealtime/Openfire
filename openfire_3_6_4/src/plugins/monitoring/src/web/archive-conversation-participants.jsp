<%@ page import="org.jivesoftware.openfire.plugin.MonitoringPlugin" %>
<%@ page import="org.jivesoftware.openfire.archive.Conversation" %>
<%@ page import="org.jivesoftware.openfire.archive.ConversationManager" %>
<%@ page import="org.jivesoftware.openfire.archive.ConversationParticipation" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.user.UserManager" %>
<%@ page import="org.jivesoftware.util.Log" %>
<%@ page import="org.jivesoftware.util.NotFoundException" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="java.util.*" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    long conversationID = ParamUtils.getLongParameter(request, "conversationID", -1);
    int start = ParamUtils.getIntParameter(request, "start", 0);

    // Get handle on the Monitoring plugin
    XMPPServer server = XMPPServer.getInstance();
    UserManager userManager = server.getUserManager();
    MonitoringPlugin plugin = (MonitoringPlugin) server.getPluginManager().getPlugin("monitoring");

    ConversationManager conversationmanager = (ConversationManager) plugin.getModule(ConversationManager.class);
    List<String[]> values = new ArrayList<String[]>();
    JID room = null;
    try {
        Conversation conversation = conversationmanager.getConversation(conversationID);
        List<JID> participants = new ArrayList<JID>(conversation.getParticipants());
        for (JID user : participants) {
            for (ConversationParticipation participation : conversation.getParticipations(user)) {
                values.add(new String[]{participation.getNickname(), user.toString()});
            }
        }
        Collections.sort(values, new Comparator<String[]>() {
            public int compare(String[] o1, String[] o2) {
                return o1[0].compareTo(o2[0]);
            }
        });
        room = conversation.getRoom();
    }
    catch (NotFoundException e) {
        Log.error("Conversation not found: " + conversationID, e);
    }
    // paginator vars
    int range = 16;
    int numPages = (int) Math.ceil((double) (values.size() / 2) / (double) (range / 2));
    int curPage = (start / range) + 1;
%>
<html>
<head>
<meta name="decorator" content="none"/>
</head>
<body>
<script type="text/javascript" language="javascript" src="scripts/tooltips/domTT.js"></script>
<script type="text/javascript" language="javascript" src="scripts/tooltips/domLib.js"></script>
<style type="text/css">
#lightbox{
	top: 20%;
	margin-top: -20px;
	}

.jive-testPanel {
	display: block;
	position: relative;
	float: left;
	margin: 0;
	padding: 0;
	border: 2px solid #666666;
	background-color: #f8f7eb;
	overflow: hidden;
	z-index: 9997;
	-moz-border-radius: 6px;
	}
.jive-testPanel-content {
	display: block;
	float: left;
	padding: 20px;
	font-size: 8pt;
	z-index: 9999;
	}
.jive-testPanel-close a,
.jive-testPanel-close a:visited {
	float: right;
	color: #666;
	padding: 2px 5px 2px 18px;
	margin: 0;
	font-size: 8pt;
	background: transparent url(../../../images/setup_btn_closetestx.gif) no-repeat left;
	background-position: 4;
	border: 1px solid #ccc;
	z-index: 9999;
	}
.jive-testPanel-close a:hover {
	background-color: #e9e8d9;
	}
.jive-testPanel-content h2 {
	font-size: 14pt;
	color: #396b9c;
	margin: 0 0 10px 0;
	padding: 0;
	}
.jive-testPanel-content h2 span {
	font-size: 10pt;
	color: #000;
	}
.jive-testPanel-content h4 {
	font-size: 12pt;
	margin: 0 0 10px 0;
	padding: 0;
	}
.jive-testPanel-content h4.jive-testSuccess {
	color: #1e7100;
	}
.jive-testPanel-content h4.jive-testError {
	color: #890000;
	}
</style>

<!-- BEGIN connection settings test panel -->
<div class="jive-testPanel">
	<div class="jive-testPanel-content">

		<div align="right" class="jive-testPanel-close">
			<a href="#" class="lbAction" rel="deactivate"><fmt:message key="archive.group_conversation.close" /></a>
		</div>


		<h2><fmt:message key="archive.group_conversation.participants.title"/></h2>

		<p><fmt:message key="archive.group_conversation.participants.description">
                <fmt:param value="<%= room != null ? "<b>"+room.getNode()+"</b>" : "" %>" />
            </fmt:message>
        </p>

        <fmt:message key="archive.group_conversation.participants" />: <b><%= values.size() %></b>

        <%  if (numPages > 1) { %>

            -- <fmt:message key="global.showing" /> <%= (start+1) %>-<%= (start+range) %>

        <%  } %>

        <%  if (numPages > 1) { %>

            <p>
            <fmt:message key="global.pages" />:
            [
            <%  for (int i=0; i<numPages; i++) {
                    String sep = ((i+1)<numPages) ? " " : "";
                    boolean isCurrent = (i+1) == curPage;
            %>
                <a href="#" rel="deactivate" onclick="showOccupants('<%=conversationID%>', <%=(i*range)%>);return false;" class="<%= ((isCurrent) ? "jive-current" : "") %>"><%= (i+1) %></a><%= sep %>
            <%  } %>
            ]

        <%  } %>
        <div class="jive-table">
        <table cellpadding="0" cellspacing="0" border="0" width="100%">
        <thead>
            <tr>
                <th colspan="2"><fmt:message key="archive.group_conversation.participants.participant" /></th>
            </tr>
        </thead>
        <tbody>
            <% if (!values.isEmpty()) {
                int from = (curPage-1) * range;
                int to = curPage * range;
                // Check ranges
                from = from > values.size() ? values.size() : from;
                to = to > values.size() ? values.size() : to;
                // Get subset of participants to display 
                values = values.subList(from, to);
                for (Iterator<String[]> it = values.iterator(); it.hasNext();) {
                    String[] participation = it.next();
                    String nickname = participation[0];
                    JID participant = new  JID(participation[1]);
            %>
            <tr>
                
                <td><%=nickname%> <i>(<%= server.isLocal(participant) && userManager.isRegisteredUser(participant) ? "<a href='/user-properties.jsp?username=" + participant.getNode() + "'>" + participant.toBareJID() + "</a>" : participant.toBareJID() %>)</i></td>

                <% if (it.hasNext()) {
                    participation = it.next();
                    nickname = participation[0];
                    participant = new  JID(participation[1]);
                %>
                <td><%=nickname%> <i>(<%= server.isLocal(participant) && userManager.isRegisteredUser(participant) ? "<a href='/user-properties.jsp?username=" + participant.getNode() + "'>" + participant.toBareJID() + "</a>" : participant.toBareJID() %>)</i></td>
                <% } else { %>
                <td>&nbsp;</td>
                <% } %>
            </tr>
            <% } } else { %>
            <tr>
                <td colspan="3"><fmt:message key="archive.group_conversation.participants.empty" /></td>
            </tr>
            <% } %>
        </tbody>
        </table>
        </div>
	</div>
</div>

</body>
</html>
