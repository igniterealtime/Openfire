<%--
   -	$RCSfile$
   -	$Revision: 32926 $
   -	$Date: 2006-08-04 15:39:24 -0700 (Fri, 04 Aug 2006) $
--%>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ page import="org.jivesoftware.xmpp.workgroup.RequestQueue,
                 org.jivesoftware.xmpp.workgroup.Workgroup,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.xmpp.workgroup.WorkgroupManager,
                 org.jivesoftware.xmpp.workgroup.WorkgroupAdminManager,
                 org.xmpp.packet.JID,
                 org.jivesoftware.openfire.fastpath.dataforms.FormManager,
                 org.jivesoftware.xmpp.workgroup.routing.RoutingManager,
                 org.jivesoftware.xmpp.workgroup.routing.RoutingRule,
                 org.jivesoftware.openfire.fastpath.dataforms.WorkgroupForm,
                 java.util.Collection,
                 java.util.ArrayList,
                 java.util.List,
                 java.util.StringTokenizer,
                 org.jivesoftware.xmpp.workgroup.utils.ModelUtil,
                 org.jivesoftware.util.StringUtils,
                 org.jivesoftware.util.NotFoundException,
                 org.jivesoftware.util.Log,
                 org.jivesoftware.openfire.fastpath.dataforms.FormUtils,
                 org.xmpp.forms.FormField, org.xmpp.forms.DataForm"
        errorPage="/error.jsp"%>

<%
    String wgID = ParamUtils.getParameter(request, "wgID");
    long qID = ParamUtils.getLongParameter(request, "qID", -1L);
    boolean createQueue = request.getParameter("createQueue") != null;
    String name = ParamUtils.getParameter(request, "name");
    String description = ParamUtils.getParameter(request, "description");
    boolean delete = ParamUtils.getBooleanParameter(request, "delete");

    String errorMessage = "";
%>

<%
    // Get a workgroup manager
    WorkgroupManager wgManager = WorkgroupManager.getInstance();
    WorkgroupAdminManager adminManager = new WorkgroupAdminManager();

    // If the workgroup manager is null, service is down so redirect:
    if (wgManager == null) {
        response.sendRedirect("error-serverdown.jsp");
        return;
    }
%>

<% // Get parameters //

    // Load the workgroup
    Workgroup workgroup = wgManager.getWorkgroup(new JID(wgID));

    if (createQueue) {
        RequestQueue queue = workgroup.createRequestQueue(name);
        queue.setDescription(description);
        response.sendRedirect("workgroup-queues.jsp?wgID=" + wgID);
        return;
    }

    if (delete) {
        RequestQueue queue = workgroup.getRequestQueue(qID);
        workgroup.deleteRequestQueue(queue);
        response.sendRedirect("workgroup-queues.jsp?wgID=" + wgID + "&deletesuccess=true");
        return;
    }
%>


