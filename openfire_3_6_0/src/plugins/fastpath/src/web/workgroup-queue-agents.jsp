<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%--
  -	$RCSfile$
  -	$Revision: 32924 $
  -	$Date: 2006-08-04 15:03:08 -0700 (Fri, 04 Aug 2006) $
--%>
<%@ page
        import="org.jivesoftware.xmpp.workgroup.Agent,
                org.jivesoftware.xmpp.workgroup.AgentManager,
                org.jivesoftware.xmpp.workgroup.RequestQueue,
                org.jivesoftware.xmpp.workgroup.Workgroup,
                org.jivesoftware.xmpp.workgroup.WorkgroupAdminManager,
                org.jivesoftware.xmpp.workgroup.WorkgroupManager,
                org.jivesoftware.xmpp.workgroup.dispatcher.DispatcherInfo,
                org.jivesoftware.util.ParamUtils,
                org.jivesoftware.openfire.group.Group,
                org.jivesoftware.openfire.group.GroupManager,
                org.xmpp.component.ComponentManagerFactory"
        errorPage="workgroup-error.jsp" %>
<%@ page import="org.xmpp.packet.JID" %><%@ page import="java.util.HashMap" %><%@ page import="java.util.Iterator" %><%@ page import="java.util.LinkedList" %><%@ page import="java.util.List" %><%@ page import="java.util.Map" %><%@ page import="java.util.StringTokenizer" %>
<%@ page import="org.jivesoftware.xmpp.workgroup.AgentNotFoundException"%>
<%@ page import="org.jivesoftware.util.Log"%><%@ page import="org.jivesoftware.openfire.group.GroupNotFoundException"%>
<%@ page import="org.jivesoftware.openfire.user.UserManager"%>
<%
    // Get parameters //
    String wgID = ParamUtils.getParameter(request, "wgID");
    long queueID = ParamUtils.getLongParameter(request, "qID", -1L);
    String agentGroups = ParamUtils.getParameter(request, "agentGroups");
    String agents = ParamUtils.getParameter(request, "agents");
    String success = ParamUtils.getParameter(request, "success");


    boolean addAgents = request.getParameter("addAgents") != null;
    boolean addGroups = request.getParameter("addGroups") != null;
    boolean removeAgents = request.getParameter("removeUsers") != null;
    boolean removeGroups = request.getParameter("removeGroup") != null;

    WorkgroupManager wgManager = WorkgroupManager.getInstance();
    WorkgroupAdminManager adminManager = new WorkgroupAdminManager();
    GroupManager groupManager = GroupManager.getInstance();

    // Load the workgroup
    Workgroup workgroup = wgManager.getWorkgroup(new JID(wgID));
    AgentManager agentManager = wgManager.getAgentManager();
    // Load the request queue:
    RequestQueue queue = workgroup.getRequestQueue(queueID);
    //AgentSelector newSelector = null;

    DispatcherInfo infos = queue.getDispatcher().getDispatcherInfo();
    long requestTimeout = infos.getRequestTimeout() / 1000;
    long offerTimeout = infos.getOfferTimeout() / 1000;

    String successMessage = null;
%>




