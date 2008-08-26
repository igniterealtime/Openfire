<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%--
  -	$RCSfile$
  -	$Revision: 32833 $
  -	$Date: 2006-08-02 15:52:36 -0700 (Wed, 02 Aug 2006) $
--%>
<%@ page
import="org.jivesoftware.util.*,
        java.util.*,
        org.jivesoftware.xmpp.workgroup.*,
        org.xmpp.packet.JID"%>

<%
    final boolean addsuccess = ParamUtils.getParameter(request, "addsuccess") != null;
    final boolean deletesuccess = ParamUtils.getParameter(request, "deletesuccess") != null;
%>

<% // Get parameters //
    String wgID = ParamUtils.getParameter(request, "wgID");
    long   queueID = ParamUtils.getLongParameter(request, "qID", -1L);
    boolean add = request.getParameter("add") != null;
    boolean delete = request.getParameter("delete") != null;
    String name = ParamUtils.getParameter(request, "name");
    String value = ParamUtils.getParameter(request, "value");

    // Load the workgroup
    final WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
    Workgroup workgroup = workgroupManager.getWorkgroup(new JID(wgID));
    AgentManager aManager = workgroupManager.getAgentManager();
    // Load the queue:
    RequestQueue queue = workgroup.getRequestQueue(queueID);
    Map    errors = new HashMap();
    if (add) {
        if (name == null) {
            errors.put("name", "");
        }
        if (value == null) {
            errors.put("value", "");
        }
        if (errors.size() == 0) {
            queue.getProperties().setProperty(name, value);
            response.sendRedirect("workgroup-queue-rules.jsp?wgID=" + wgID + "&qID=" + queueID + "&addsuccess=true");
            return;
        }
    }
    if (delete) {
        if (name != null) {
            queue.getProperties().deleteProperty(name);
            response.sendRedirect("workgroup-queue-rules.jsp?wgID=" + wgID + "&qID=" + queueID + "&deletesuccess=true");
            return;
        }
    }
    DbProperties props = queue.getProperties();
%>
<html>
    <head>
        <title>Add Queue Rules</title>
        <meta name="subPageID" content="workgroup-queues"/>
        <meta name="extraParams" content="wgID=<%= wgID %>"/>
    </head>
    <body>
        <p>Below is a list of routing rules for this queue. Use the form below to add new rules. Routing rules are
        keyed off of incoming call request meta data. So, if you have a rule name and value of &quot;foo&quot; and
        &quot;bar&quot; then this rule would match if there is an incoming meta data name of &quot;foo&quot; with the
        value &quot;bar&quot;.</p>
       <% if (addsuccess) { %>
            <div class="jive-success">
                <table cellpadding="0" cellspacing="0" border="0">
                    <tbody>
                        <tr>
                            <td class="jive-icon">
                                <img src="images/success-16x16.gif" width="16" height="16" border="0"/>
                            </td>
                            <td class="jive-icon-label">Rule has been added.</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        <% } %>
        <% if(deletesuccess) { %>
            <div class="jive-success">
                <table cellpadding="0" cellspacing="0" border="0">
                    <tbody>
                        <tr>
                            <td class="jive-icon">
                                <img src="images/success-16x16.gif" width="16" height="16" border="0"/>
                            </td>
                            <td class="jive-icon-label">Rule has been deleted.</td>
                        </tr>
                    </tbody>
                </table>
            </div>
            <% } %>
    <br/>
    <table class="jive-table" cellpadding="3" cellspacing="0" border="0" width="100%">
        <tr>
            <th>&nbsp;</th> <th>Name</th> <th>Value</th> <th>Delete</th>
        </tr>
<%
        Collection<String> propertyKeys = props.getPropertyNames();
        if (propertyKeys.isEmpty()) {
%>
            <tr>
                <td colspan="4" align="center">
                    <br/>
                    <i>No rules set</i>
                    <br/>
                    <br/>
                </td>
            </tr>
<%
        }
%>
<%
        int counter = 0;
        for (String key : propertyKeys) {
            counter++;
            String val = props.getProperty(key);
%>
            <tr class="jive-<%= (((counter % 2) == 0) ? "even" : "odd") %>">
                <td width="1%" nowrap>
                    <%= (counter) %>.
                </td>
                <td width="49%">
                    <%= StringUtils.escapeHTMLTags(key) %>
                </td>
                <td width="49%">
                    <%= StringUtils.escapeHTMLTags(val) %>
                </td>
                <td width="1%" nowrap align="center">
                    <a href="workgroup-queue-rules.jsp?wgID=<%= wgID %>&qID=<%= queueID %>&delete=true&name=<%= key %>">
                    <img src="images/delete-16x16.gif" width="16" height="16" border="0"/> </a>
                </td>
            </tr>
<%
        }
%>
    </table>
          <p>
              <b>Add New Rule</b>
          </p>
    <form action="workgroup-queue-rules.jsp" method="post">
        <input type="hidden" name="wgID" value="<%= wgID %>"/>
        <input type="hidden" name="qID" value="<%= queueID %>"/>
        <table class="jive-table" cellpadding="3" cellspacing="0" border="0">
            <tr>
                <td class="c1">Name: *
<%
                    if (errors.get("name") != null) {
%>
                        <span class="jive-error-text">
                        <br/>Please enter a valid rule name. </span>
<%
                    }
%>
                </td>
                <td class="c2">
                    <input type="text" name="name" size="30" value="<%= ((name != null) ? name : "") %>"/>
                </td>
            </tr>
            <tr>
                <td class="c1">Value: *
<%
                    if (errors.get("value") != null) {
%>
                        <span class="jive-error-text">
                        <br/>Please enter a valid value for this rule. </span>
<%
                    }
%>
                </td>
                <td class="c2">
                    <input type="text" name="value" size="30" value="<%= ((value != null) ? value : "") %>"/>
                </td>
            </tr>
        </table>
            <p>* Required fields</p>
        <input type="submit" name="add" value="Add Rule"/>
    </form>
    </body>
</html>