<%
    final WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
    FormManager formManager = FormManager.getInstance();
    DataForm dataForm = formManager.getDataForm(workgroup);

    RoutingManager routingManager = RoutingManager.getInstance();

    WorkgroupForm form = formManager.getWebForm(workgroup);

    Collection<RoutingRule> rules = routingManager.getRoutingRules(workgroup);
    boolean errors = false;

    boolean edit = request.getParameter("edit") != null;
    int pos = ParamUtils.getIntParameter(request, "pos", -1);
    String editVariable = request.getParameter("editVariable");
    String editValue = request.getParameter("editValue");
    String editQuery = request.getParameter("editQueryField");
    boolean editAdvancedQuery = request.getParameter("editAdvancedQuery") != null;
    long editQueueID = ParamUtils.getLongParameter(request, "editQueueID", 0);


    String variable = request.getParameter("variable");
    String value = request.getParameter("value");

    if (value == null) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < 50; i++) {
            String tempValue = request.getParameter("value" + i);
            if (tempValue != null) {
                buf.append(variable + ":" + tempValue);
                if (request.getParameter("value" + (i + 1)) != null) {
                    buf.append(" AND ");
                }
            }
        }

        if (buf.length() > 0) {
            value = buf.toString();
            variable = "";
        }
    }
    long queueID = ParamUtils.getLongParameter(request, "queueID", -1);


    String query = "";

    boolean advancedBuilder = "advancedBuilder".equals(request.getParameter("selector"));
    String advancedQuery = request.getParameter("advancedQuery");

    if (edit) {
        List routers = (ArrayList)routingManager.getRoutingRules(workgroup);
        RoutingRule rule = (RoutingRule)routers.get(pos - 1);

        StringTokenizer tkn = new StringTokenizer(rule.getQuery(), ":");
        if (tkn.countTokens() == 2) {
            variable = tkn.nextToken();
            value = StringUtils.escapeForXML(tkn.nextToken());
        }
        else {
            advancedBuilder = true;
            variable = StringUtils.escapeForXML(rule.getQuery());
        }


        queueID = rule.getQueueID();
    }


    boolean handleEditForm = request.getParameter("editRule") != null;

    if (handleEditForm) {
        if (editAdvancedQuery) {
            if (!org.jivesoftware.xmpp.workgroup.utils.ModelUtil.hasLength(editQuery)) {
                errors = true;
                errorMessage = "Your query cannot be empty";
            }

            if (!errors) {
                int editPos = ParamUtils.getIntParameter(request, "editPos", -1);
                routingManager.removeRoutingRule(workgroup, editPos);

                routingManager.addRoutingRule(workgroup, editQueueID, editPos, editQuery);
            }

        }

        else {

            if (!ModelUtil.hasLength(editValue)) {
                errors = true;
                errorMessage = "Please specify a value to map to the form variable.";
            }

            if (!errors) {
                int editPos = ParamUtils.getIntParameter(request, "editPos", -1);
                routingManager.removeRoutingRule(workgroup, editPos);

                query = editVariable + ":" + editValue;
                routingManager.addRoutingRule(workgroup, editQueueID, editPos, query);
            }
        }
    }


    boolean submit = request.getParameter("submit") != null;
    if (submit) {
        if (!ModelUtil.hasLength(value) && !advancedBuilder) {
            errors = true;
            errorMessage = "Please specify a value to map to the form variable.";
        }
        else if (advancedBuilder && !ModelUtil.hasLength(advancedQuery)) {
            errors = true;
            errorMessage = "Specify a valid query.";
        }

        if (!errors) {
            // Add Rule
            if (!advancedBuilder) {
                if (variable.length() > 0) {
                    query = variable + ":" + value;
                }
                else {
                    query = value;
                }
                routingManager.addRoutingRule(workgroup, queueID, rules.size() + 1, query);
            }
            else {
                routingManager.addRoutingRule(workgroup, queueID, rules.size() + 1, advancedQuery);
            }
        }
    }


    boolean changePos = request.getParameter("changePos") != null;
    boolean remove = request.getParameter("remove") != null;


    if (changePos) {
        boolean up = request.getParameter("up") != null;
        boolean down = request.getParameter("down") != null;
        String index = request.getParameter("pos");
        int routerIndex = Integer.parseInt(index);

        RoutingRule moveUpRule = null;
        RoutingRule moveDownRule = null;
        if (up) {
            // Change selected router index to pos - 1 and
            // change pos - 1 to pos + 1 and save.
            for (RoutingRule rule : rules) {
                if (rule.getPosition() == routerIndex) {
                    moveUpRule = rule;
                }

                if (rule.getPosition() == routerIndex - 1) {
                    moveDownRule = rule;
                }
            }

            // Delete both rules and reapply
            routingManager.removeRoutingRule(workgroup, routerIndex);
            routingManager.removeRoutingRule(workgroup, routerIndex - 1);

            // Add new rules
            moveUpRule.setPosition(routerIndex - 1);
            moveDownRule.setPosition(routerIndex);

        }
        else if (down) {
            for (RoutingRule rule : rules) {
                if (rule.getPosition() == routerIndex) {
                    moveUpRule = rule;
                }

                if (rule.getPosition() == routerIndex + 1) {
                    moveDownRule = rule;
                }
            }

            // Delete both rules and reapply
            routingManager.removeRoutingRule(workgroup, routerIndex);
            routingManager.removeRoutingRule(workgroup, routerIndex + 1);

            // Add new rules
            moveUpRule.setPosition(routerIndex + 1);
            moveDownRule.setPosition(routerIndex);
        }

        routingManager.addRoutingRule(workgroup, moveUpRule.getQueueID(), moveUpRule.getPosition(),
                moveUpRule.getQuery());
        routingManager.addRoutingRule(workgroup, moveDownRule.getQueueID(),
                moveDownRule.getPosition(), moveDownRule.getQuery());
    }

    if (remove) {
        String index = request.getParameter("pos");
        int routerIndex = Integer.parseInt(index);
        routingManager.removeRoutingRule(workgroup, routerIndex);
        for (RoutingRule rule : rules) {
            if (rule.getPosition() > routerIndex) {
                routingManager
                        .updateRoutingRule(workgroup, rule.getPosition(), rule.getPosition() - 1);
            }
        }

    }


    rules = routingManager.getRoutingRules(workgroup);