<%


    Map errors = new HashMap();

    if(addGroups && agentGroups == null){
        errors.put("groups", "Please specify a valid group name.");
    }



    if(addGroups && errors.size() == 0){
        StringTokenizer tokenizer = new StringTokenizer(agentGroups, ",\t\n\r\f");
        while (tokenizer.hasMoreTokens()) {
            String tok = tokenizer.nextToken().trim();
            try {
                Group group = groupManager.getGroup(tok);
                queue.addGroup(group);
            }
            catch (Exception e) {
                errors.put("groups", "Group not found.");
                ComponentManagerFactory.getComponentManager().getLog().error(e);
            }
        }

        successMessage = "Group(s) have been added to queue.";
    }
    else if (addAgents && errors.size() == 0) {
        if (agents != null) {
            // loop thru all params
            StringTokenizer tokenizer = new StringTokenizer(agents, ", \t\n\r\f");
            while (tokenizer.hasMoreTokens()) {
                String usernameToken = tokenizer.nextToken();
                if (usernameToken.indexOf('@') != -1) {
                    usernameToken = JID.escapeNode(usernameToken);
                }
                try {
                    // See if they are a user in the system.
                    UserManager.getInstance().getUser(usernameToken);
                    usernameToken += ("@" + ComponentManagerFactory.getComponentManager().getServerName());
                    JID address = new JID(usernameToken.trim());
                    Agent agent = null;

                    if (agentManager.hasAgent(address)) {
                        agent = agentManager.getAgent(address);
                    }
                    else {
                        agent = agentManager.createAgent(address);
                    }
                    queue.addMember(agent);
                }
                catch (Exception e) {
                    if (!errors.containsKey("agents")) {
                        errors.put("agents", new LinkedList());
                    }
                    List list = (List)errors.get("agents");
                    list.add(usernameToken);
                }

                successMessage = "Agent(s) have been added to the queue.";
            }

        }

        if(errors.size() == 0){
        response.sendRedirect("workgroup-queue-agents.jsp?success=true&wgID=" + wgID + "&qID=" + queue.getID());
        return;
        }
    }

    if(removeAgents){
        String[] agentID = request.getParameterValues("remove");
        final int no = agentID != null ? agentID.length : 0;
        for(int i=0; i<no; i++){
            try {
                long id = Long.parseLong(agentID[i]);
                Agent agent = agentManager.getAgent(id);
                queue.removeMember(agent);
            }
            catch (AgentNotFoundException e1) {
                Log.error(e1);
            }
        }

       successMessage = "Agent(s) have been removed from the queue.";
    }

    if(removeGroups){
        String[] groups = request.getParameterValues("groupRemove");
        final int no = groups != null ? groups.length : 0;
        for (int i = 0; i < no; i++) {
            try {
                String groupName = groups[i];
                Group group = groupManager.getGroup(groupName);
                queue.removeGroup(group);
            }
            catch (GroupNotFoundException e1) {
                Log.error(e1);
            }
        }

        successMessage = "Group(s) have been removed from the queue.";
    }

    RequestQueue backupQueue = null;

    if (errors.size() == 0) {
        DispatcherInfo dispatcherInfo = queue.getDispatcher().getDispatcherInfo();
        agentGroups = "";


        for (Iterator iter = queue.getGroups().iterator(); iter.hasNext();) {
            Group ag = (Group)iter.next();
            String sep = (iter.hasNext() ? ", " : "");
            agentGroups += (ag.getName() + sep);
        }
        agents = "";

        for (Iterator iter = queue.getMembers().iterator(); iter.hasNext();) {
            Agent a = (Agent)iter.next();
            String sep = (iter.hasNext() ? ", " : "");
            agents += (a.getAgentJID().toString() + sep);
        }
    }

    String overFlowDescription = "";
    RequestQueue.OverflowType overflowType = queue.getOverflowType();
    if (overflowType == RequestQueue.OverflowType.OVERFLOW_BACKUP) {
        overFlowDescription = "Overflow to " + queue.getBackupQueue().getName();
    }
    else if (overflowType == RequestQueue.OverflowType.OVERFLOW_NONE) {
        overFlowDescription = "No Overflow.";
    }
    else {
        overFlowDescription = "Overflow to random queue.";
    }
%>


<html>
    <head>
        <title>Manage Queue - <%= queue.getName()%></title>
        <meta name="subPageID" content="workgroup-queues"/>
        <meta name="extraParams" content="wgID=<%= wgID %>"/>
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
    <body>

    <p>
        <b>Description:&nbsp;</b><%= queue.getDescription() != null ? queue.getDescription() : ""%>
    </p>
<p>
    Use the form below to add agents and/or groups to this queue. All users specified will be marked as agents that are
    able to take incoming chat requests to this queue.
</p>
<p>
<a href="workgroup-queues.jsp?wgID=<%= wgID %>"
        >&laquo; Back to list of queues in workgroup.</a>
</p>


