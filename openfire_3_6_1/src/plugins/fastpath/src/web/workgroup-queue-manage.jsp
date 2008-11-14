<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%--
  -	$RCSfile$
  -	$Revision: 32958 $
  -	$Date: 2006-08-07 09:12:40 -0700 (Mon, 07 Aug 2006) $
--%>
<%@ page
import   ="java.util.*,
           org.jivesoftware.xmpp.workgroup.Agent,
           org.jivesoftware.xmpp.workgroup.*,
           java.util.LinkedList,
           org.xmpp.packet.JID,
           org.jivesoftware.xmpp.workgroup.dispatcher.DispatcherInfo,
           org.jivesoftware.util.ParamUtils"
errorPage="workgroup-error.jsp"%>
<%
    // Get parameters //
    String wgID = ParamUtils.getParameter(request, "wgID");
    long queueID = ParamUtils.getLongParameter(request, "qID", -1L);
    String name = ParamUtils.getParameter(request, "name");
    String description = ParamUtils.getParameter(request, "description");
    // String agentSelector = ParamUtils.getParameter(request,"agentSelector");
    String success = ParamUtils.getParameter(request, "success");


    boolean update = request.getParameter("update") != null;
    int overflow = ParamUtils.getIntParameter(request, "overflow", 1);
    long overflowQID = ParamUtils.getLongParameter(request, "overflowQID", -1L);


    WorkgroupManager wgManager = WorkgroupManager.getInstance();
    WorkgroupAdminManager adminManager = new WorkgroupAdminManager();

    // Load the workgroup
    Workgroup workgroup = wgManager.getWorkgroup(new JID(wgID));

    long offerTimeout = ParamUtils.getLongParameter(request, "offerTimeout", -1);
    if (offerTimeout == -1) {
        offerTimeout = workgroup.getOfferTimeout() / 1000;
    }


    long requestTimeout = ParamUtils.getLongParameter(request, "requestTimeout", -1);
    if (requestTimeout == -1) {
        requestTimeout = workgroup.getRequestTimeout() / 1000;
    }

    AgentManager aManager = wgManager.getAgentManager();
    // Load the request queue:
    RequestQueue queue = workgroup.getRequestQueue(queueID);
    //AgentSelector newSelector = null;
%>




<%
    RequestQueue.OverflowType overflowType = null;
    switch (overflow) {
        case 1:
            overflowType = RequestQueue.OverflowType.OVERFLOW_NONE;
            break;
        case 2:
            overflowType = RequestQueue.OverflowType.OVERFLOW_RANDOM;
            break;
        case 3:
            overflowType = RequestQueue.OverflowType.OVERFLOW_BACKUP;
            break;
    }



    Map errors = new HashMap();
    if (update) {
        if (name == null) {
            errors.put("name","");
        }

        else {

        }
        if (errors.size() == 0) {
            queue.setName(name);
            queue.setDescription(description);
            // set timeouts
            DispatcherInfo infos = queue.getDispatcher().getDispatcherInfo();

            infos.setOfferTimeout(offerTimeout*1000L);
            infos.setRequestTimeout(requestTimeout*1000L);
            queue.getDispatcher().setDispatcherInfo(infos);
        }


            queue.setOverflowType(overflowType);
            if (overflowType == RequestQueue.OverflowType.OVERFLOW_BACKUP && overflowQID != -1L) {
                queue.setBackupQueue(workgroup.getRequestQueue(overflowQID));
            }

            response.sendRedirect("workgroup-queue-manage.jsp?success=true&wgID=" + wgID + "&qID=" + queue.getID());
            return;
        }

    RequestQueue backupQueue = null;

    if (errors.size() == 0) {
        name = queue.getName();
        description = queue.getDescription();
        DispatcherInfo dispatcherInfo = queue.getDispatcher().getDispatcherInfo();
        offerTimeout = dispatcherInfo.getOfferTimeout();
        requestTimeout = dispatcherInfo.getRequestTimeout();

        overflowType = queue.getOverflowType();
        if (overflowType == RequestQueue.OverflowType.OVERFLOW_BACKUP) {
            backupQueue = queue.getBackupQueue();
        }
    }
%>


<html>
    <head>
        <title>Edit Queue Settings - <%=queue.getName()%></title>
        <meta name="subPageID" content="workgroup-queues"/>
        <meta name="extraParams" content="wgID=<%= wgID %>"/>
        <!--<meta name="helpPage" content="edit_queue_properties.html"/>-->

        <script language="JavaScript" type="text/javascript">
        function openWin(el) {
            var win = window.open('user-browser.jsp?formName=f&elName=agents','newWin','width=500,height=550,menubar=yes,location=no,personalbar=no,scrollbars=yes,resize=yes');
        }

        function openAgentGroupWindow(el){
             var agentwin = window.open('agent-group-browser.jsp?formName=f&elName=agentGroups','newWin','width=500,height=550,menubar=yes,location=no,personalbar=no,scrollbars=yes,resize=yes');

        }
        </script>
    </head>
    <body>

<p>
The queue name and description helps
administrators and agents with identifying a particular request queue.
</p>

<p>
<a href="workgroup-queue-agents.jsp?wgID=<%= wgID %>&qID=<%= queueID%>">Edit Agents and Groups</a>
    &nbsp;