%>
<html>
<head>
    <title><%= "Workgroup Queues for " + wgID%></title>
    <meta name="subPageID" content="workgroup-queues"/>
    <meta name="extraParams" content="<%= "wgID="+wgID %>"/>
    <!--<meta name="helpPage" content="create_a_queue.html"/>-->

    <script type="text/javascript">
        function enableDefault() {

            document.getElementById('advancedField').disabled = true;
        }

        function enableAdvanced() {

            document.getElementById('advancedField').disabled = false;
        }

        function updateForm(selectbox){
          window.location.href = "workgroup-queues.jsp?wgID=<%= wgID%>&fElement="+selectbox.value;
        }
    </script>
</head>

<body>
<%
    boolean added = ParamUtils.getBooleanParameter(request, "queueaddsuccess");
    boolean deleted = ParamUtils.getBooleanParameter(request, "deletesuccess");

%>
<% if (errors) { %>
<div class="error">
    <%= errorMessage%>
</div><br/>
<% } %>

<%  if (added) { %>
<div class="success">
    A new Request Queue has been added.
</div><br>
<%  }
else if (deleted) { %>

<div class="success">
    Request Queue has been removed.
</div><br>
<%  } %>

<% if (handleEditForm && !errors) { %>
<div class="success">
    Routing rules have been updated.
</div>
<% } %>


<% if (!errors && submit) { %>
<div class="success">
    New routing rule has been added.
</div>
<% } %>

<p>
    A request queue handles incoming client support requests. To add members to a queue, click on an available queue below.
</p>

<table>
    <tr>
        <td>
            <a href="workgroup-queue-create.jsp?wgID=<%= wgID %>"><img src="/images/add-16x16.gif" width="16" height="16" border="0"></a>
        </td>
        <td>
            <a href="workgroup-queue-create.jsp?wgID=<%= wgID %>">Add Queue</a>
        </td>
    </tr>
</table>

<br/>


<table class="jive-table" cellpadding="3" cellspacing="0" border="0">
    <tr>
        <th nowrap align="left" colspan="2">Name/Description</th>
        <th nowrap>Agents (active/total)</th>
        <th nowrap>In Queue</th>
        <th nowrap>Avg. Wait Time (sec)</th>
        <th nowrap>Edit</th>
        <th nowrap>Delete</th>
    </tr>
    <%
        int requestCount = workgroup.getRequestQueueCount();
        if (requestCount == 0) {
    %>
    <tr>
        <td colspan="98">
            No queues.
        </td>
    </tr>
    <%
        }
        int i = 0;
        for (RequestQueue requestQueue : workgroup.getRequestQueues()) {
            i++;
    %>
    <tr>
        <td width="1%" valign="top" nowrap>
            <%= i %>.
        </td>
        <td width="37%">
            <a href="workgroup-queue-agents.jsp?wgID=<%= wgID %>&qID=<%= requestQueue.getID() %>"
               title="Click to add/remove Agents and Groups."
                ><b><%= requestQueue.getName() %></b></a>

            <%  if (requestQueue.getDescription() != null) { %>

            <br>
            <span class="jive-description">
                <%= requestQueue.getDescription() %>
                </span>

            <%  } %>
        </td>
        <td width="15%" align="center">
            <%= requestQueue.getAgentSessionList().getAvailableAgentCount() %>
            /
            <%= requestQueue.getMemberCount() %>
        </td>
        <td width="15%" align="center">
            <%= requestQueue.getRequestCount() %>
        </td>
        <td width="15%" align="center">
            <%= requestQueue.getAverageTime() %>
        </td>
        <td width="1%" align="center">
            <a href="workgroup-queue-agents.jsp?wgID=<%= wgID %>&qID=<%= requestQueue.getID() %>"
               title="Click to manage this queue..."
                ><img src="images/edit-16x16.gif" width="16" height="16" border="0"></a>
        </td>
        <td width="1%" align="center">
            <a href="workgroup-queues.jsp?wgID=<%= wgID %>&qID=<%= requestQueue.getID() %>&delete=true"
               title="Click to delete this queue..."
               onclick="return confirm('Are you sure you want to delete this queue?');"
                ><img src="images/delete-16x16.gif" width="16" height="16" border="0"></a>
        </td>
    </tr>

    <%  } %>