<% if (successMessage != null && errors.size() == 0) { %>
<div class="success">
    <%= successMessage%>
</div>
<br/>
<% } %>
<%  if (errors.size() > 0) { %>

    <div class="error">
    Please fix the errors below.
    </div>

<%  } %>

 <table width="100%" class="jive-table" cellpadding="3" cellspacing="0" border="0">
    <th colspan="2">Queue Settings</th>
     <tr>
         <td width="1%" nowrap>Offer Timeout:</td>
         <td><%= offerTimeout%> seconds</td>
     </tr>
     <tr>
         <td width="1%" nowrap>Request Timeout:</td>
         <td><%= requestTimeout%> seconds.</td>
     </tr>
     <tr>
         <td width="1%" nowrap>Overflow Policy:</td>
         <td><%= overFlowDescription%></td>
     </tr>
     <tr>
         <td colspan="2"><input type="button" name="edit" value="Edit Settings" onclick="window.location.href='workgroup-queue-manage.jsp?wgID=<%=wgID%>&qID=<%= queueID%>'"/></td>
     </tr>
</table>

<br/>


<form action="workgroup-queue-agents.jsp" method="post" name="f">
<input type="hidden" name="wgID" value="<%= wgID %>">
<input type="hidden" name="qID" value="<%= queueID %>">



<table cellpadding="3" cellspacing="0" border="0">
<tr valign="top">
    <td class="c1">
        <b>Add Agent(s):</b>
        <%  if (errors.get("agents") != null) { %>
            &nbsp;
            <span class="jive-error-text">
            Agent not found.
            </span>
        <%  } %>
        <br/>
    </td>
    <td>
        <input type="text" name="agents" size="40"/>&nbsp;<input type="submit" name="addAgents" value="Add"/>

    </td>

    <td>

                <a href="#" onclick="openWin(document.f.agents);return false;"
                   title="Click to browse available agents..."
                        >Browse...</a>

    </td>

</table>

<!-- List Agents -->
    <table width="100%" class="jive-table" cellpadding="3" cellspacing="0" border="0">
       <tr>
            <th>Username</th><th>Remove</th>
       </tr>
        <% for(Agent agent : queue.getMembers()){ %>
            <tr>
                <td><%= JID.unescapeNode(agent.getAgentJID().getNode())%></td>
                <td><input type="checkbox" name="remove" value="<%= agent.getID()%>"></td>
            </tr>
        <% } %>
        <tr><td>&nbsp;</td><td><input type="submit" name="removeUsers" value="Remove" /></td></tr>

     </table>
    <br/>
     <table cellpadding="3" cellspacing="0" border="0">
        <tr valign="top">
             <td class="c1">
        <b>Add Groups(s):</b>
            <%  if (errors.get("groups") != null) { %>

            <br/>
            <span class="jive-error-text">
            <%= errors.get("groups") %>
            </span>

        <%  } %>
            </td>
            <td>
                <input type="text" name="agentGroups" size="40">&nbsp;
                <input type="submit" name="addGroups" value="Add"/>
            </td>
             <td nowrap valign="top">
                <a href="#" onclick="openAgentGroupWindow(document.f.agentGroups);return false;"
                   title="Click to browse available agent groups..."
                        >Browse...</a>
            </td>
        </tr>
        </table>
<!-- List  Groups-->
    <table width="100%" class="jive-table" cellpadding="3" cellspacing="0" border="0">
       <tr>
            <th>Group</th><th>Remove</th>
       </tr>
        <%
            boolean hasGroup = false;
            for(Group group : queue.getGroups()){
            hasGroup = true;
        %>
            <tr>
                <td><%= group.getName() %></td>
                <td><input type="checkbox" name="groupRemove" value="<%= group.getName()%>"></td>
            </tr>
        <% } %>
        <% if(hasGroup){ %>
        <tr><td>&nbsp;</td><td><input type="submit" name="removeGroup" value="Remove" /></td></tr>
        <% } %>
     </table>

</form>

<script type="text/javascript">
    document.f.agents.focus();
</script>
</body>
</html>