<a href="workgroup-queues.jsp?wgID=<%= wgID %>">View Queues</a>
</p>


<% if(success != null && errors.size() == 0) { %>
<div class="success">
   Workgroup Queue has been updated.
</div>
<br/>
<% } %>
<%  if (errors.size() > 0) { %>

    <div class="error">
    Please fix the errors below.
    </div>

<%  } %>

<form action="workgroup-queue-manage.jsp" method="post" name="f">
<input type="hidden" name="wgID" value="<%= wgID %>">
<input type="hidden" name="qID" value="<%= queueID %>">
    <table width="100%" class="jive-table" cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th colspan="3">Queue Settings</th>
        </tr>
<tr valign="top">
    <td class="c1">
        <b>Name: *</b>
        <%  if (errors.get("name") != null) { %>
            <span class="jive-error-text">
            Please enter a name.
            </span>

        <%  } %>
         <br/>
        <span class="jive-description">
        Please specify the name of the queue. The queue name should be created based on the Queue Routing rules.
        </span>
    </td>
    <td class="c2">
        <input type="text" name="name" size="40"
         value="<%= ((name != null) ? name : "") %>">
    </td>
</tr>
<tr valign="top">
    <td class="c1">
        <b>Description:</b>
        <br/>
        <span class="jive-description">Specify a description for this queue to easily identify it.</span>
    </td>
    <td class="c2">
        <textarea name="description" cols="40" rows="2" wrap="virtual"><%= ((description != null) ? description : "") %></textarea>
    </td>
</tr>
<tr valign="top">
    <td class="c1">
        <b>Request Timeout: (sec) *</b>
        <%  if (errors.get("requestTimeout") != null) { %>
            &nbsp;
            <span class="jive-error-text">
            Please enter a valid timeout value.
            </span>

        <%  } %>
        <br/>
        <span class="jive-description">The total time before an individual request will timeout if no agents accept it.</span>
    </td>

                <td width="99%">
                    <input type="text" name="requestTimeout" value="<%= requestTimeout/1000L %>"
                     size="5" maxlength="10"
                    >
                </td>

</tr>
<tr valign="top">
    <td class="c1">
        <b>Offer Timeout: (sec) *</b>
        <%  if (errors.get("offerTimeout") != null) { %>
            &nbsp;
            <span class="jive-error-text">
            Please enter a valid timeout value.
            </span>

        <%  } %>
        <br/>
        <span class="jive-description">The time each agent will be giving to accept a chat request.</span>
    </td>
    <td class="c2">
      <input type="text" name="offerTimeout" value="<%= offerTimeout/1000 %>" size="5" maxlength="10">
    </td>
</tr>


<tr valign="top">
    <td class="c1">
        <b>Queue Overflow Policy:</b>
        <br/>
        <span class="jive-description">Specify failover for this queue.</span>
    </td>
    <td class="c2">
        <table cellpadding="2" cellspacing="0" border="0" style="border-width:0px !important;">
        <tr>
            <td>
                <input type="radio" name="overflow" value="1" id="over01"
                 <%= ((overflowType==RequestQueue.OverflowType.OVERFLOW_NONE) ? "checked" : "") %>>
            </td>
            <td>
                <label for="over01">Never overflow requests</label>
            </td>
        </tr>
        <tr>
            <td>
                <input type="radio" name="overflow" value="2" id="over02"
                 <%= ((overflowType==RequestQueue.OverflowType.OVERFLOW_RANDOM) ? "checked" : "") %>>
            </td>
            <td>
                <label for="over02">Overflow requests to a random queue</label>
            </td>
        </tr>

        <%  // Get a list of all other queues in this workgroup
            List queues = new LinkedList();
            for(RequestQueue requestQueue : workgroup.getRequestQueues()){
                if (requestQueue.getID() != queueID) {
                    queues.add(requestQueue);
                }
            }
        %>

        <tr>
            <td>
                <input type="radio" name="overflow" value="3" id="over03"
                 <%= ((overflowType==RequestQueue.OverflowType.OVERFLOW_BACKUP) ? "checked" : "") %>
                 <%= ((queues.size()==0) ? "disabled" : "") %>>
            </td>
            <td>
                <label for="over03">Overflow requests to a specified queue:</label>
            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td>
                <%  if (queues.size() == 0) { %>

                    No other queues -
                    <a href="workgroup-queue-create.jsp?wgID=<%= wgID %>">create one</a>.

                <%  } else { %>

                <select size="1" name="overflowQID" onchange="this.form.overflow[2].checked=true;">

                    <option value=""></option>

                    <%  for (int i=0; i<queues.size(); i++) {
                            RequestQueue q = (RequestQueue)queues.get(i);
                    %>
                        <option value="<%= q.getID() %>"
                        <%  if (backupQueue != null) { %>

                            <%= ((backupQueue.getID()==q.getID()) ? "selected" : "") %>

                        <%  } %>
                         ><%= q.getName() %></option>

                    <%  } %>

                </select>

                <%  } %>
            </td>
        </tr>
        </table>
    </td>
</tr>
</table>

<br>

* Required field.

<br><br>

<input type="submit" name="update" value="Save Settings">

</form>

<script language="JavaScript" type="text/javascript">
document.f.name.focus();
</script>

</body>
</html>