</table>

<br/><br/>
<b>Routing Rules</b>
<br/>

<p>
    Specify which queue to route to based on the values assigned to the form variables in the Web Chat Client.
</p>

<table class="jive-table" cellspacing="0" width="100%">
    <th>Order</th><th>Query</th><th>Routes to Queue</th><th>Move</th><th>Edit</th><th>Delete</th>

    <tr style="border-left: none;">

    </tr>


    <% for (RoutingRule rule : rules) {
        RequestQueue rq = null;
        try {
            rq = workgroup.getRequestQueue(rule.getQueueID());
        }
        catch (NotFoundException e) {
            Log.error(e);
        }

        // Do not show rule.
        if (rq == null) {
            for (RequestQueue q : workgroup.getRequestQueues()) {
                rq = q;
                break;
            }
        }

        int rulePosition = rule.getPosition();
    %>
    <tr>
        <td><%= rule.getPosition()%>.</td>
        <td>
            <%= rule.getQuery()%>
        </td>
        <td>
            <%= rq.getName() %>
        </td>
        <td nowrap>
            <%  if ((rule.getPosition()) < rules.size()) { %>
            <a href="workgroup-queues.jsp?wgID=<%= wgID %>&changePos=true&down=true&pos=<%= rule.getPosition() %>"
                ><img src="images/arrow_down.gif" width="16" height="16" alt="Move this router down." border="0"></a>
            <%  } else { %>
            <img src="images/blank.gif" width="16" height="16" border="0" alt=""/>
            <%  } %>

            <%  if (rule.getPosition() != 1) { %>
            <a href="workgroup-queues.jsp?wgID=<%= wgID %>&changePos=true&up=true&pos=<%= rule.getPosition() %>"
                ><img src="images/arrow_up.gif" width="16" height="16" alt="Move this router up." border="0"></a>
            <%  } else { %>
            <img src="images/blank.gif" width="16" height="16" border="0" alt=""/>
            <%  } %>
        <td align="center">
            <a href="workgroup-queues.jsp?edit=true&wgID=<%= wgID %>&pos=<%= rule.getPosition() %>"
                ><img src="images/edit-16x16.gif" width="16" height="16" alt="Edit the properties of this Router" border="0"
                ></a>
        </td>
        <td align="center">
            <a href="workgroup-queues.jsp?remove=true&wgID=<%= wgID %>&pos=<%= rule.getPosition()%>"
                ><img src="images/delete-16x16.gif" width="16" height="16" alt="Delete this Router" border="0"
                ></a>
        </td>

    </tr>
    <% } %>


    <% if (rules.size() == 0) { %>
    <tr>
        <td colspan="7" align="center">There are no routing rules defined for this workgroup.</td>
    </tr>
    <%} %>

</table>
<br/>

<div id="editform" style="<%= edit ? "" : "display:none;" %>">
    <fieldset>
        <legend>Edit Routing Rule</legend>
        <table cellspacing="0" cellpadding="3">
            <form action="workgroup-queues.jsp" method="post">
                <input type="hidden" name="editPos" value="<%= pos %>"/>
                <tr>
                    <td colspan="3">
                        Update routing rule.
                        <br/><br/>
                    </td>
                </tr>
                <% if (!advancedBuilder) { %>
                <tr>
                    <td>
                        Form Variable:
                    </td>
                    <td>
                        <select name="editVariable">
                            <% for (FormField field : dataForm.getFields()) { %>
                            <option value="<%= field.getVariable()%>" <%= field.getVariable().equals(variable) ? "selected" : ""%>><%= field.getVariable()%></option>
                            <% } %>

                        </select>
                    </td>
                </tr><tr>
                <td>Form Value:</td>
                <td>
                    <input type="text" name="editValue" size="30" value="<%= value != null ? value : "" %>"/>
                </td>
            </tr>

                <% }
                else { %>
                <tr>
                    <input type="hidden" name="editAdvancedQuery" value="true"/>
                    <td>Edit Query:</td>
                    <td><input type="text" name="editQueryField" size="40" value="<%= variable%>"></td>
                </tr>

                <% } %>


                <tr>

                    <td>Route To Queue:</td>
                    <td>
                        <select name="editQueueID">
                            <% for (RequestQueue queue : workgroup.getRequestQueues()) { %>
                            <option value="<%= queue.getID()%>" <%= queue.getID() == queueID ? "selected" : ""%>><%= queue.getName()%></option>
                            <% } %>
                        </select>
                    </td>
                    <td>
                        <input type="submit" name="editRule" value="Update"/>
                    </td>
                </tr>

                <input type="hidden" name="wgID" value="<%= wgID%>"/>
            </form>
        </table>
    </fieldset>
    <br/>
