<%@ page import="java.util.List,
        org.xmpp.packet.JID, java.util.StringTokenizer,
        org.jivesoftware.xmpp.workgroup.WorkgroupManager,
        org.jivesoftware.xmpp.workgroup.DbProperties,
        org.xmpp.component.ComponentManagerFactory,
        org.jivesoftware.xmpp.workgroup.Workgroup,
        java.util.ArrayList,
        org.jivesoftware.xmpp.workgroup.AgentManager,
        org.jivesoftware.xmpp.workgroup.utils.ModelUtil" %>
<html>
<%
    String wgID = request.getParameter("wgID");
    String agents = request.getParameter("agents");
%>
    <head>
        <title>Workgroup Monitors For <%= wgID%></title>
        <meta name="subPageID" content="workgroup-monitors"/>
        <meta name="extraParams" content="<%= "wgID="+wgID %>"/>


          <!--<meta name="helpPage" content="edit_queue_properties.html"/>-->

        <script language="JavaScript" type="text/javascript">
        function openWin(el) {
            var win = window.open('user-browser.jsp?formName=f&elName=agents', 'newWin', 'width=500,height=550,menubar=yes,location=no,personalbar=no,scrollbars=yes,resize=yes');
        }

        function openAgentGroupWindow(el) {
            var agentwin = window.open('agent-group-browser.jsp?formName=f&elName=agentGroups', 'newWin', 'width=500,height=550,menubar=yes,location=no,personalbar=no,scrollbars=yes,resize=yes');

        }
        </script>
    </head>
<%
    boolean errors = false;
    WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
    AgentManager aManager = workgroupManager.getAgentManager();

    Workgroup workgroup = WorkgroupManager.getInstance().getWorkgroup(new JID(wgID));
    DbProperties props = workgroup.getProperties();
    String users = props.getProperty("monitors");

    StringBuffer buf = new StringBuffer();
    List list = new ArrayList();
    if (agents != null) {
        // Save monitiors
        StringTokenizer tkn = new StringTokenizer(agents, ",");
        while (tkn.hasMoreTokens()) {
            String tok = tkn.nextToken();

            // Check if user is agent
            if (tok.indexOf('@') == -1) {
                tok += ("@" + ComponentManagerFactory.getComponentManager().getServerName());
            }
            try {
                JID address = new JID(tok.trim());
                boolean exists = aManager.hasAgent(address);
                if (exists) {
                    String bareJID = address.toBareJID();
                    if (!list.contains(bareJID)) {
                        buf.append(bareJID);
                        if (tkn.hasMoreTokens()) {
                            buf.append(",");
                        }
                        list.add(bareJID);
                    }
                }
            }
            catch (IllegalArgumentException e) {

            }
        }

        users = buf.toString();
        if (users.endsWith(",") && users.length() > 1) {
            users = users.substring(0, users.length() - 1);
        }

        // Save users
        if (ModelUtil.hasLength(users)) {
            System.out.println(users);
            props.setProperty("monitors", users);
        }
        else {
            errors = true;
        }
    }


    if (users == null) {
        users = "";
    }
%>


<body>

<% if (errors) { %>
 <div class="jive-error">
            <table cellpadding="0" cellspacing="0" border="0">
                <tbody>
                    <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16"
                                                   border="0"></td>
                        <td class="jive-icon-label">
                           Unable to update Room Monitors.
                        </td></tr>
                </tbody>
            </table>
        </div><br>
<%}
else if (agents != null) {%>
 <div class="jive-success">
            <table cellpadding="0" cellspacing="0" border="0">
                <tbody>
                    <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16"
                                                   border="0"></td>
                        <td class="jive-icon-label">
                          Room Monitors have been updated.
                        </td></tr>
                </tbody>
            </table>
        </div><br>

<% } %>
<form name="f" method="post" action="workgroup-monitors.jsp">
       <div class="jive-contentBoxHeader">
        Conversation Monitors
        </div>
<table  cellpadding="3" cellspacing="0" border="0"  class="jive-contentBox">

    <tr valign="top">
        <td width="35%"><b>Specify Workgroup Monitors</b><br/><span class="jive-description">
            Specify a comma-delimited list of monitors.<br/><br/> Monitors are users who are allowed to listen in on others
            conversations without showing their presence in the conversation.
        </span></td>
   <td width="1%"><textarea name="agents" cols="40" rows="5" wrap="virtual"><%= users%></textarea></td>

    <td nowrap valign="top" width="1%" align="right">
    <a href="#" onclick="openWin(document.f.agents);return false;" title="Click to browse available agents...">
        <img src="/images/add-16x16.gif" border="0"/></a>
    </td><td nowrap valign="top" align="left">
    <a href="#" onclick="openWin(document.f.agents);return false;" title="Click to browse available agents...">
        Add Agents</a>
    </td>
 </tr>

    <tr><td colspan="4"><input type="submit" name="submit" value="Submit"></td></tr>
</table>
    <input type="hidden" name="wgID" value="<%= wgID%>"/>
    </form>
</body>
</html>