</div>
<%
    String formElement = request.getParameter("fElement");
%>

<div style="<%= edit ? "display:none;" : "" %>" class="jive-contentBox">
	  <h4>Create New Routing Rule</h4>

    <table cellspacing="0" cellpadding="3">
        <form action="workgroup-queues.jsp" method="post">
            <tr>
                <td colspan="3">
                   Routing rules allow searches against incoming chat request metadata and allow for the routing to specific queues within this workgroup<br/><br/>
                </td>
            </tr>
            <tr>
                <td colspan="3">

                    <table>
                        <tr>
                            <td><input type="radio" name="selector" value="queryBuilder" checked onclick="enableDefault();"></td>
                            <td colspan="2"><b>Form Field Matcher</b></td>
                        </tr>
                        <tr>
                            <td></td>
                            <td>
                                Form Variable:
                            </td>
                            <td>
                                <select name="variable" onchange="updateForm(this);">
                                    <% for (FormField field : dataForm.getFields()) {
                                            if(formElement == null){
                                                formElement = field.getVariable();
                                            }
                                        String selected = field.getVariable().equals(formElement) ? "selected" : "";
                                    %>

                                    <option value="<%= field.getVariable()%>" <%= selected %>><%= field.getVariable()%></option>
                                    <% } %>

                                </select>
                            </td>
                        </tr><tr valign="top">
                        <td></td>
                        <td>Form Value:</td>
                        <td>
                            <%
                                for (org.jivesoftware.openfire.fastpath.dataforms.FormElement ele : form
                                        .getFormElements()) {
                                    if (formElement.equals(ele.getVariable())) {
                                        out.println(FormUtils.createAnswers(ele, "value"));
                                    }
                                }
                            %>

                        </td>
                    </tr>
                    </table>

                </td>



                <tr>
                    <td colspan="3">
                        <div id="advanced">

                            <table width="600">
                                <tr>
                                    <td><input type="radio" name="selector" value="advancedBuilder" onclick="enableAdvanced();"></td>
                                    <td colspan="2"><b>Query Builder</b></td>
                                </tr>
                                <tr>
                                    <td></td>
                                    <td width="1%" nowrap>Query:</td>
                                    <td><input type="text" name="advancedQuery" size="40" id="advancedField"/></td>
                                </tr>
                                <tr>
                                    <td></td>
                                    <td colspan="2"><span class="jive-description">Uses Lucene search syntax to search metadata. To search for
                                    a match in the username as well as in a question, use the following syntax: <i>username:derek AND question:chat</i>.<br>Please refer to the
                                    <a href="http://lucene.apache.org/java/docs/queryparsersyntax.html" target="_blank">Lucene Query Parser Syntax</a>&nbsp;tutorial for proper syntax.</span></td>
                                </tr>
                            </table>

                        </div>
                    </td>
                </tr>


                <td>
                    <table>
                        <tr>
                            <td>Route To Queue:</td>
                            <td>
                                <select name="queueID">
                                    <% for (RequestQueue queue : workgroup.getRequestQueues()) { %>
                                    <option value="<%= queue.getID()%>"><%= queue.getName()%></option>
                                    <% } %>
                                </select>
                            </td>
                            <td>

                                <input type="submit" name="submit" value="Add"/>
                            </td>
                        </tr>
                    </table>
                </td>


                <input type="hidden" name="wgID" value="<%= wgID%>"/>
        </form>
    </table>

</div>


<script type="text/javascript">
    enableDefault();
</script>


</body>
</html>
